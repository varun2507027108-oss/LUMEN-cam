'use client'

/**
 * CameraScreen — the live viewfinder. Owns the AuroraEngine instance for the
 * active source (webcam or demo scene), renders the HUD, format/mode chrome,
 * contextual control panels, and the two-stage double-exposure shutter. (v2)
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { FlipHorizontal, Images, Loader2, LogOut, RotateCcw, SwitchCamera, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { downloadDataUrl, saveCaptureBundle } from '@/lib/aurora/client'
import { AuroraEngine } from '@/lib/aurora/engine'
import { FORMATS, type AuroraParams, type EngineStatus, type FormatKey } from '@/lib/aurora/types'
import { DxPanel, EchoPanel, FormatPills, LookPanel, ModePills, TrailsPanel } from '@/components/aurora/panels'
import { useFittedBox } from '@/components/aurora/useFittedBox'

const INITIAL_STATUS: EngineStatus = { fps: 0, sourceW: 0, sourceH: 0, outW: 0, outH: 0, firstCaptured: false }

export function CameraScreen({
  video,
  demoMode,
  active,
  params,
  onParamsChange,
  onOpenGallery,
  onCaptureSaved,
  onSwitchCamera,
  onExit,
}: {
  video: HTMLVideoElement | null
  demoMode: boolean
  active: boolean
  params: AuroraParams
  onParamsChange: (p: AuroraParams) => void
  onOpenGallery: () => void
  onCaptureSaved: () => void
  onSwitchCamera: (() => void) | undefined
  onExit: () => void
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const engineRef = useRef<AuroraEngine | null>(null)
  const paramsRef = useRef(params)
  const [status, setStatus] = useState<EngineStatus>(INITIAL_STATUS)
  const [busy, setBusy] = useState(false)
  const [flash, setFlash] = useState(0)
  const [lutName, setLutName] = useState('')
  const [webglError, setWebglError] = useState<string | null>(null)

  const fmt = FORMATS[params.format]
  const { ref: fitRef, box } = useFittedBox(fmt.previewW / fmt.previewH)

  // Keep params in sync without tearing down the engine.
  useEffect(() => {
    paramsRef.current = params
    engineRef.current?.updateParams(params)
  }, [params])

  // Engine lifecycle: one engine per source (webcam / demo).
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    if (!video && !demoMode) return
    let engine: AuroraEngine
    try {
      engine = new AuroraEngine(canvas, paramsRef.current)
    } catch (e) {
      setWebglError(e instanceof Error ? e.message : 'Failed to initialise WebGL2.')
      return
    }
    engineRef.current = engine
    setLutName(engine.lutName)
    engine.onStatus(setStatus)
    if (video) engine.attachVideo(video)
    else engine.startDemo()
    engine.updateParams(paramsRef.current)
    engine.start()
    return () => {
      engine.dispose()
      engineRef.current = null
    }
  }, [video, demoMode])

  // Pause the loop while gallery/remix overlays are on top.
  useEffect(() => {
    engineRef.current?.setPaused(!active)
  }, [active])

  const setLook = useCallback(
    (look: AuroraParams['look']) => onParamsChange({ ...params, look }),
    [params, onParamsChange],
  )
  const setDx = useCallback(
    (dx: AuroraParams['dx']) => onParamsChange({ ...params, dx }),
    [params, onParamsChange],
  )
  const setEcho = useCallback(
    (echo: AuroraParams['echo']) => onParamsChange({ ...params, echo }),
    [params, onParamsChange],
  )
  const setTrails = useCallback(
    (trails: AuroraParams['trails']) => onParamsChange({ ...params, trails }),
    [params, onParamsChange],
  )

  const handleUploadCube = useCallback(async (file: File) => {
    const engine = engineRef.current
    if (!engine) return
    try {
      const text = await file.text()
      const res = engine.setLutFromCube(file.name, text)
      if (res.ok) {
        setLutName(engine.lutName)
        toast.success(`LUT loaded: ${file.name}`)
      } else {
        toast.error('Could not parse .cube file', { description: res.error })
      }
    } catch {
      toast.error('Could not read the uploaded file')
    }
  }, [])

  const handleShutter = useCallback(async () => {
    const engine = engineRef.current
    if (!engine || busy) return
    if (params.mode === 'dx' && !status.firstCaptured) {
      engine.captureFirst()
      return
    }
    setBusy(true)
    setFlash((n) => n + 1)
    try {
      const bundle = await engine.capture()
      const saved = await saveCaptureBundle(bundle)
      const composite = bundle.images.find((i) => i.kind === 'composite') ?? bundle.images[0]
      toast.success(
        saved > 1 ? `Saved ${saved} frames to gallery` : 'Capture saved to gallery',
        composite
          ? {
              duration: 6000,
              action: {
                label: 'Download',
                onClick: () => downloadDataUrl(composite.dataUrl, `auroracam-${bundle.mode}-${Date.now()}.jpg`),
              },
            }
          : undefined,
      )
      onCaptureSaved()
    } catch (e) {
      toast.error('Capture failed', { description: e instanceof Error ? e.message : undefined })
    } finally {
      setBusy(false)
      window.setTimeout(() => setFlash(0), 400)
    }
  }, [busy, params.mode, status.firstCaptured, onCaptureSaved])

  const dxStaged = params.mode === 'dx' && status.firstCaptured

  return (
    <div className="relative flex h-full min-h-0 flex-col bg-neutral-950 text-neutral-100">
      {/* Top chrome */}
      <header className="flex items-center justify-between gap-2 px-3 pt-3">
        <FormatPills value={params.format} onChange={(f: FormatKey) => onParamsChange({ ...params, format: f })} />
        <div className="flex items-center gap-1.5">
          <Button
            variant="ghost"
            size="icon"
            aria-label="Open gallery"
            className="h-9 w-9 rounded-full text-neutral-300 hover:bg-white/10"
            onClick={onOpenGallery}
          >
            <Images className="h-4.5 w-4.5" aria-hidden />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Stop camera and exit"
            className="h-9 w-9 rounded-full text-neutral-300 hover:bg-white/10"
            onClick={onExit}
          >
            <LogOut className="h-4.5 w-4.5" aria-hidden />
          </Button>
        </div>
      </header>

      {/* HUD */}
      <div className="px-4 pt-2 font-mono text-[10px] uppercase tracking-widest text-neutral-500">
        {demoMode ? 'demo source' : 'camera'} {status.sourceW > 0 ? `${status.sourceW}×${status.sourceH}` : '…'} · buf{' '}
        {status.outW > 0 ? `${status.outW}×${status.outH}` : '…'} · {status.fps} fps
        {webglError ? ' · error' : ''}
      </div>

      {/* Viewfinder */}
      <div ref={fitRef} className="relative flex min-h-0 flex-1 items-center justify-center p-2">
        <div className="relative" style={{ width: box.w || 1, height: box.h || 1 }}>
          {webglError ? (
            <div className="flex h-full w-full items-center justify-center rounded-lg bg-neutral-900 p-6 text-center ring-1 ring-white/10">
              <div>
                <TriangleAlert className="mx-auto mb-2 h-6 w-6 text-amber-400" aria-hidden />
                <p className="text-sm font-medium">WebGL2 unavailable</p>
                <p className="mt-1 text-xs text-neutral-500">{webglError}</p>
              </div>
            </div>
          ) : (
            <canvas
              ref={canvasRef}
              className="block h-full w-full rounded-lg ring-1 ring-white/10"
              aria-label="Camera viewfinder"
            />
          )}

          {/* XPAN frame lines */}
          {params.format === '65:24' && !webglError && (
            <div className="pointer-events-none absolute inset-0" aria-hidden>
              <div className="absolute inset-x-[6%] top-[10%] h-px bg-orange-400/50" />
              <div className="absolute inset-x-[6%] bottom-[10%] h-px bg-orange-400/50" />
              <div className="absolute left-[6%] top-[10%] h-3 w-3 border-l-2 border-t-2 border-orange-400/80" />
              <div className="absolute right-[6%] top-[10%] h-3 w-3 border-r-2 border-t-2 border-orange-400/80" />
              <div className="absolute bottom-[10%] left-[6%] h-3 w-3 border-b-2 border-l-2 border-orange-400/80" />
              <div className="absolute bottom-[10%] right-[6%] h-3 w-3 border-b-2 border-r-2 border-orange-400/80" />
            </div>
          )}

          {/* Double exposure staged banner */}
          {dxStaged && (
            <div className="absolute inset-x-2 top-2 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-black/70 px-3 py-2 ring-1 ring-amber-500/40 backdrop-blur-sm">
              <p className="text-[11px] text-amber-300">
                1st exposure locked — adjust the blend, then press the shutter to composite.
              </p>
              <div className="flex gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 border-white/15 bg-transparent px-2 text-[10px] text-neutral-200 hover:bg-white/10"
                  onClick={() => engineRef.current?.retakeFirst()}
                >
                  <RotateCcw className="mr-1 h-3 w-3" aria-hidden />
                  Retake
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 border-white/15 bg-transparent px-2 text-[10px] text-neutral-200 hover:bg-white/10"
                  onClick={() => setDx({ ...params.dx, flipFirst: !params.dx.flipFirst })}
                  aria-pressed={params.dx.flipFirst}
                >
                  <FlipHorizontal className="mr-1 h-3 w-3" aria-hidden />
                  Flip
                </Button>
              </div>
            </div>
          )}

          {/* Trails tripod hint */}
          {params.mode === 'trails' && (
            <div className="absolute inset-x-2 bottom-2 rounded-lg bg-black/60 px-3 py-1.5 text-center text-[10px] text-neutral-300 ring-1 ring-white/10 backdrop-blur-sm">
              Steady your device or use a tripod — trails accumulate over time.
            </div>
          )}

          {/* Capture flash */}
          {flash > 0 && <div key={flash} className="aurora-flash pointer-events-none absolute inset-0 rounded-lg bg-white" aria-hidden />}
        </div>
      </div>

      {/* Bottom chrome */}
      <div className="space-y-2 px-3 pb-4 pt-1">
        <ModePills value={params.mode} onChange={(m) => onParamsChange({ ...params, mode: m })} />

        <div className="aurora-scroll max-h-44 space-y-2 overflow-y-auto pr-0.5">
          {params.mode === 'dx' && <DxPanel dx={params.dx} onChange={setDx} />}
          {params.mode === 'echo' && <EchoPanel echo={params.echo} onChange={setEcho} />}
          {params.mode === 'trails' && (
            <TrailsPanel trails={params.trails} onChange={setTrails} onClear={() => engineRef.current?.clearTrails()} />
          )}
          <LookPanel
            look={params.look}
            onChange={setLook}
            lutName={lutName || 'Aurora Warm (procedural)'}
            onUseProcedural={() => {
              engineRef.current?.useProceduralLut()
              setLutName(engineRef.current?.lutName ?? '')
            }}
            onUploadCube={handleUploadCube}
          />
        </div>

        {/* Shutter row */}
        <div className="flex items-center justify-between px-4 pt-1">
          <div className="w-12">
            {onSwitchCamera && (
              <Button
                variant="ghost"
                size="icon"
                aria-label="Switch camera"
                className="h-11 w-11 rounded-full text-neutral-300 hover:bg-white/10"
                onClick={onSwitchCamera}
              >
                <SwitchCamera className="h-5 w-5" aria-hidden />
              </Button>
            )}
          </div>

          <button
            type="button"
            onClick={handleShutter}
            disabled={busy || !!webglError}
            aria-label={
              params.mode === 'dx' ? (dxStaged ? 'Capture second exposure and composite' : 'Capture first exposure') : 'Capture photo'
            }
            className={`relative flex h-16 w-16 items-center justify-center rounded-full ring-4 transition-transform active:scale-95 disabled:cursor-not-allowed disabled:opacity-60 ${
              dxStaged ? 'ring-amber-400' : 'ring-white/90'
            } ${busy ? 'ring-white/40' : ''}`}
          >
            <span className={`flex h-12 w-12 items-center justify-center rounded-full ${busy ? 'bg-white/30' : 'bg-white'}`}>
              {busy && <Loader2 className="h-5 w-5 animate-spin text-black" aria-hidden />}
            </span>
            {params.mode === 'dx' && !busy && (
              <span
                className={`absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold ${
                  dxStaged ? 'bg-amber-500 text-black' : 'bg-neutral-800 text-white ring-1 ring-white/30'
                }`}
              >
                {dxStaged ? '2' : '1'}
              </span>
            )}
          </button>

          <div className="w-12" />
        </div>
      </div>
    </div>
  )
}

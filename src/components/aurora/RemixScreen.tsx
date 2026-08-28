'use client'

/**
 * RemixScreen — the M3 remix editor. Reloads a stored composite's two
 * exposures as GPU textures and re-runs the double-exposure + Signature Look
 * pipeline over them, live. Saving stores the re-blended composite (plus the
 * input pair under a new group, so remixes stay remixable).
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowLeft, Loader2, Save } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { saveCaptureBundle } from '@/lib/aurora/client'
import { AuroraEngine } from '@/lib/aurora/engine'
import {
  DEFAULT_PARAMS,
  FORMATS,
  isFormatKey,
  type AuroraParams,
  type EngineStatus,
} from '@/lib/aurora/types'
import { DxPanel, LookPanel } from '@/components/aurora/panels'
import { useFittedBox } from '@/components/aurora/useFittedBox'

export function RemixScreen({
  payload,
  onBack,
  onSaved,
}: {
  payload: { groupId: string; first: string; second: string; format: string; blendMode: number; opacity: number }
  onBack: () => void
  onSaved: () => void
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const engineRef = useRef<AuroraEngine | null>(null)
  const paramsRef = useRef<AuroraParams | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [lutName, setLutName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<EngineStatus>({ fps: 0, sourceW: 0, sourceH: 0, outW: 0, outH: 0, firstCaptured: false })

  const format = isFormatKey(payload.format) ? payload.format : '4:3'
  const fmt = FORMATS[format]
  const { ref: fitRef, box } = useFittedBox(fmt.previewW / fmt.previewH)
  const [params, setParams] = useState<AuroraParams>({
    ...DEFAULT_PARAMS,
    format,
    mode: 'dx',
    dx: { blendMode: payload.blendMode, opacity: payload.opacity, flipFirst: false },
  })

  useEffect(() => {
    paramsRef.current = params
    engineRef.current?.updateParams(params)
  }, [params])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    let disposed = false
    let engine: AuroraEngine | null = null
    try {
      engine = new AuroraEngine(canvas, paramsRef.current ?? { ...DEFAULT_PARAMS, format, mode: 'dx' })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to initialise WebGL2.')
      setLoading(false)
      return
    }
    engineRef.current = engine
    setLutName(engine.lutName)
    engine.onStatus(setStatus)
    engine.updateParams(paramsRef.current ?? { ...DEFAULT_PARAMS, format, mode: 'dx' })
    engine.setPaused(true) // stay idle until the pair is loaded
    engine.start()
    const load = async () => {
      try {
        await engine!.loadRemixPair(payload.first, payload.second)
        if (disposed) return
        engine!.setPaused(false)
        setLoading(false)
      } catch (e) {
        if (disposed) return
        setError(e instanceof Error ? e.message : 'Failed to load the stored exposures.')
        setLoading(false)
      }
    }
    void load()
    return () => {
      disposed = true
      engine?.dispose()
      engineRef.current = null
    }
  }, [payload.groupId, payload.first, payload.second])

  const setLook = useCallback((look: AuroraParams['look']) => setParams((p) => ({ ...p, look })), [])
  const setDx = useCallback((dx: AuroraParams['dx']) => setParams((p) => ({ ...p, dx })), [])

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

  const handleSave = useCallback(async () => {
    const engine = engineRef.current
    if (!engine || busy || loading) return
    setBusy(true)
    try {
      const bundle = await engine.capture()
      await saveCaptureBundle(bundle)
      toast.success('Remix saved to gallery')
      onSaved()
    } catch (e) {
      toast.error('Remix failed', { description: e instanceof Error ? e.message : undefined })
    } finally {
      setBusy(false)
    }
  }, [busy, loading, onSaved])

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-neutral-950 text-neutral-100">
      <header className="flex items-center justify-between gap-2 px-3 pt-3">
        <Button
          variant="ghost"
          size="icon"
          aria-label="Back to gallery"
          className="h-9 w-9 rounded-full text-neutral-300 hover:bg-white/10"
          onClick={onBack}
        >
          <ArrowLeft className="h-4.5 w-4.5" aria-hidden />
        </Button>
        <div className="text-center">
          <h2 className="text-sm font-semibold">Remix</h2>
          <p className="font-mono text-[10px] uppercase tracking-widest text-neutral-500">
            {fmt.label} · stored exposures
          </p>
        </div>
        <Button
          aria-label="Save remix"
          size="sm"
          className="h-9 bg-amber-500 px-3 text-xs font-semibold text-black hover:bg-amber-400"
          onClick={handleSave}
          disabled={busy || loading || !!error}
        >
          {busy ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" aria-hidden /> : <Save className="mr-1.5 h-3.5 w-3.5" aria-hidden />}
          Save
        </Button>
      </header>

      <div className="px-4 pt-2 text-center font-mono text-[10px] uppercase tracking-widest text-neutral-500">
        {loading ? 'loading exposures…' : error ? 'error' : `re-blending · ${status.fps} fps`}
      </div>

      {/* Viewfinder */}
      <div ref={fitRef} className="relative flex min-h-0 flex-1 items-center justify-center p-2">
        <div className="relative" style={{ width: box.w || 1, height: box.h || 1 }}>
          <canvas
            ref={canvasRef}
            className="block h-full w-full rounded-lg ring-1 ring-white/10"
            aria-label="Remix viewfinder"
          />
          {loading && !error && (
            <div className="absolute inset-0 flex items-center justify-center rounded-lg bg-neutral-950/70">
              <Loader2 className="h-6 w-6 animate-spin text-amber-400" aria-hidden />
            </div>
          )}
          {error && (
            <div className="absolute inset-0 flex items-center justify-center rounded-lg bg-neutral-950/90 p-6 text-center">
              <p className="text-xs text-neutral-400">{error}</p>
            </div>
          )}
        </div>
      </div>

      {/* Controls */}
      <div className="aurora-scroll max-h-52 space-y-2 overflow-y-auto px-3 pb-5">
        <DxPanel dx={params.dx} onChange={setDx} />
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
    </div>
  )
}

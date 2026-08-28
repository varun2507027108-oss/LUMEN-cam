'use client'

/**
 * AuroraCam — app shell. Owns the camera stream (getUserMedia), the global
 * creative params, and the view state machine:
 * gate → camera (+ gallery / remix overlays).
 */

import { useCallback, useRef, useState } from 'react'
import { CameraScreen } from '@/components/aurora/CameraScreen'
import { GalleryScreen } from '@/components/aurora/GalleryScreen'
import { PermissionGate } from '@/components/aurora/PermissionGate'
import { RemixScreen } from '@/components/aurora/RemixScreen'
import { DEFAULT_PARAMS, type AuroraParams, type RemixPayload } from '@/lib/aurora/types'

type View = 'camera' | 'gallery' | 'remix'
type Facing = 'user' | 'environment'

export default function Home() {
  const [started, setStarted] = useState(false)
  const [demoMode, setDemoMode] = useState(false)
  const [videoReady, setVideoReady] = useState(false)
  const [starting, setStarting] = useState(false)
  const [gateError, setGateError] = useState<string | null>(null)
  const [facing, setFacing] = useState<Facing>('user')

  const [params, setParams] = useState<AuroraParams>(DEFAULT_PARAMS)
  const [view, setView] = useState<View>('camera')
  const [remixPayload, setRemixPayload] = useState<RemixPayload | null>(null)
  const [galleryKey, setGalleryKey] = useState(0)

  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }, [])

  const startCamera = useCallback(
    async (wantFacing: Facing) => {
      setStarting(true)
      setGateError(null)
      try {
        if (!navigator.mediaDevices?.getUserMedia) {
          throw new Error('This browser does not expose a camera API. Try demo mode instead.')
        }
        stopStream()
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: {
            facingMode: wantFacing,
            width: { ideal: 1920 },
            height: { ideal: 1080 },
            frameRate: { ideal: 30 },
          },
        })
        streamRef.current = stream
        const video = videoRef.current
        if (!video) throw new Error('Video element unavailable.')
        video.srcObject = stream
        await video.play()
        if (video.readyState < 2) {
          await new Promise<void>((resolve) => {
            const done = () => resolve()
            video.addEventListener('loadeddata', done, { once: true })
            window.setTimeout(done, 3000)
          })
        }
        setDemoMode(false)
        setParams((p) => ({ ...p, mirror: wantFacing === 'user' }))
        setFacing(wantFacing)
        setVideoReady(true)
        setStarted(true)
        setView('camera')
      } catch (e) {
        const msg =
          e instanceof DOMException && (e.name === 'NotAllowedError' || e.name === 'SecurityError')
            ? 'Camera permission was denied. Allow access in your browser, or try demo mode.'
            : e instanceof DOMException && e.name === 'NotFoundError'
              ? 'No camera device found. Try demo mode instead.'
              : e instanceof Error
                ? e.message
                : 'Unknown camera error.'
        setGateError(msg)
        stopStream()
      } finally {
        setStarting(false)
      }
    },
    [stopStream],
  )

  const startDemo = useCallback(() => {
    stopStream()
    setDemoMode(true)
    setVideoReady(false)
    setParams((p) => ({ ...p, mirror: false }))
    setStarted(true)
    setView('camera')
  }, [stopStream])

  const switchCamera = useCallback(() => {
    const next: Facing = facing === 'user' ? 'environment' : 'user'
    void startCamera(next)
  }, [facing, startCamera])

  const exitToGate = useCallback(() => {
    stopStream()
    setStarted(false)
    setDemoMode(false)
    setVideoReady(false)
    setGateError(null)
    setView('camera')
  }, [stopStream])

  if (!started) {
    return (
      <PermissionGate
        onStartCamera={() => void startCamera(facing)}
        onStartDemo={startDemo}
        cameraStarting={starting}
        error={gateError}
      />
    )
  }

  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-neutral-950 text-neutral-100">
      {/* Hidden capture element for the live camera stream. */}
      <video
        ref={videoRef}
        playsInline
        muted
        autoPlay
        className="pointer-events-none absolute h-px w-px opacity-0"
        aria-hidden
      />

      <div className="min-h-0 flex-1">
        <CameraScreen
          video={videoReady && !demoMode ? videoRef.current : null}
          demoMode={demoMode}
          active={view === 'camera'}
          params={params}
          onParamsChange={setParams}
          onOpenGallery={() => setView('gallery')}
          onCaptureSaved={() => setGalleryKey((k) => k + 1)}
          onSwitchCamera={demoMode ? undefined : switchCamera}
          onExit={exitToGate}
        />
      </div>

      {view === 'gallery' && (
        <GalleryScreen
          refreshKey={galleryKey}
          onBack={() => setView('camera')}
          onRemix={(payload) => {
            setRemixPayload(payload)
            setView('remix')
          }}
        />
      )}

      {view === 'remix' && remixPayload && (
        <RemixScreen
          payload={remixPayload}
          onBack={() => setView('gallery')}
          onSaved={() => {
            setGalleryKey((k) => k + 1)
            setView('gallery')
          }}
        />
      )}
    </div>
  )
}

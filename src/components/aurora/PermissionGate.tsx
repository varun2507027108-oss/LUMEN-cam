'use client'

/**
 * Permission gate — the entry screen. Offers real camera access (getUserMedia)
 * or Demo Mode (animated night scene) for environments without a webcam.
 */

import { Aperture, Camera, Images, Layers, Loader2, MonitorPlay, ShieldCheck, Waves, Zap } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

const FEATURES = [
  { icon: Layers, title: 'Live double exposure', desc: 'Screen / Lighten / Add / Multiply / Overlay — composed in the viewfinder, before the shutter.' },
  { icon: Waves, title: 'Motion echo & light trails', desc: 'GPU ring buffers and accumulation buffers — no frame ever touches the CPU.' },
  { icon: Aperture, title: 'Signature Look', desc: 'Filmic tone curve, 33³ 3D LUT, luma-masked grain, vignette — all real-time.' },
  { icon: Images, title: 'Gallery & remix', desc: 'Reload any composite\u2019s two exposures and re-blend them later.' },
]

export function PermissionGate({
  onStartCamera,
  onStartDemo,
  cameraStarting,
  error,
}: {
  onStartCamera: () => void
  onStartDemo: () => void
  cameraStarting: boolean
  error: string | null
}) {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-neutral-950 px-4 py-10 text-neutral-100">
      <div className="w-full max-w-md space-y-6">
        <header className="space-y-3 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-amber-500/10 ring-1 ring-amber-500/30">
            <Aperture className="h-7 w-7 text-amber-400" aria-hidden />
          </div>
          <div>
            <h1 className="text-3xl font-semibold tracking-tight">AuroraCam</h1>
            <p className="mt-1 text-sm text-neutral-400">
              Real-time creative camera — double exposure, temporal effects and a film-style
              signature look, rendered on the GPU <em>before</em> you press the shutter.
            </p>
          </div>
        </header>

        <ul className="space-y-2.5">
          {FEATURES.map((f) => (
            <li key={f.title} className="flex gap-3 rounded-xl bg-neutral-900/60 p-3 ring-1 ring-white/5">
              <f.icon className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" aria-hidden />
              <div>
                <p className="text-xs font-medium text-neutral-200">{f.title}</p>
                <p className="mt-0.5 text-[11px] leading-snug text-neutral-500">{f.desc}</p>
              </div>
            </li>
          ))}
        </ul>

        {error && (
          <Alert variant="destructive">
            <AlertTitle>Camera unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="space-y-2.5">
          <Button
            size="lg"
            className="h-12 w-full bg-amber-500 text-base font-semibold text-black hover:bg-amber-400"
            onClick={onStartCamera}
            disabled={cameraStarting}
          >
            {cameraStarting ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />
            ) : (
              <Camera className="mr-2 h-4 w-4" aria-hidden />
            )}
            {cameraStarting ? 'Starting camera…' : 'Enable camera'}
          </Button>
          <Button
            size="lg"
            variant="outline"
            className="h-11 w-full border-white/15 bg-transparent text-sm text-neutral-300 hover:bg-white/10 hover:text-neutral-100"
            onClick={onStartDemo}
          >
            <MonitorPlay className="mr-2 h-4 w-4" aria-hidden />
            Try demo mode
          </Button>
        </div>

        <p className="flex items-center justify-center gap-1.5 text-center text-[11px] text-neutral-600">
          <ShieldCheck className="h-3.5 w-3.5" aria-hidden />
          Frames are processed locally on your GPU. Nothing is uploaded unless you save a capture.
        </p>
      </div>
    </main>
  )
}

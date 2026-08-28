'use client'

/**
 * AuroraCam — Gallery overlay.
 *
 * Full-screen, dark photographic overlay listing saved captures
 * (kind `single` / `composite`) in a responsive grid. Opening a card
 * fetches the full record (with the full-resolution JPEG) in a dialog
 * with metadata, download, remix (composites only) and delete actions.
 */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ArrowLeft, Camera, Download, RotateCw, Trash2, Wand2 } from 'lucide-react'
import { toast } from 'sonner'

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import {
  BLEND_MODES,
  CREATIVE_MODE_LABELS,
  isCreativeMode,
  type CaptureMeta,
  type RemixPayload,
} from '@/lib/aurora/types'

interface GalleryScreenProps {
  /** Bump to force a refetch. */
  refreshKey: number
  onBack: () => void
  onRemix: (payload: RemixPayload) => void
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof DOMException && cause.name === 'AbortError'
}

function modeLabel(mode: string): string {
  return isCreativeMode(mode) ? CREATIVE_MODE_LABELS[mode] : mode
}

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-2 border-b border-white/5 py-1">
      <dt className="shrink-0 text-[11px] tracking-wide text-neutral-500 uppercase">{label}</dt>
      <dd className="min-w-0 truncate text-right text-xs text-neutral-200">{value}</dd>
    </div>
  )
}

export function GalleryScreen({ refreshKey, onBack, onRemix }: GalleryScreenProps) {
  const [items, setItems] = useState<CaptureMeta[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [nonce, setNonce] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<CaptureMeta | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [remixing, setRemixing] = useState(false)

  const refresh = useCallback(() => setNonce((value) => value + 1), [])

  const dateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }),
    []
  )

  /** Load the grid list; aborts + ignores stale responses on cleanup. */
  useEffect(() => {
    const controller = new AbortController()
    let stale = false
    setLoading(true)
    setError(null)

    fetch('/api/captures?limit=100', { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(`Request failed (${response.status})`)
        return (await response.json()) as { items: CaptureMeta[] }
      })
      .then((payload) => {
        if (!stale) setItems(payload.items)
      })
      .catch((cause: unknown) => {
        if (stale || isAbortError(cause)) return
        setError(cause instanceof Error ? cause.message : 'Failed to load captures')
      })
      .finally(() => {
        if (!stale) setLoading(false)
      })

    return () => {
      stale = true
      controller.abort()
    }
  }, [refreshKey, nonce])

  /** Load the full record for the detail dialog whenever one is opened. */
  useEffect(() => {
    if (selectedId === null) {
      setDetail(null)
      setDetailLoading(false)
      return
    }
    const controller = new AbortController()
    let stale = false
    setDetail(null)
    setDetailLoading(true)

    fetch(`/api/captures/${encodeURIComponent(selectedId)}`, { signal: controller.signal })
      .then(async (response) => {
        if (response.status === 404) throw new Error('Not found')
        if (!response.ok) throw new Error(`Request failed (${response.status})`)
        return (await response.json()) as CaptureMeta
      })
      .then((item) => {
        if (!stale) setDetail(item)
      })
      .catch((cause: unknown) => {
        if (stale || isAbortError(cause)) return
        toast.error(cause instanceof Error ? cause.message : 'Failed to load capture')
      })
      .finally(() => {
        if (!stale) setDetailLoading(false)
      })

    return () => {
      stale = true
      controller.abort()
    }
  }, [selectedId])

  const handleDownload = useCallback(() => {
    if (!detail?.data) return
    const anchor = document.createElement('a')
    anchor.download = `auroracam-${detail.id}.jpg`
    anchor.href = detail.data
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  }, [detail])

  /** Fetch the stored exposures of the composite's group and hand them to the remix editor. */
  const handleRemix = useCallback(async () => {
    if (!detail) return
    setRemixing(true)
    try {
      const response = await fetch(
        `/api/captures?group=${encodeURIComponent(detail.groupId)}&full=1`
      )
      if (!response.ok) throw new Error(`Request failed (${response.status})`)
      const payload = (await response.json()) as { items: CaptureMeta[] }
      const first = payload.items.find((item) => item.kind === 'first')
      const second = payload.items.find((item) => item.kind === 'second')
      if (!first?.data || !second?.data) {
        toast.error('Stored exposures not found for this capture')
        return
      }
      onRemix({
        groupId: detail.groupId,
        first: first.data,
        second: second.data,
        format: detail.format,
        blendMode: detail.blendMode,
        opacity: detail.opacity,
      })
    } catch (cause: unknown) {
      toast.error(cause instanceof Error ? cause.message : 'Failed to load exposures')
    } finally {
      setRemixing(false)
    }
  }, [detail, onRemix])

  const handleDelete = useCallback(async () => {
    if (!detail) return
    setDeleting(true)
    try {
      const response = await fetch(`/api/captures/${encodeURIComponent(detail.id)}`, {
        method: 'DELETE',
      })
      if (!response.ok) throw new Error(`Request failed (${response.status})`)
      setItems((previous) => previous.filter((item) => item.id !== detail.id))
      toast.success('Deleted')
      setConfirmDelete(false)
      setSelectedId(null)
    } catch (cause: unknown) {
      toast.error(cause instanceof Error ? cause.message : 'Failed to delete capture')
      setConfirmDelete(false)
    } finally {
      setDeleting(false)
    }
  }, [detail])

  const countLabel = loading
    ? 'Loading…'
    : `${items.length} ${items.length === 1 ? 'capture' : 'captures'}`

  return (
    <div className="fixed inset-0 z-40 overflow-y-auto bg-neutral-950 text-neutral-200 [&::-webkit-scrollbar]:w-2 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-neutral-800 [&::-webkit-scrollbar-track]:bg-transparent">
      <header className="sticky top-0 z-10 border-b border-white/10 bg-neutral-950/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-2 p-3 sm:gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={onBack}
            aria-label="Back to camera"
            className="text-neutral-300 hover:bg-white/5 hover:text-amber-400"
          >
            <ArrowLeft />
          </Button>
          <div className="flex min-w-0 flex-col">
            <h1 className="text-base font-semibold tracking-wide text-neutral-200">Gallery</h1>
            <p className="text-xs text-neutral-500">{countLabel}</p>
          </div>
          <Button
            variant="outline"
            onClick={refresh}
            disabled={loading}
            className="ml-auto h-9 border-neutral-800 bg-neutral-900 text-neutral-300 hover:border-amber-400/40 hover:bg-neutral-800 hover:text-amber-400"
          >
            <RotateCw className={loading ? 'animate-spin' : undefined} />
            Refresh
          </Button>
        </div>
      </header>

      <main className="mx-auto max-w-6xl p-3">
        {loading ? (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {Array.from({ length: 6 }, (_, index) => (
              <div key={index} className="overflow-hidden rounded-lg bg-neutral-900 ring-1 ring-white/5">
                <Skeleton className="aspect-[4/3] w-full rounded-none" />
                <div className="space-y-1.5 p-2">
                  <Skeleton className="h-3 w-1/2" />
                  <Skeleton className="h-3 w-1/3" />
                </div>
              </div>
            ))}
          </div>
        ) : error !== null ? (
          <div className="flex flex-col items-center gap-4 py-24 text-center">
            <p className="text-sm text-neutral-400">{error}</p>
            <Button
              onClick={refresh}
              className="bg-amber-500 text-black hover:bg-amber-400"
            >
              <RotateCw /> Try again
            </Button>
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-4 py-24 text-center">
            <div className="flex size-14 items-center justify-center rounded-full bg-neutral-900 ring-1 ring-white/5">
              <Camera className="size-6 text-amber-400" />
            </div>
            <p className="text-sm text-neutral-400">No captures yet — go shoot something</p>
            <Button onClick={onBack} className="bg-amber-500 text-black hover:bg-amber-400">
              <ArrowLeft /> Back to camera
            </Button>
          </div>
        ) : (
          <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {items.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => setSelectedId(item.id)}
                  aria-label={`Open capture from ${dateFormatter.format(new Date(item.createdAt))}`}
                  className="w-full overflow-hidden rounded-lg bg-neutral-900 text-left ring-1 ring-white/5 transition outline-none hover:ring-amber-400/40 focus-visible:ring-2 focus-visible:ring-amber-400"
                >
                  {item.thumb !== '' ? (
                    <img
                      src={item.thumb}
                      alt={`${modeLabel(item.mode)} capture`}
                      loading="lazy"
                      decoding="async"
                      className="aspect-[4/3] w-full object-cover"
                    />
                  ) : (
                    <div className="flex aspect-[4/3] w-full items-center justify-center bg-neutral-800">
                      <Camera className="size-6 text-neutral-600" />
                    </div>
                  )}
                  <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1 px-2 py-1.5">
                    <Badge
                      variant="outline"
                      className="border-neutral-700 px-1.5 text-[10px] text-neutral-300"
                    >
                      {item.format}
                    </Badge>
                    {item.kind === 'composite' ? (
                      <span className="rounded-sm bg-amber-500/15 px-1.5 py-0.5 text-[10px] font-medium text-amber-400">
                        DX
                      </span>
                    ) : item.kind === 'single' ? (
                      <span className="rounded-sm bg-neutral-800 px-1.5 py-0.5 text-[10px] font-medium text-neutral-400">
                        Single
                      </span>
                    ) : null}
                    <span className="min-w-0 flex-1 truncate text-[11px] text-neutral-400">
                      {modeLabel(item.mode)}
                    </span>
                    <span className="text-[10px] whitespace-nowrap text-neutral-500">
                      {dateFormatter.format(new Date(item.createdAt))}
                    </span>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </main>

      <Dialog
        open={selectedId !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedId(null)
        }}
      >
        <DialogContent className="max-h-[90vh] gap-3 overflow-y-auto border-neutral-800 bg-neutral-900 p-4 text-neutral-200 sm:max-w-2xl sm:p-6">
          <DialogHeader>
            <DialogTitle className="text-neutral-100">
              {detail?.kind === 'composite' ? 'Composite capture' : 'Capture'}
            </DialogTitle>
            <DialogDescription className="truncate text-xs text-neutral-500">
              {detail ? `auroracam-${detail.id}` : (selectedId ?? '')}
            </DialogDescription>
          </DialogHeader>

          {detailLoading ? (
            <Skeleton className="aspect-[4/3] w-full" />
          ) : detail?.data ? (
            <img
              src={detail.data}
              alt="Full-size capture"
              className="mx-auto max-h-[65vh] w-auto object-contain"
            />
          ) : (
            <div className="flex aspect-[4/3] items-center justify-center rounded-md bg-neutral-800 text-sm text-neutral-500">
              No image data
            </div>
          )}

          <dl className="grid grid-cols-1 gap-x-6 sm:grid-cols-2">
            <MetaRow label="Format" value={detail?.format ?? '—'} />
            <MetaRow label="Mode" value={detail ? modeLabel(detail.mode) : '—'} />
            <MetaRow
              label="Blend"
              value={detail ? (BLEND_MODES[detail.blendMode] ?? '—') : '—'}
            />
            <MetaRow
              label="Opacity"
              value={detail ? `${Math.round(detail.opacity * 100)}%` : '—'}
            />
            <MetaRow
              label="Look intensity"
              value={detail ? `${Math.round(detail.lookIntensity * 100)}%` : '—'}
            />
            <MetaRow label="LUT" value={detail?.lutName ? detail.lutName : '—'} />
            <MetaRow
              label="Dimensions"
              value={detail ? `${detail.width}×${detail.height}` : '—'}
            />
            <MetaRow
              label="Created"
              value={detail ? dateFormatter.format(new Date(detail.createdAt)) : '—'}
            />
          </dl>

          <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
            <Button
              variant="outline"
              onClick={handleDownload}
              disabled={detailLoading || !detail?.data}
              className="h-9 border-neutral-700 bg-neutral-900 text-neutral-200 hover:border-amber-400/40 hover:bg-neutral-800 hover:text-amber-400"
            >
              <Download /> Download
            </Button>
            {detail?.kind === 'composite' && (
              <Button
                variant="outline"
                onClick={() => void handleRemix()}
                disabled={remixing}
                className="h-9 border-neutral-700 bg-neutral-900 text-neutral-200 hover:border-amber-400/40 hover:bg-neutral-800 hover:text-amber-400"
              >
                <Wand2 className={remixing ? 'animate-pulse' : undefined} />
                {remixing ? 'Loading…' : 'Remix'}
              </Button>
            )}
            <AlertDialog open={confirmDelete} onOpenChange={setConfirmDelete}>
              <AlertDialogTrigger asChild>
                <Button
                  variant="destructive"
                  disabled={detailLoading || !detail}
                  className="h-9"
                >
                  <Trash2 /> Delete
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent className="border-neutral-800 bg-neutral-900 text-neutral-200">
                <AlertDialogHeader>
                  <AlertDialogTitle>Delete this capture?</AlertDialogTitle>
                  <AlertDialogDescription>
                    This permanently removes the capture from your gallery. This action cannot
                    be undone.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel className="h-9 border-neutral-700 bg-neutral-900 text-neutral-200 hover:bg-neutral-800 hover:text-neutral-100">
                    Cancel
                  </AlertDialogCancel>
                  <AlertDialogAction
                    disabled={deleting}
                    onClick={(event) => {
                      // Keep the confirm open until the request settles.
                      event.preventDefault()
                      void handleDelete()
                    }}
                    className="h-9 bg-red-600 text-white hover:bg-red-500"
                  >
                    {deleting ? 'Deleting…' : 'Delete'}
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default GalleryScreen

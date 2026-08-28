/**
 * Client helpers for persisting captures to the app gallery API
 * (the web equivalent of the MediaStore writer).
 */

import type { CaptureBundle, CapturePostDTO } from './types'

export async function saveCaptureBundle(b: CaptureBundle): Promise<number> {
  let saved = 0
  for (const img of b.images) {
    const body: CapturePostDTO = {
      groupId: b.groupId,
      kind: img.kind,
      format: b.format,
      mode: b.mode,
      blendMode: b.blendMode,
      opacity: b.opacity,
      lookIntensity: b.lookIntensity,
      lutName: b.lutName,
      width: img.width,
      height: img.height,
      data: img.dataUrl,
      thumb: img.thumb,
    }
    const res = await fetch('/api/captures', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!res.ok) {
      let message = `HTTP ${res.status}`
      try {
        const json: unknown = await res.json()
        if (json && typeof json === 'object' && 'error' in json && typeof json.error === 'string') {
          message = json.error
        }
      } catch {
        // ignore body parse errors, keep the status message
      }
      throw new Error(`Saving ${img.kind} failed: ${message}`)
    }
    saved++
  }
  return saved
}

export function downloadDataUrl(dataUrl: string, filename: string): void {
  const a = document.createElement('a')
  a.href = dataUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
}

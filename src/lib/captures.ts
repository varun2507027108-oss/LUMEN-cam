/**
 * AuroraCam — capture persistence helpers shared by the capture API routes.
 *
 * Maps Prisma `Capture` rows onto the wire shape `CaptureMeta`
 * (see `src/lib/aurora/types.ts`) and centralizes the JPEG data-url
 * constraints enforced on POST.
 */
import type { Capture } from '@prisma/client'

import { isCaptureKind, type CaptureMeta } from '@/lib/aurora/types'

export const JPEG_DATA_URL_PREFIX = 'data:image/jpeg;base64,'
export const MAX_DATA_LENGTH = 7_000_000
export const MAX_THUMB_LENGTH = 300_000

/**
 * A full `Capture` row, or a projected subset of one — list queries omit the
 * heavy `data` column unless `full=1` is requested.
 */
export type CaptureLike = Omit<Capture, 'data'> & Partial<Pick<Capture, 'data'>>

/**
 * Map a Prisma Capture row to the API `CaptureMeta` shape.
 * Dates become ISO strings; the heavy `data` field is only included
 * when explicitly requested (`full=1` / detail endpoint).
 */
export function toCaptureMeta(record: CaptureLike, includeData: boolean): CaptureMeta {
  const meta: CaptureMeta = {
    id: record.id,
    groupId: record.groupId,
    // The column is a plain string; POST validation pins it to the four
    // literal kinds, so guard once here instead of trusting the DB blindly.
    kind: isCaptureKind(record.kind) ? record.kind : 'single',
    format: record.format,
    mode: record.mode,
    blendMode: record.blendMode,
    opacity: record.opacity,
    lookIntensity: record.lookIntensity,
    lutName: record.lutName,
    width: record.width,
    height: record.height,
    createdAt: record.createdAt.toISOString(),
    thumb: record.thumb,
  }
  if (includeData && record.data !== undefined) meta.data = record.data
  return meta
}

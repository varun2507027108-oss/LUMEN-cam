/**
 * AuroraCam capture API — collection endpoint.
 *
 * GET  /api/captures?group=<id>&full=1&limit=24
 *   - no `group`: only `single` / `composite` kinds, newest first, plus a
 *     computed `hasPair` flag (group contains BOTH a `first` AND a `second`
 *     exposure — resolved with a single groupBy, no N+1).
 *   - with `group`: every kind of that group (remix editor source), newest first.
 *   - `full=1` includes the full-resolution `data` field.
 *
 * POST /api/captures  body = CapturePostDTO → 201 { id } | 400 { error }
 */
import { NextResponse } from 'next/server'
import { z } from 'zod'

import { db } from '@/lib/db'
import {
  JPEG_DATA_URL_PREFIX,
  MAX_DATA_LENGTH,
  MAX_THUMB_LENGTH,
  toCaptureMeta,
} from '@/lib/captures'

/** Fields every list query returns; `data` is opt-in via `full=1`. */
const LIST_SELECT = {
  id: true,
  groupId: true,
  kind: true,
  format: true,
  mode: true,
  blendMode: true,
  opacity: true,
  lookIntensity: true,
  lutName: true,
  width: true,
  height: true,
  thumb: true,
  createdAt: true,
} as const

/** POST payload schema — mirrors CapturePostDTO with hard limits. */
const capturePostSchema = z.object({
  groupId: z.string().min(1),
  kind: z.enum(['single', 'first', 'second', 'composite']),
  format: z.enum(['4:3', '1:1', '65:24']),
  mode: z.enum(['normal', 'dx', 'echo', 'trails']),
  blendMode: z.number().int().min(0).max(5),
  opacity: z.number().min(0).max(1),
  lookIntensity: z.number().min(0).max(1),
  lutName: z.string().max(500),
  width: z.number().int().positive(),
  height: z.number().int().positive(),
  data: z.string().startsWith(JPEG_DATA_URL_PREFIX).max(MAX_DATA_LENGTH),
  thumb: z
    .string()
    .max(MAX_THUMB_LENGTH)
    .refine(
      (value) => value === '' || value.startsWith(JPEG_DATA_URL_PREFIX),
      'thumb must be empty or a jpeg data url'
    ),
})

/** Parse `limit` (default 24, clamped to 1..100); garbage falls back to 24. */
function parseLimit(raw: string | null): number {
  if (raw === null) return 24
  const parsed = Number(raw)
  if (!Number.isFinite(parsed)) return 24
  return Math.min(Math.max(Math.trunc(parsed), 1), 100)
}

export async function GET(request: Request): Promise<NextResponse> {
  const { searchParams } = new URL(request.url)
  const group = searchParams.get('group')
  const includeData = searchParams.get('full') === '1'
  const limit = parseLimit(searchParams.get('limit'))
  const select = includeData ? { ...LIST_SELECT, data: true } : LIST_SELECT

  if (group !== null && group !== '') {
    // Remix editor: every kind belonging to the group, newest first.
    const rows = await db.capture.findMany({
      where: { groupId: group },
      orderBy: { createdAt: 'desc' },
      take: limit,
      select,
    })
    return NextResponse.json({ items: rows.map((row) => toCaptureMeta(row, includeData)) })
  }

  const rows = await db.capture.findMany({
    where: { kind: { in: ['single', 'composite'] } },
    orderBy: { createdAt: 'desc' },
    take: limit,
    select,
  })

  // One grouped query over the pair kinds, scoped to the page's groups —
  // resolves `hasPair` for the whole page without per-item queries.
  const groupIds = [...new Set(rows.map((row) => row.groupId))]
  const pairRows =
    groupIds.length > 0
      ? await db.capture.groupBy({
          by: ['groupId', 'kind'],
          where: { kind: { in: ['first', 'second'] }, groupId: { in: groupIds } },
        })
      : []
  const firstGroups = new Set(
    pairRows.filter((row) => row.kind === 'first').map((row) => row.groupId)
  )
  const secondGroups = new Set(
    pairRows.filter((row) => row.kind === 'second').map((row) => row.groupId)
  )

  const items = rows.map((row) => {
    const meta = toCaptureMeta(row, includeData)
    meta.hasPair = firstGroups.has(row.groupId) && secondGroups.has(row.groupId)
    return meta
  })
  return NextResponse.json({ items })
}

export async function POST(request: Request): Promise<NextResponse> {
  let body: unknown
  try {
    body = await request.json()
  } catch {
    return NextResponse.json({ error: 'Invalid JSON body' }, { status: 400 })
  }

  const parsed = capturePostSchema.safeParse(body)
  if (!parsed.success) {
    const issue = parsed.error.issues[0]
    const path = issue && issue.path.length > 0 ? `${issue.path.join('.')}: ` : ''
    return NextResponse.json(
      { error: `${path}${issue ? issue.message : 'Invalid payload'}` },
      { status: 400 }
    )
  }

  try {
    const created = await db.capture.create({ data: { ...parsed.data } })
    return NextResponse.json({ id: created.id }, { status: 201 })
  } catch {
    return NextResponse.json({ error: 'Failed to save capture' }, { status: 500 })
  }
}

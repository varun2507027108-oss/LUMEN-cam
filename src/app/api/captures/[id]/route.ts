/**
 * AuroraCam capture API — item endpoint.
 *
 * GET    /api/captures/[id] → the full record mapped to `CaptureMeta`
 *                             (including `data`), or 404 { error: "Not found" }.
 * DELETE /api/captures/[id] → { ok: true }, or 404.
 *
 * Next.js 16: dynamic route params arrive as an async Promise.
 */
import { NextResponse } from 'next/server'

import { db } from '@/lib/db'
import { toCaptureMeta } from '@/lib/captures'

interface RouteContext {
  params: Promise<{ id: string }>
}

export async function GET(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  const { id } = await params

  const record = await db.capture.findUnique({ where: { id } })
  if (!record) {
    return NextResponse.json({ error: 'Not found' }, { status: 404 })
  }

  return NextResponse.json(toCaptureMeta(record, true))
}

export async function DELETE(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  const { id } = await params

  const record = await db.capture.findUnique({ where: { id }, select: { id: true, kind: true, groupId: true } })
  if (!record) {
    return NextResponse.json({ error: 'Not found' }, { status: 404 })
  }

  // Deleting a composite/single purges its whole group — the stored first/
  // second exposures only exist to feed the remix editor and are useless
  // once the main shot is gone.
  const where = record.kind === 'composite' || record.kind === 'single' ? { groupId: record.groupId } : { id }
  await db.capture.deleteMany({ where })

  return NextResponse.json({ ok: true })
}

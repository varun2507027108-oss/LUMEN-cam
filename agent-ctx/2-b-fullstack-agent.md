# Task 2-b — fullstack agent (persistence + gallery layer)

## Context
- Task: AuroraCam persistence + gallery layer (Prisma Capture model, capture REST API, GalleryScreen overlay).
- Project worklog: `/home/z/my-project/worklog.md` (read it first — contains the API contract and worklog protocol).
- Shared types: `/home/z/my-project/src/lib/aurora/types.ts` (CaptureMeta, CapturePostDTO, RemixPayload, BLEND_MODES, CREATIVE_MODE_LABELS...). Do not modify anything under `src/lib/aurora/`.

## Files I created / changed
| File | What |
|---|---|
| `prisma/schema.prisma` | Added `Capture` model (kept User/Post). Pushed with `bun run db:push`. |
| `src/lib/captures.ts` | API-side helpers: `toCaptureMeta()` mapper (Date→ISO, optional `data`), `CaptureLike` type, JPEG data-url constants (prefix / 7,000,000 / 300,000). |
| `src/app/api/captures/route.ts` | GET (list: `group`, `full=1`, `limit` clamped 1..100 default 24) + POST (zod v4 validation → 201 `{id}` / 400 `{error}`). |
| `src/app/api/captures/[id]/route.ts` | GET (full `CaptureMeta` incl. `data`) + DELETE, Next.js 16 async `params`, 404 handling. |
| `src/components/aurora/GalleryScreen.tsx` | `'use client'` full-screen overlay gallery (named + default export). |

## API contract implemented (for other agents)
- `GET /api/captures?limit=24` → `{ items: CaptureMeta[] }` — only kinds `single`/`composite`, newest first, `thumb` included, `data` omitted, `hasPair` computed (one extra `groupBy(['groupId','kind'])` scoped to the page — no N+1).
- `GET /api/captures?group=<id>&full=1` → all kinds of the group incl. `data`.
- `POST /api/captures` (CapturePostDTO) → `201 { id }` / `400 { error }`.
- `GET /api/captures/[id]` → the mapped `CaptureMeta` object at the TOP LEVEL (not wrapped in `{ item }`), incl. `data`; 404 `{ error: "Not found" }`.
- `DELETE /api/captures/[id]` → `{ ok: true }` or 404.
- `hasPair` only appears on the default (no-group) listing.

## GalleryScreen integration notes (Task 1)
```tsx
<GalleryScreen refreshKey={n} onBack={...} onRemix={(p) => ...} />
```
- Fetches `/api/captures?limit=100` on mount / `refreshKey` change / manual Refresh (AbortController + stale guards).
- `onRemix` receives `RemixPayload { groupId, first, second, format, blendMode, opacity }` (data URLs).
- Toasts come from `sonner` (`import { toast } from 'sonner'`) — the layout must mount `<Toaster />` (Task 1 owns layout; do NOT add a second Toaster inside GalleryScreen).
- Dark photographic theme: `bg-neutral-950`, `text-neutral-200`, amber accents only (no blue/indigo).

## Verification done
- `bunx tsc --noEmit`: clean (pre-existing errors only in `examples/`, `skills/` — eslint-ignored).
- `bun run lint`: 0 errors, 0 warnings.
- curl E2E against dev server :3000 — POST composite/first/second (201), list shows only composite with `hasPair:true`, detail GET incl. `data`, group GET returns all 3 kinds, 8 invalid POSTs → 400 with short messages, unknown id GET/DELETE → 404, `limit=500`/`limit=abc` tolerated, DELETEs → `{ok:true}`, table count back to 0.
- dev.log confirms 2 SQL statements per list request (findMany + groupBy) and no errors.

## Deviations
- Did NOT add `{/* eslint-disable-next-line @next/next/no-img-element */}` above `<img>` tags: `eslint.config.mjs` disables that rule globally, so the directives triggered "unused eslint-disable directive" warnings. Removed to keep lint at zero warnings.
- `lutName` additionally capped at 500 chars in POST validation (harmless hardening, not in the spec).

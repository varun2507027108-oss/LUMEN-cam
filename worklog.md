# AuroraCam Web — Shared Worklog

## Project Context

The user's request is the "AuroraCam" Android master prompt (real-time creative camera:
live double exposure, motion echo, light trails, signature film-style look with a 33³ 3D LUT).
This sandbox is a **Next.js 16 web environment** — it cannot compile Kotlin or produce APKs —
so we are building AuroraCam as a faithful **web app**:

- GPU-only preview pipeline (WebGL2 instead of GLES; camera frames → texture → shaders → screen,
  zero per-frame CPU pixel work; readPixels only at shutter press).
- Blend formulas pinned to the user's spec: Screen = 1-(1-A)(1-B); Lighten = max(A,B);
  Add = min(A+B,1); Multiply = A*B; Overlay = per-channel A<0.5 ? 2AB : 1-2(1-A)(1-B);
  Normal = crossfade. Final = mix(B, blended, uOpacity).
- Signature Look pass: tone curve (highlight shoulder + lifted matte blacks) → 3D LUT
  (sampler3D, 33³, LINEAR, coord = c*(N-1)/N + 0.5/N, DOMAIN_MIN/MAX support) →
  luminance-masked animated grain (weight = 4*l*(1-l)) → vignette. Intensity mix.
- Formats: 4:3 (3200×2400), 1:1 (2448×2448), 65:24 XPAN (3250×1200, orange frame lines).
  Preview buffers: 1280×960 / 960×960 / 1300×480. No 4000×3000 exists.
- Captures: JPEG quality 95, saved via POST /api/captures (Prisma/SQLite) with thumbnails;
  gallery + remix (reload first+second exposures as GPU textures, re-blend, re-save).
- Demo Mode: animated night scene (aurora ribbons + drifting bokeh orbs) as a fallback
  "camera" so the app works without a webcam (and for headless verification).
- NEVER use the word "Hasselblad" anywhere in code, UI, or docs. The look feature is
  "Signature Look". UI accent color: amber/orange on near-black. No blue/indigo accents.

## Shared contracts

All types live in `src/lib/aurora/types.ts` (AuroraParams, CaptureBundle, CapturePostDTO,
CaptureMeta, RemixPayload, FORMATS, BLEND_MODES, DEFAULT_PARAMS...). Read it before coding.
The engine (`src/lib/aurora/engine.ts`, owned by the main agent) exposes:
`new AuroraEngine(canvas)`, `attachVideo(video)`, `startDemo()`, `updateParams(p)`,
`onStatus(cb)`, `captureFirst()`, `retakeFirst()`, `capture(): Promise<CaptureBundle>`,
`loadRemixPair(firstUrl, secondUrl)`, `clearTrails()`, `setLutFromCube(name, text)`,
`useProceduralLut()`, `setPaused(bool)`, `dispose()`.

## Task assignments

- Task 0 (main agent): types.ts, this worklog — DONE.
- Task 2-a (general-purpose agent): `src/lib/aurora/lut.ts` (makeProceduralLut, parseCube) +
  `src/lib/aurora/demoScene.ts` (DemoScene class).
- Task 2-b (full-stack agent): prisma `Capture` model + db push, `src/app/api/captures/route.ts`,
  `src/app/api/captures/[id]/route.ts`, `src/components/aurora/GalleryScreen.tsx`.
- Task 1 (main agent): `src/lib/aurora/{glUtils,shaders,engine,client}.ts`,
  `src/components/aurora/{PermissionGate,CameraScreen,panels,RemixScreen}.tsx`,
  `src/app/page.tsx`, layout + theme.
- Task 3 (main agent): integration, lint, dev.log check, agent-browser E2E verification, fixes.

## API contract (Task 2-b owns server side)

- `GET /api/captures?limit=24` → `{ items: CaptureMeta[] }` — only kind single/composite,
  newest first, with `thumb` and computed `hasPair` (group contains first AND second).
- `GET /api/captures?group=<id>&full=1` → all kinds of the group, including `data`.
- `POST /api/captures` body = CapturePostDTO → `201 { id }`. Validate kind/format/mode enums,
  `data` starts with `data:image/jpeg;base64,` and ≤ 7,000,000 chars, `thumb` ≤ 300,000 chars.
- `GET /api/captures/[id]` → full record incl. `data` (404 if missing).
- `DELETE /api/captures/[id]` → `{ ok: true }`.
- Next.js 16: route handler params are async — `const { id } = await params`.

## Worklog protocol

Every agent MUST append (never overwrite) a section to this file when finishing its Task ID,
using this template:

```markdown
---
Task ID: <id>
Agent: <agent name>
Task: <what you were asked to do>

Work Log:
- <concrete steps>

Stage Summary:
- <key results / decisions / artifacts>
```

---
Task ID: 2-b
Agent: fullstack agent (persistence + gallery layer)

Task: Prisma `Capture` model + db push, capture REST API (`/api/captures`, `/api/captures/[id]`), and the `GalleryScreen` overlay component.

Work Log:
- Read worklog.md, types.ts, schema.prisma, db.ts, package.json, and the shadcn/ui components (button, dialog, alert-dialog, badge, skeleton, sonner).
- Added the `Capture` model (groupId/kind/format/mode/blendMode/opacity/lookIntensity/lutName/width/height/thumb/data/createdAt + indexes on groupId & createdAt) to `prisma/schema.prisma`, keeping User/Post untouched. Ran `bun run db:push` — success, Prisma Client regenerated.
- Created `src/lib/captures.ts` (shared API-side helpers): `toCaptureMeta(record, includeData)` mapper (Prisma row → `CaptureMeta`, dates → ISO strings, kind narrowed via `isCaptureKind` guard), `CaptureLike` type (rows with `data` optionally projected out), and the JPEG data-url constants (prefix, 7M data cap, 300k thumb cap).
- Created `src/app/api/captures/route.ts`:
  - GET: `group` (all kinds of that group), `full=1` (include `data`), `limit` (default 24, clamped 1..100, garbage-tolerant). Default listing returns only `single`/`composite` newest-first; `hasPair` computed with ONE extra `groupBy(['groupId','kind'])` scoped to the page's groupIds (no N+1). Always `{ items: CaptureMeta[] }`.
  - POST: zod v4 schema (enums for kind/format/mode, blendMode int 0..5, opacity/lookIntensity 0..1, positive int width/height, data prefix+7M cap, thumb empty-or-prefix+300k cap, lutName ≤500) → 201 `{ id }` / 400 `{ error }` with short `path: message` text / 500 on unexpected DB failure.
- Created `src/app/api/captures/[id]/route.ts` (Next.js 16 async params): GET → full `CaptureMeta` incl. `data` or 404 `{ error: 'Not found' }`; DELETE via `deleteMany` → `{ ok: true }` or 404. Explicit `Promise<NextResponse>` return types, no `any`.
- Created `src/components/aurora/GalleryScreen.tsx` (`'use client'`, exported + default export): fixed inset-0 z-40 overlay on `bg-neutral-950` with amber accents (no blue/indigo), sticky top bar (back w/ aria-label "Back to camera", "Gallery" title, live count, Refresh with spinning icon), responsive grid (2/3/4/5 cols at base/sm/lg/xl, gap-2, p-3, max-w-6xl mx-auto), cards with thumb img (lazy, aspect-[4/3] object-cover) + caption row (format Badge outline text-[10px], Single/DX kind chips, mode label via CREATIVE_MODE_LABELS, short Intl date), skeleton/error/empty states, detail Dialog (fetch-on-open with AbortController + stale guards, skeleton, full image max-h-[65vh], 8-row metadata dl incl. BLEND_MODES lookup), Download (anchor download="auroracam-<id>.jpg", disabled while loading), Remix (composite only; fetches group with full=1, missing exposures → `toast.error('Stored exposures not found for this capture')`), Delete (AlertDialog confirm, controlled so it stays open during the request, then removes from state + `toast.success('Deleted')`). Uses only existing shadcn/ui components + lucide icons + sonner toast (no extra Toaster).
- Type-check: `bunx tsc --noEmit` clean for all project files (only pre-existing errors in examples/ and skills/, which are eslint-ignored).
- Lint: `bun run lint` → 0 errors, 0 warnings. (The requested `eslint-disable-next-line @next/next/no-img-element` comments were REMOVED because this project's eslint config turns that rule off, making the directives "unused" warnings.)
- curl sanity-check against the running dev server (port 3000): POST composite → 201 `{id}`; POST first+second → 201×2; GET list → only the composite, `hasPair:true`; GET /[id] → full record with data; GET ?group=…&full=1 → [second, first, composite] all with data; 8 invalid POSTs → 400 with short messages (bad kind/format/mode enum, png prefix, webp thumb, blendMode 9, empty groupId, float width, malformed JSON); GET/DELETE unknown id → 404; limit=500 and limit=abc → 200 (clamped/default); DELETE ×3 → `{ok:true}`; final list empty; `db.capture.count() === 0` (test rows cleaned up). dev.log shows exactly 2 SQL queries per list request (findMany + groupBy).

Stage Summary:
- Artifacts: `prisma/schema.prisma` (Capture model added, pushed), `src/lib/captures.ts`, `src/app/api/captures/route.ts`, `src/app/api/captures/[id]/route.ts`, `src/components/aurora/GalleryScreen.tsx`.
- API notes for integrators (Task 1/3): `GET /api/captures/[id]` returns the mapped `CaptureMeta` object directly at the top level (not wrapped in `{ item }`); list endpoints return `{ items: [...] }`. `hasPair` is only present on the default (no-group) listing. Gallery list fetch uses `limit=100`.
- Deviation: omitted the `@next/next/no-img-element` eslint-disable comments in GalleryScreen (rule is globally disabled in eslint.config.mjs; the comments produced "unused directive" warnings).
- Ready for Task 1 integration: mount `<GalleryScreen refreshKey={n} onBack={...} onRemix={...} />` from page.tsx; sonner `<Toaster />` must be mounted in the layout for toasts to render (Task 1 owns layout).

---
Task ID: 2-a
Agent: general-purpose
Task: Build `src/lib/aurora/lut.ts` (procedural "Aurora Warm" LUT + .cube parser) and `src/lib/aurora/demoScene.ts` (deterministic animated Demo Mode night scene).

Work Log:
- Read worklog.md + src/lib/aurora/types.ts for shared contracts before coding.
- Implemented makeProceduralLut (default 33³, RGBA8888, flat index = r + g*size + b*size², gentle S-curve → warm midtones → green desaturation, luma computed pre-curve) and proceduralLutName → "Aurora Warm (procedural)".
- Implemented parseCube: LF/CRLF, trimmed lines, skips #/TITLE/COMMENT (case-insensitive), LUT_3D_SIZE validation (integer 2-64, conflict on duplicate differing values), LUT_1D_SIZE rejection, DOMAIN_MIN/MAX with defaults + min<max check, 3+ column data rows (extras ignored), values clamped to [0,1], exact N³ row count with expected-vs-found error message. .cube row order equals our flat order, so row k maps to byte offset k*4 directly.
- Implemented DemoScene: fixed-seed mulberry32 (0x41555230) used only in the constructor; pre-rendered static sky layer (near-black gradient, hazy moon, teal horizon + warm town glows) and ground strip (two ridges, mist, 26 tiny warm lights) plus glow/bokeh/star sprites; per frame: twinkling stars (sin alpha), 3 additive aurora ribbons (teal/green/magenta, layered translucent strokes with persistent gradients), 8 drifting amber bokeh orbs (r 20-70, horizontal wrap + vertical bob), 2 bright car headlight pairs + red taillights + wet-road reflections. render(t) is pure in t, never throws, allocates nothing per frame; dispose() zeroes the offscreen layers and makes render a no-op.
- Verified lut.ts with a throwaway bun test (round-trip through .cube text, CRLF/lowercase/extra columns/out-of-range clamping, 11 error cases) — all pass. Smoke-tested DemoScene in bun with a stubbed 2D context (counts, determinism across instances, render at t = 0…1e9, dispose safety) — all pass. Type-checked both files with tsc --strict; scratch test files removed afterwards.
- Ran `bun run lint` — clean (no errors or warnings).

Stage Summary:
- Files created: src/lib/aurora/lut.ts (123 lines), src/lib/aurora/demoScene.ts (230 lines). No other project files touched; dev server not started; no build run.
- Exports: makeProceduralLut(size = 33): Uint8Array; proceduralLutName(): string; parseCube(text: string): ParsedCube { size; data; domainMin; domainMax }; class DemoScene { readonly canvas; constructor(); render(t: number); dispose() }.
- Engine integration notes: procedural and parsed .cube LUTs share the identical RGBA layout — upload either to a sampler3D as-is. DemoScene.canvas is an opaque 1600x1200 2D canvas, safe for texImage2D every frame; call dispose() on teardown.

---
Task ID: 0
Agent: main (Z.ai Code)
Task: Shared contracts — types.ts + worklog initialization

Work Log:
- Audited the request: Android "AuroraCam" master prompt cannot be built here (Next.js web sandbox, no Kotlin/APK toolchain); decided on a faithful web port with the same architecture and pinned formulas.
- Explored project: package.json (sonner, shadcn full set, prisma 6, zod 4), prisma/schema.prisma, src/lib/db.ts, dev server already running on :3000, DATABASE_URL=file:db/custom.db.
- Created src/lib/aurora/types.ts: FormatKey/CreativeMode/CaptureKind, FORMATS (4:3→3200×2400, 1:1→2448×2448, 65:24→3250×1200; preview buffers 1280×960 / 960×960 / 1300×480), BLEND_MODES (6, index-pinned to GLSL uMode), AuroraParams, DEFAULT_PARAMS, EngineStatus, CaptureBundle/CaptureImage/CapturePostDTO/CaptureMeta/RemixPayload + guards.
- Initialized this worklog with project context, engine API surface, API contract, task assignments.

Stage Summary:
- Single source of truth for all cross-agent contracts; no breaking changes after launch.

---
Task ID: 1
Agent: main (Z.ai Code)
Task: WebGL2 render pipeline, AuroraEngine, camera/remix UI, app shell

Work Log:
- src/lib/aurora/glUtils.ts: program builder with cached uniform locations, RGBA8 FBO factory (completeness-checked), source/upload textures.
- src/lib/aurora/shaders.ts: VS_QUAD + FS_BASE (aspect-crop + Y-flip + selfie mirror), FS_COPY, FS_BLEND (Screen/Lighten/Add/Multiply/Overlay/Normal — formulas verbatim from spec, Final = mix(B, blended, uOpacity), uFlipA), FS_ECHO, FS_TRAILS_ACCUM (bright-pass + decay + Lighten/Add), FS_TRAILS_SHOW, FS_LOOK (tone curve shoulder + matte blacks → sampler3D LUT with (N-1)/N + 0.5/N coords and DOMAIN_MIN/MAX → luma-masked animated grain 4l(1-l) → vignette → mix(clean, graded, uIntensity)).
- src/lib/aurora/engine.ts: AuroraEngine — one GL thread (rAF loop), per-frame source upload (video/demo canvas), pass pipeline base→creative→look→screen; DX first-exposure FBO freeze; echo ring buffer (5 FBOs, store-after-read, delay stride, fade weights, normalized); trails ping-pong accumulation; capture path re-renders at 3200×2400/2448×2448/3250×1200 with readPixels→row-flip→ImageData→JPEG q0.95 + 420px thumb (one-shot CPU only); LUT upload (procedural 33³ + .cube with domain support); status telemetry (fps over 2s window, source/buffer sizes, firstCaptured); remix pair loading as static GPU textures.
- src/lib/aurora/client.ts: saveCaptureBundle (sequential POSTs) + downloadDataUrl.
- UI: PermissionGate (camera/demo choice, graceful getUserMedia error mapping), CameraScreen (fitted-box letterboxed viewfinder, HUD, XPAN orange frame overlay, DX staged banner + Retake/Flip, trails tripod hint + clear, flash animation, two-stage shutter with 1/2 badge), panels.tsx (FormatPills, ModePills, LookPanel with .cube upload, DxPanel with blend Select, EchoPanel, TrailsPanel with Lighten/Add + clip warning), RemixScreen (own engine, static pair, save re-posts first+second+composite under a new group so remixes stay remixable), useFittedBox hook.
- page.tsx: gate ↔ camera + gallery/remix overlays, stream lifecycle + facing flip + mirror param, engine pause when overlayed.
- layout.tsx: AuroraCam metadata, dark html, sonner Toaster. globals.css: flash keyframes + aurora-scroll scrollbar.

Stage Summary:
- Full M0–M5 feature parity on the web platform; all shader math pinned to the spec.

---
Task ID: 3
Agent: main (Z.ai Code)
Task: Integration, lint, E2E browser verification, fixes

Work Log:
- Integrated 2-a/2-b modules; lint + tsc clean (pre-existing examples/skills errors excluded — untouched).
- agent-browser E2E on http://localhost:3000: gate → demo mode → engine running (26–27 fps under SwiftShader) → pixel-verified rendering (108KB screenshots, non-black).
- FIX #1 (critical, shader portability): SwiftShader/ANGLE rejects sampler-array indexing with loop variables (GLSL ES 3.00 constant-index rule) → FS_ECHO unrolled to uEcho0..uEcho4 uniforms.
- FIX #2 (critical, production bug): dispose() called WEBGL_lose_context.loseContext(), permanently killing the context for any successor engine on the same canvas (source switch / Fast Refresh) → removed loseContext, dispose now deletes VAO/quad buffer too and removes the context-lost listener; verified engine survives HMR remount (27 fps after).
- FIX #3 (UX): capture-success toast with a Download action lingered over the top bar → explicit duration 6000.
- FIX #4 (data hygiene): DELETE /api/captures/[id] now purges the whole group when deleting a composite/single (stored first/second exposures are remix-only data).
- Verified DX flow end-to-end: stage-1 banner → stage-2 composite → 3 DB rows (first/second/composite, 3200×2400, Aurora Warm LUT) → composite decodes as valid 2.4MB JPEG.
- Verified gallery: grid card from thumb, detail dialog with metadata, Download/Remix/Delete; remix editor re-blends stored pair (16 fps), saves new group (DB: 3+3 rows), delete removes card.
- Verified echo capture (single 3200×2400), trails accumulation (VLM-confirmed streaks) + capture, XPAN (buf 1300×480, 6 orange overlay elements), format pill switching.
- Verified Signature Look deterministically on the static remix source (grain noise excluded by averaging): look ON vs OFF = +5.4R/+4.5G/+2.6B warm matte shift on the canvas region.
- Verified .cube upload end-to-end with a size-2 grayscale LUT: badge shows filename, canvas becomes grayscale (channel spread 3.3 ≈ tone-curve matte lift only).
- Verified mobile viewport 390×844: canvas 374×292, shutter visible, no horizontal overflow; VLM-confirmed clean layout and rendered aurora scene.
- Cleaned all test captures from the DB (fresh start); final lint clean; dev.log error-free.

Stage Summary:
- Browser-verified: rendering, all four creative modes, all capture paths, LUT pipeline (procedural + user .cube), gallery/remix/delete, mobile layout. Two critical bugs found and fixed during verification (sampler indexing, context-loss on remount).

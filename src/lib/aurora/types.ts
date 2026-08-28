/**
 * AuroraCam — shared contracts.
 *
 * A faithful web port of the AuroraCam creative-camera architecture:
 * GPU-only preview pipeline, live double exposure, motion echo, light
 * trails, and a "Signature Look" grade (tone curve → 33³ 3D LUT →
 * luminance-masked grain → vignette).
 *
 * This file is the integration contract between the render engine,
 * the React UI, and the capture API. Do not break these shapes.
 */

export type FormatKey = '4:3' | '1:1' | '65:24'
export type CreativeMode = 'normal' | 'dx' | 'echo' | 'trails'
export type CaptureKind = 'single' | 'first' | 'second' | 'composite'

/** Blend modes — indices are pinned by the GLSL `uMode` uniform. */
export const BLEND_MODES = ['Normal', 'Screen', 'Lighten', 'Add', 'Multiply', 'Overlay'] as const
export type BlendModeName = (typeof BLEND_MODES)[number]

export const CREATIVE_MODE_LABELS: Record<CreativeMode, string> = {
  normal: 'Standard',
  dx: 'Double Exposure',
  echo: 'Motion Echo',
  trails: 'Light Trails',
}

export interface FormatSpec {
  key: FormatKey
  label: string
  /** Final capture resolution (matches the device ground-truth size list). */
  captureW: number
  captureH: number
  /** Preview / processing buffer resolution (GPU FBO size). */
  previewW: number
  previewH: number
}

/**
 * Capture ceilings — mirrors the probed device list:
 * 4:3 → 3200x2400, 1:1 → 2448x2448, 65:24 (XPAN) → 3250x1200.
 * There is no 4000x3000; never request sizes outside this table.
 */
export const FORMATS: Record<FormatKey, FormatSpec> = {
  '4:3': { key: '4:3', label: '4:3', captureW: 3200, captureH: 2400, previewW: 1280, previewH: 960 },
  '1:1': { key: '1:1', label: '1:1', captureW: 2448, captureH: 2448, previewW: 960, previewH: 960 },
  '65:24': { key: '65:24', label: 'XPAN', captureW: 3250, captureH: 1200, previewW: 1300, previewH: 480 },
}

export interface DxParams {
  /** Index into BLEND_MODES — pinned to the shader's uMode. */
  blendMode: number
  /** 0..1 — Final = mix(live, blended, opacity). */
  opacity: number
  /** Mirror the first exposure horizontally. */
  flipFirst: boolean
}

export interface EchoParams {
  /** Number of ghost echoes, 1..5. */
  count: number
  /** Frame spacing between echoes, 2..10. */
  delay: number
  /** Falloff per echo, 0.05..0.95. */
  fade: number
}

export interface TrailsParams {
  /** Bright-pass luma threshold, 0..1. */
  threshold: number
  /** Per-frame decay multiplier, 0.80..0.995. */
  decay: number
  /** false = Lighten-accumulate (safe), true = Add (can clip). */
  addMode: boolean
}

export interface LookParams {
  enabled: boolean
  /** 0..1 — final = mix(clean, graded, intensity). */
  intensity: number
  /** Grain amplitude, 0..0.3. */
  grain: number
  /** Vignette strength, 0..1. */
  vignette: number
}

export interface AuroraParams {
  format: FormatKey
  mode: CreativeMode
  /** Mirror preview horizontally (front camera). */
  mirror: boolean
  dx: DxParams
  echo: EchoParams
  trails: TrailsParams
  look: LookParams
}

export const DEFAULT_PARAMS: AuroraParams = {
  format: '4:3',
  mode: 'normal',
  mirror: false,
  dx: { blendMode: 1, opacity: 0.6, flipFirst: false }, // Screen
  echo: { count: 3, delay: 4, fade: 0.55 },
  trails: { threshold: 0.55, decay: 0.94, addMode: false },
  look: { enabled: true, intensity: 0.85, grain: 0.12, vignette: 0.35 },
}

/** Periodic engine telemetry for the viewfinder HUD. */
export interface EngineStatus {
  fps: number
  sourceW: number
  sourceH: number
  outW: number
  outH: number
  firstCaptured: boolean
}

/** One rendered image produced at shutter press (JPEG data URLs). */
export interface CaptureImage {
  kind: CaptureKind
  dataUrl: string
  /** Small JPEG thumbnail for the gallery grid. */
  thumb: string
  width: number
  height: number
}

/** Everything a shutter press produces, ready to POST. */
export interface CaptureBundle {
  groupId: string
  format: FormatKey
  mode: CreativeMode
  blendMode: number
  opacity: number
  lookIntensity: number
  lutName: string
  images: CaptureImage[]
}

/** POST /api/captures body. */
export interface CapturePostDTO {
  groupId: string
  kind: CaptureKind
  format: string
  mode: string
  blendMode: number
  opacity: number
  lookIntensity: number
  lutName: string
  width: number
  height: number
  data: string
  thumb: string
}

/** GET /api/captures item (list view; `data` only included when `full=1`). */
export interface CaptureMeta {
  id: string
  groupId: string
  kind: CaptureKind
  format: string
  mode: string
  blendMode: number
  opacity: number
  lookIntensity: number
  lutName: string
  width: number
  height: number
  createdAt: string
  thumb: string
  hasPair?: boolean
  data?: string
}

/** Payload handed to the remix editor. */
export interface RemixPayload {
  groupId: string
  first: string
  second: string
  format: string
  blendMode: number
  opacity: number
}

export const isFormatKey = (v: string): v is FormatKey => v === '4:3' || v === '1:1' || v === '65:24'
export const isCreativeMode = (v: string): v is CreativeMode =>
  v === 'normal' || v === 'dx' || v === 'echo' || v === 'trails'
export const isCaptureKind = (v: string): v is CaptureKind =>
  v === 'single' || v === 'first' || v === 'second' || v === 'composite'

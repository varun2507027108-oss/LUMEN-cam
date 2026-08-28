'use client'

/**
 * Shared viewfinder control panels: format pills, mode pills, Signature Look,
 * and per-mode creative controls (double exposure / echo / light trails).
 * Used by both the camera screen and the remix editor.
 */

import { Layers, RotateCcw, TriangleAlert, Waves, Zap } from 'lucide-react'
import { Camera } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Slider } from '@/components/ui/slider'
import { Switch } from '@/components/ui/switch'
import {
  BLEND_MODES,
  CREATIVE_MODE_LABELS,
  FORMATS,
  type CreativeMode,
  type DxParams,
  type EchoParams,
  type FormatKey,
  type LookParams,
  type TrailsParams,
} from '@/lib/aurora/types'

export function SliderRow({
  label,
  value,
  min,
  max,
  step,
  onChange,
  format,
}: {
  label: string
  value: number
  min: number
  max: number
  step: number
  onChange: (v: number) => void
  format?: (v: number) => string
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-[11px]">
        <span className="text-neutral-400">{label}</span>
        <span className="font-mono text-neutral-300">{format ? format(value) : String(value)}</span>
      </div>
      <Slider
        value={[value]}
        min={min}
        max={max}
        step={step}
        onValueChange={(v: number[]) => onChange(v[0] ?? value)}
        aria-label={label}
        className="py-1"
      />
    </div>
  )
}

export function FormatPills({
  value,
  onChange,
}: {
  value: FormatKey
  onChange: (f: FormatKey) => void
}) {
  return (
    <div
      className="flex items-center gap-1 rounded-full bg-black/50 p-1 ring-1 ring-white/10"
      role="group"
      aria-label="Capture format"
    >
      {(Object.keys(FORMATS) as FormatKey[]).map((key) => (
        <button
          key={key}
          type="button"
          onClick={() => onChange(key)}
          aria-pressed={value === key}
          className={`rounded-full px-3 py-1 text-[11px] font-medium tracking-wide transition-colors ${
            value === key
              ? 'bg-amber-500 text-black'
              : key === '65:24'
                ? 'text-orange-300/80 hover:bg-white/10'
                : 'text-neutral-400 hover:bg-white/10'
          }`}
        >
          {FORMATS[key].label}
        </button>
      ))}
    </div>
  )
}

const MODE_ICONS: Record<CreativeMode, typeof Camera> = {
  normal: Camera,
  dx: Layers,
  echo: Waves,
  trails: Zap,
}

export function ModePills({
  value,
  onChange,
}: {
  value: CreativeMode
  onChange: (m: CreativeMode) => void
}) {
  return (
    <div
      className="aurora-scroll flex items-center gap-1 overflow-x-auto rounded-full bg-black/50 p-1 ring-1 ring-white/10"
      role="group"
      aria-label="Creative mode"
    >
      {(Object.keys(CREATIVE_MODE_LABELS) as CreativeMode[]).map((mode) => {
        const Icon = MODE_ICONS[mode]
        return (
          <button
            key={mode}
            type="button"
            onClick={() => onChange(mode)}
            aria-pressed={value === mode}
            title={CREATIVE_MODE_LABELS[mode]}
            className={`flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-[11px] font-medium transition-colors ${
              value === mode ? 'bg-amber-500 text-black' : 'text-neutral-400 hover:bg-white/10'
            }`}
          >
            <Icon className="h-3.5 w-3.5" aria-hidden />
            <span className="hidden sm:inline">{CREATIVE_MODE_LABELS[mode]}</span>
          </button>
        )
      })}
    </div>
  )
}

export function LookPanel({
  look,
  onChange,
  lutName,
  onUseProcedural,
  onUploadCube,
}: {
  look: LookParams
  onChange: (l: LookParams) => void
  lutName: string
  onUseProcedural: () => void
  onUploadCube: (file: File) => void
}) {
  return (
    <section className="space-y-2 rounded-xl bg-neutral-900/70 p-3 ring-1 ring-white/10" aria-label="Signature Look controls">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold tracking-wide text-neutral-200">Signature Look</h3>
        <div className="flex items-center gap-2">
          <span className="text-[10px] uppercase tracking-wider text-neutral-500">
            {look.enabled ? 'on' : 'off'}
          </span>
          <Switch checked={look.enabled} onCheckedChange={(v) => onChange({ ...look, enabled: v })} aria-label="Toggle Signature Look" />
        </div>
      </div>
      <SliderRow
        label="Intensity"
        value={look.intensity}
        min={0}
        max={1}
        step={0.01}
        onChange={(v) => onChange({ ...look, intensity: v })}
        format={(v) => `${Math.round(v * 100)}%`}
      />
      <SliderRow
        label="Grain"
        value={look.grain}
        min={0}
        max={0.3}
        step={0.005}
        onChange={(v) => onChange({ ...look, grain: v })}
        format={(v) => `${Math.round((v / 0.3) * 100)}%`}
      />
      <SliderRow
        label="Vignette"
        value={look.vignette}
        min={0}
        max={1}
        step={0.01}
        onChange={(v) => onChange({ ...look, vignette: v })}
        format={(v) => `${Math.round(v * 100)}%`}
      />
      <div className="flex flex-wrap items-center gap-2 pt-1">
        <span className="text-[11px] text-neutral-400">LUT</span>
        <span className="max-w-[170px] truncate rounded-md bg-black/40 px-2 py-0.5 font-mono text-[10px] text-amber-300/90">
          {lutName}
        </span>
        <Button variant="outline" size="sm" className="h-7 border-white/10 bg-transparent px-2 text-[10px] text-neutral-300 hover:bg-white/10" onClick={onUseProcedural}>
          Reset
        </Button>
        <label className="inline-flex h-7 cursor-pointer items-center rounded-md border border-white/10 bg-transparent px-2 text-[10px] text-neutral-300 transition-colors hover:bg-white/10">
          Upload .cube
          <input
            type="file"
            accept=".cube,text/plain"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) onUploadCube(file)
              e.target.value = ''
            }}
          />
        </label>
        <span className="font-mono text-[9px] text-neutral-600">33³ · sampler3D</span>
      </div>
    </section>
  )
}

export function DxPanel({ dx, onChange }: { dx: DxParams; onChange: (d: DxParams) => void }) {
  return (
    <section className="space-y-2 rounded-xl bg-neutral-900/70 p-3 ring-1 ring-white/10" aria-label="Double exposure controls">
      <h3 className="text-xs font-semibold tracking-wide text-neutral-200">Double Exposure</h3>
      <div className="space-y-1.5">
        <span className="text-[11px] text-neutral-400">Blend mode</span>
        <Select value={String(dx.blendMode)} onValueChange={(v) => onChange({ ...dx, blendMode: Number(v) })}>
          <SelectTrigger className="h-8 w-full border-white/10 bg-black/40 text-xs" aria-label="Blend mode">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {BLEND_MODES.map((name, i) => (
              <SelectItem key={name} value={String(i)} className="text-xs">
                {name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <SliderRow
        label="Opacity"
        value={dx.opacity}
        min={0}
        max={1}
        step={0.01}
        onChange={(v) => onChange({ ...dx, opacity: v })}
        format={(v) => `${Math.round(v * 100)}%`}
      />
    </section>
  )
}

export function EchoPanel({ echo, onChange }: { echo: EchoParams; onChange: (e: EchoParams) => void }) {
  return (
    <section className="space-y-2 rounded-xl bg-neutral-900/70 p-3 ring-1 ring-white/10" aria-label="Motion echo controls">
      <h3 className="text-xs font-semibold tracking-wide text-neutral-200">Motion Echo</h3>
      <SliderRow
        label="Echo count"
        value={echo.count}
        min={1}
        max={5}
        step={1}
        onChange={(v) => onChange({ ...echo, count: Math.round(v) })}
      />
      <SliderRow
        label="Delay (frames)"
        value={echo.delay}
        min={2}
        max={10}
        step={1}
        onChange={(v) => onChange({ ...echo, delay: Math.round(v) })}
      />
      <SliderRow
        label="Fade"
        value={echo.fade}
        min={0.05}
        max={0.95}
        step={0.01}
        onChange={(v) => onChange({ ...echo, fade: v })}
        format={(v) => v.toFixed(2)}
      />
    </section>
  )
}

export function TrailsPanel({
  trails,
  onChange,
  onClear,
}: {
  trails: TrailsParams
  onChange: (t: TrailsParams) => void
  onClear: () => void
}) {
  return (
    <section className="space-y-2 rounded-xl bg-neutral-900/70 p-3 ring-1 ring-white/10" aria-label="Light trails controls">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold tracking-wide text-neutral-200">Light Trails</h3>
        <Button
          variant="outline"
          size="sm"
          className="h-7 border-white/10 bg-transparent px-2 text-[10px] text-neutral-300 hover:bg-white/10"
          onClick={onClear}
        >
          <RotateCcw className="mr-1 h-3 w-3" aria-hidden />
          Clear
        </Button>
      </div>
      <SliderRow
        label="Threshold"
        value={trails.threshold}
        min={0}
        max={1}
        step={0.01}
        onChange={(v) => onChange({ ...trails, threshold: v })}
        format={(v) => v.toFixed(2)}
      />
      <SliderRow
        label="Decay"
        value={trails.decay}
        min={0.8}
        max={0.995}
        step={0.005}
        onChange={(v) => onChange({ ...trails, decay: v })}
        format={(v) => v.toFixed(3)}
      />
      <div className="space-y-1.5">
        <span className="text-[11px] text-neutral-400">Accumulation</span>
        <div className="flex gap-1" role="group" aria-label="Accumulation mode">
          <button
            type="button"
            onClick={() => onChange({ ...trails, addMode: false })}
            aria-pressed={!trails.addMode}
            className={`flex-1 rounded-md px-2 py-1.5 text-[11px] transition-colors ${
              !trails.addMode ? 'bg-amber-500 font-medium text-black' : 'bg-black/40 text-neutral-400 hover:bg-white/10'
            }`}
          >
            Lighten · safe
          </button>
          <button
            type="button"
            onClick={() => onChange({ ...trails, addMode: true })}
            aria-pressed={trails.addMode}
            className={`flex-1 rounded-md px-2 py-1.5 text-[11px] transition-colors ${
              trails.addMode ? 'bg-amber-500 font-medium text-black' : 'bg-black/40 text-neutral-400 hover:bg-white/10'
            }`}
          >
            Add · clips
          </button>
        </div>
      </div>
      {trails.addMode && (
        <p className="flex items-start gap-1.5 rounded-md bg-amber-500/10 px-2 py-1.5 text-[10px] leading-snug text-amber-300">
          <TriangleAlert className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
          Add mode accumulates toward white — watch the highlights.
        </p>
      )}
    </section>
  )
}

/**
 * AuroraCam — LUT construction & .cube parsing.
 *
 * 3D LUTs are RGBA8888 in `.cube` line order: red index changes fastest,
 * then green, then blue — flat index = r + g*size + b*size*size.
 */

export function proceduralLutName(): string {
  return 'Aurora Warm (procedural)'
}

const clamp01 = (v: number): number => (v < 0 ? 0 : v > 1 ? 1 : v)

/** smoothstep(0, 1, x) — cubic ease with zero slope at both ends. */
const smoothstep = (x: number): number => x * x * (3 - 2 * x)

/**
 * Default "Signature Look" LUT ("Aurora Warm"): gentle S-curve, warm
 * midtones, slightly desaturated greens. Pure function of the grid point.
 */
export function makeProceduralLut(size = 33): Uint8Array {
  const data = new Uint8Array(size * size * size * 4)
  const step = 1 / (size - 1)
  let i = 0
  for (let b = 0; b < size; b++) {
    for (let g = 0; g < size; g++) {
      for (let r = 0; r < size; r++) {
        const r0 = r * step
        const g0 = g * step
        const b0 = b * step
        const l = 0.2126 * r0 + 0.7152 * g0 + 0.0722 * b0 // luma, pre-curve
        // 1) gentle S-curve per channel
        let cr = r0 * 0.65 + smoothstep(r0) * 0.35
        let cg = g0 * 0.65 + smoothstep(g0) * 0.35
        let cb = b0 * 0.65 + smoothstep(b0) * 0.35
        // 2) warm the midtones (bell peaking at l = 0.5)
        const mid = 1 - Math.abs(2 * l - 1)
        cr += 0.035 * mid
        cg += 0.015 * mid
        cb -= 0.025 * mid
        // 3) slightly desaturate greens
        const greenness = Math.max(0, cg - Math.max(cr, cb))
        cg -= greenness * 0.22
        // 4) clamp, quantize, store (alpha = 255)
        data[i] = Math.round(clamp01(cr) * 255)
        data[i + 1] = Math.round(clamp01(cg) * 255)
        data[i + 2] = Math.round(clamp01(cb) * 255)
        data[i + 3] = 255
        i += 4
      }
    }
  }
  return data
}

export interface ParsedCube {
  size: number
  data: Uint8Array
  domainMin: [number, number, number]
  domainMax: [number, number, number]
}

/**
 * Parse IRIDAS/Adobe `.cube` text (3D only). Data rows are read in file
 * order (red fastest), which already matches our flat RGBA layout, so row
 * index k maps directly to byte offset k*4.
 */
export function parseCube(text: string): ParsedCube {
  let size = 0
  const domainMin: [number, number, number] = [0, 0, 0]
  const domainMax: [number, number, number] = [1, 1, 1]
  const rows: number[] = [] // flat r,g,b triples in file order
  let rowCount = 0

  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith('#')) continue
    const parts = line.split(/\s+/)
    const kw = parts[0].toUpperCase()
    if (kw === 'TITLE' || kw === 'COMMENT') continue // rest of line ignored
    if (kw === 'LUT_1D_SIZE') throw new Error('Only 3D LUTs are supported')
    if (kw === 'LUT_3D_SIZE') {
      const n = Number(parts[1])
      if (!Number.isInteger(n) || n < 2 || n > 64) throw new Error('LUT_3D_SIZE must be an integer 2-64')
      if (size !== 0 && size !== n) throw new Error('Conflicting LUT_3D_SIZE values')
      size = n
      continue
    }
    if (kw === 'DOMAIN_MIN' || kw === 'DOMAIN_MAX') {
      const dst = kw === 'DOMAIN_MIN' ? domainMin : domainMax
      for (let c = 0; c < 3; c++) {
        const v = Number(parts[1 + c])
        if (!Number.isFinite(v)) throw new Error(`${kw} needs 3 numbers`)
        dst[c] = v
      }
      continue
    }
    // Data row: 3+ columns of floats (extras ignored); values may exceed [0,1].
    if (parts.length < 3) throw new Error(`Malformed LUT row ${rowCount + 1}`)
    for (let c = 0; c < 3; c++) {
      const v = Number(parts[c])
      if (!Number.isFinite(v)) throw new Error(`Malformed LUT row ${rowCount + 1}`)
      rows.push(v)
    }
    rowCount++
  }

  if (size === 0) throw new Error('Missing LUT_3D_SIZE')
  const expected = size * size * size
  if (rowCount !== expected) throw new Error(`LUT row mismatch: expected ${expected}, found ${rowCount}`)
  for (let c = 0; c < 3; c++) {
    if (domainMin[c] >= domainMax[c]) throw new Error('DOMAIN_MIN must be below DOMAIN_MAX')
  }

  const data = new Uint8Array(expected * 4)
  for (let i = 0; i < expected; i++) {
    data[i * 4] = Math.round(clamp01(rows[i * 3]) * 255)
    data[i * 4 + 1] = Math.round(clamp01(rows[i * 3 + 1]) * 255)
    data[i * 4 + 2] = Math.round(clamp01(rows[i * 3 + 2]) * 255)
    data[i * 4 + 3] = 255
  }
  return { size, data, domainMin, domainMax }
}

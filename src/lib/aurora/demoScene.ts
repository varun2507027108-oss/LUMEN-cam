/**
 * AuroraCam — Demo Mode fallback "camera": an animated 1600x1200 night scene
 * (aurora ribbons, twinkling stars, drifting bokeh, car lights on a dark
 * silhouette) with moving bright lights for Light Trails / Motion Echo and
 * a dark base for Add blend. Deterministic: a fixed-seed mulberry32 RNG is
 * used only at construction, so render(t) is pure in t; static layers and
 * sprites are baked offscreen once and blitted each frame.
 */

const W = 1600
const H = 1200
const GROUND_Y = 940 // top of the pre-rendered ground strip
const GROUND_H = H - GROUND_Y

interface Star { x: number; y: number; s: number; base: number; speed: number; phase: number }
interface Bokeh { x0: number; y0: number; r: number; vx: number; bob: number; bobW: number; phase: number; alpha: number }
interface Ribbon { grad: CanvasGradient; baseY: number; a1: number; f1: number; a2: number; f2: number; speed: number; phase: number; width: number; gain: number }
interface Car { y: number; dir: number; speed: number; x0: number; gap: number; size: number }

/** mulberry32 — small deterministic seeded PRNG (construction-time only). */
const mulberry32 = (seed: number): (() => number) => {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Fresh offscreen canvas + 2D context. */
const makeLayer = (w: number, h: number): { c: HTMLCanvasElement; x: CanvasRenderingContext2D } => {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  return { c, x: c.getContext('2d')! }
}

/** Offscreen square sprite filled with one radial gradient (radius r). */
const radialSprite = (size: number, stops: [number, string][], r = size / 2): HTMLCanvasElement => {
  const { c, x } = makeLayer(size, size)
  const g = x.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, r)
  for (const [o, col] of stops) g.addColorStop(o, col)
  x.fillStyle = g
  x.fillRect(0, 0, size, size)
  return c
}

/** Soft radial glow painted straight into a layer while baking it. */
const paintGlow = (x: CanvasRenderingContext2D, cx: number, cy: number, r: number, inner: string): void => {
  const g = x.createRadialGradient(cx, cy, 0, cx, cy, r)
  g.addColorStop(0, inner)
  g.addColorStop(1, 'rgba(0,0,0,0)')
  x.fillStyle = g
  x.fillRect(cx - r, cy - r, r * 2, r * 2)
}

/** One tiny light: glow sprite plus an optional hotter core. */
const paintLight = (x: CanvasRenderingContext2D, img: HTMLCanvasElement, lx: number, ly: number, r: number, a: number, core?: string): void => {
  x.globalAlpha = a
  x.drawImage(img, lx - r, ly - r, r * 2, r * 2)
  if (core) {
    x.globalAlpha = Math.min(1, a + 0.2)
    x.fillStyle = core
    x.fillRect(lx - 1, ly - 1, 2, 2)
  }
}

/** Filled ridge silhouette: curve at fn(lx), solid down to the strip bottom. */
const fillRidge = (x: CanvasRenderingContext2D, fn: (lx: number) => number, fill: string): void => {
  x.beginPath()
  x.moveTo(0, fn(0))
  for (let lx = 16; lx <= W; lx += 16) x.lineTo(lx, fn(lx))
  x.lineTo(W, GROUND_H)
  x.lineTo(0, GROUND_H) // fill() auto-closes back up to the curve start
  x.fillStyle = fill
  x.fill()
}

/** Layered stroke widths/alphas that give the aurora ribbons soft edges. */
const RIBBON_PASSES: readonly (readonly [number, number])[] = [[1, 0.12], [0.62, 0.2], [0.3, 0.32]]

export class DemoScene {
  readonly canvas: HTMLCanvasElement // 1600x1200
  private readonly ctx: CanvasRenderingContext2D
  private readonly sky: HTMLCanvasElement
  private readonly ground: HTMLCanvasElement
  private readonly glowWarm: HTMLCanvasElement
  private readonly glowRed: HTMLCanvasElement
  private readonly bokehImg: HTMLCanvasElement
  private readonly starSprites: HTMLCanvasElement[]
  private readonly stars: Star[] = []
  private readonly bokeh: Bokeh[] = []
  private readonly ribbons: Ribbon[] = []
  private readonly cars: Car[] = []
  private disposed = false

  constructor() {
    this.canvas = document.createElement('canvas')
    this.canvas.width = W
    this.canvas.height = H
    this.ctx = this.canvas.getContext('2d', { alpha: false })!
    Object.assign(this.ctx, { lineCap: 'round', lineJoin: 'round' }) // soft ribbon strokes
    const rnd = mulberry32(0x41555230) // fixed seed — the scene is reproducible
    const rr = (a: number, b: number): number => a + (b - a) * rnd()

    // --- sprites, pre-rendered once and reused every frame ---
    this.glowWarm = radialSprite(32, [[0, 'rgba(255,225,175,1)'], [0.3, 'rgba(255,190,120,0.5)'], [1, 'rgba(255,179,92,0)']])
    this.glowRed = radialSprite(32, [[0, 'rgba(255,130,110,0.95)'], [0.35, 'rgba(255,80,70,0.4)'], [1, 'rgba(255,60,60,0)']])
    this.bokehImg = radialSprite(64, [[0, 'rgba(255,217,160,0.7)'], [0.28, 'rgba(255,190,110,0.38)'], [0.55, 'rgba(255,179,92,0.5)'], [0.8, 'rgba(255,160,80,0.2)'], [1, 'rgba(255,160,80,0)']])
    this.starSprites = [2.2, 3.8, 5.4, 7].map((r) => radialSprite(16, [[0, 'rgba(255,248,235,1)'], [0.5, 'rgba(255,244,220,0.55)'], [1, 'rgba(255,244,220,0)']], r))

    // --- static sky layer: near-black gradient, hazy moon, horizon glows ---
    const sky = makeLayer(W, H)
    const sg = sky.x.createLinearGradient(0, 0, 0, H)
    for (const [o, col] of [[0, '#05080c'], [0.4, '#070d13'], [0.66, '#091419'], [0.8, '#0c1e21'], [0.9, '#08141a'], [1, '#060e11']] as const) sg.addColorStop(o, col)
    sky.x.fillStyle = sg
    sky.x.fillRect(0, 0, W, H)
    paintGlow(sky.x, W * 0.78, 205, 300, 'rgba(255,244,220,0.08)') // moon halo
    paintGlow(sky.x, W * 0.78, 205, 36, 'rgba(255,250,240,0.9)') // hazy moon disc
    paintGlow(sky.x, W * 0.5, 1015, W * 0.65, 'rgba(45,212,167,0.09)') // teal at the horizon
    paintGlow(sky.x, W * 0.22, 1010, 330, 'rgba(255,179,92,0.05)') // distant warm town
    this.sky = sky.c

    // --- static ground strip: two ridges, teal mist, scattered warm lights ---
    const ground = makeLayer(W, GROUND_H)
    const nearY = (lx: number): number => 100 + 20 * Math.sin(lx * 0.004 + 0.7) + 8 * Math.sin(lx * 0.011 + 2.1) + 4 * Math.sin(lx * 0.023 + 4.4)
    const farY = (lx: number): number => 70 + 18 * Math.sin(lx * 0.003 + 1.9) + 7 * Math.sin(lx * 0.009 + 5.2)
    fillRidge(ground.x, farY, '#0a1412')
    for (let i = 0; i < 7; i++) { // dim lights on the far ridge (occluded by the near one)
      const lx = rr(24, W - 24)
      paintLight(ground.x, this.glowWarm, lx, farY(lx) + rr(3, 9), rr(3, 6), rr(0.25, 0.6))
    }
    paintGlow(ground.x, W * 0.5, 85, W * 0.55, 'rgba(45,212,167,0.05)') // mist over the far ridge
    fillRidge(ground.x, nearY, '#05070a')
    for (let i = 0; i < 19; i++) { // cabins / lamps on the near silhouette
      const lx = rr(24, W - 24)
      paintLight(ground.x, this.glowWarm, lx, nearY(lx) + rr(7, GROUND_H - nearY(lx) - 10), rr(4, 13), rr(0.25, 0.85), i % 3 === 0 ? '#ffd9a0' : '#ffb35c')
    }
    this.ground = ground.c

    // --- stars: positions fixed here; alpha twinkles per frame ---
    for (let i = 0; i < 110; i++) this.stars.push({
      x: rr(0, W), y: Math.pow(rnd(), 1.2) * (GROUND_Y - 40), s: Math.min(3, Math.floor(Math.pow(rnd(), 1.6) * 4)),
      base: rr(0.35, 1), speed: rr(0.4, 2.2), phase: rr(0, Math.PI * 2), // mostly small stars, denser up top
    })

    // --- aurora ribbons: sinusoidal curtains (teal, green, magenta) ---
    const ribbon = (rgb: string, baseY: number, width: number, gain: number, speed: number): void => {
      const g = this.ctx.createLinearGradient(0, baseY - 250, 0, baseY + 250)
      for (const [o, a] of [[0, 0], [0.35, 0.35], [0.55, 0.8], [0.8, 0.15], [1, 0]] as const) g.addColorStop(o, `rgba(${rgb},${a})`)
      this.ribbons.push({ grad: g, baseY, a1: rr(38, 62), f1: rr(0.0028, 0.004), a2: rr(18, 30), f2: rr(0.008, 0.012), speed, phase: rr(0, Math.PI * 2), width, gain })
    }
    ribbon('45,212,167', 330, 190, 1, 0.16)
    ribbon('74,222,128', 460, 150, 0.85, -0.11)
    ribbon('232,121,249', 195, 120, 0.5, 0.07)

    // --- foreground bokeh orbs + car light pairs ---
    for (let i = 0; i < 8; i++) this.bokeh.push({
      x0: rr(0, W), y0: rr(500, H - 70), r: rr(20, 70), vx: rr(10, 42) * (rnd() < 0.3 ? -1 : 1),
      bob: rr(6, 20), bobW: rr(0.15, 0.5), phase: rr(0, Math.PI * 2), alpha: rr(0.16, 0.42),
    })
    this.cars.push({ y: 1096, dir: 1, speed: 115, x0: rr(0, W), gap: 26, size: 1.15 })
    this.cars.push({ y: 1080, dir: -1, speed: 78, x0: rr(0, W), gap: 18, size: 0.8 })
  }

  /** Render one frame. t = seconds; the same t always yields the same frame. */
  render(t: number): void {
    if (this.disposed) return
    const ctx = this.ctx
    ctx.globalCompositeOperation = 'source-over' // 1) static night sky
    ctx.globalAlpha = 1
    ctx.drawImage(this.sky, 0, 0)
    // 2) twinkling stars: alpha = base * (0.55 + 0.45 sin(t*speed + phase))
    for (const st of this.stars) {
      ctx.globalAlpha = st.base * (0.55 + 0.45 * Math.sin(t * st.speed + st.phase))
      ctx.drawImage(this.starSprites[st.s], st.x - 8, st.y - 8)
    }
    // 3) aurora ribbons — layered translucent strokes, additive glow
    ctx.globalCompositeOperation = 'lighter'
    for (const rb of this.ribbons) {
      ctx.beginPath()
      for (let x = -80; x <= W + 80; x += 40) {
        const y = rb.baseY + rb.a1 * Math.sin(x * rb.f1 + t * rb.speed + rb.phase) + rb.a2 * Math.sin(x * rb.f2 - t * rb.speed * 0.7 + rb.phase * 1.7)
        if (x === -80) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      }
      for (const [wMul, a] of RIBBON_PASSES) {
        ctx.lineWidth = rb.width * wMul
        ctx.globalAlpha = a * rb.gain
        ctx.strokeStyle = rb.grad
        ctx.stroke()
      }
    }
    // 4) ground silhouette (with its baked warm lights)
    ctx.globalCompositeOperation = 'source-over'
    ctx.globalAlpha = 1
    ctx.drawImage(this.ground, 0, GROUND_Y)
    // 5) car light pairs crossing the ridge road, with wet-road reflections
    ctx.globalCompositeOperation = 'lighter'
    for (const c of this.cars) {
      const span = W + 320
      const cx = (((c.x0 + c.speed * c.dir * t) % span) + span) % span - 160, r = 15 * c.size
      for (const off of [-c.gap / 2, c.gap / 2]) { // bright headlight pair
        paintLight(ctx, this.glowWarm, cx + off, c.y, r, 0.9, '#fff3d6')
        ctx.globalAlpha = 0.25
        ctx.drawImage(this.glowWarm, cx + off - r * 0.8, c.y + 4, r * 1.6, r * 1.2)
      }
      const tx = cx - c.dir * c.gap * 1.9 // taillights trail the travel direction
      for (const off of [-c.gap / 2, c.gap / 2]) paintLight(ctx, this.glowRed, tx + off, c.y, r * 0.55, 0.55, '#ff6a55')
    }
    // 6) warm bokeh orbs drifting horizontally (wrap-around) with a vertical bob
    for (const o of this.bokeh) {
      const span = W + o.r * 2 + 40
      const cx = (((o.x0 + o.vx * t) % span) + span) % span - o.r - 20, y = o.y0 + o.bob * Math.sin(t * o.bobW + o.phase)
      ctx.globalAlpha = o.alpha * (0.85 + 0.15 * Math.sin(t * 0.4 + o.phase * 2.3))
      ctx.drawImage(this.bokehImg, cx - o.r, y - o.r, o.r * 2, o.r * 2)
    }
    ctx.globalCompositeOperation = 'source-over'
    ctx.globalAlpha = 1
  }

  /** Free the offscreen layers; render() becomes a no-op afterwards. */
  dispose(): void {
    if (this.disposed) return
    this.disposed = true
    for (const c of [this.sky, this.ground, this.glowWarm, this.glowRed, this.bokehImg, ...this.starSprites]) c.width = 0
    for (const arr of [this.stars, this.bokeh, this.ribbons, this.cars]) arr.length = 0
  }
}

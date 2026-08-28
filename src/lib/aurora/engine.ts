/**
 * AuroraEngine — the GPU-only render pipeline.
 *
 * Frames flow: camera/demo source → texture upload (GPU) → base pass
 * (aspect-crop + mirror) → creative pass (double exposure / motion echo /
 * light trails) → look pass (tone curve → 3D LUT → grain → vignette) →
 * screen. Zero per-frame CPU pixel work; readPixels happens only at
 * shutter press (one-shot), exactly like the project's hard rule #1/#3.
 */

import { DemoScene } from './demoScene'
import { makeProceduralLut, parseCube, proceduralLutName, type ParsedCube } from './lut'
import {
  DEFAULT_PARAMS,
  FORMATS,
  type AuroraParams,
  type CaptureBundle,
  type CaptureImage,
  type EngineStatus,
} from './types'
import {
  buildProgram,
  createFbo,
  createSourceTexture,
  disposeFbo,
  uploadImageTexture,
  type Fbo,
  type ProgramBundle,
} from './glUtils'
import {
  FS_BASE,
  FS_BLEND,
  FS_COPY,
  FS_ECHO,
  FS_LOOK,
  FS_TRAILS_ACCUM,
  FS_TRAILS_SHOW,
  VS_QUAD,
} from './shaders'

const MAX_ECHO = 5
const DEMO_W = 1600
const DEMO_H = 1200

interface Progs {
  base: ProgramBundle
  copy: ProgramBundle
  blend: ProgramBundle
  echo: ProgramBundle
  trailsAccum: ProgramBundle
  trailsShow: ProgramBundle
  look: ProgramBundle
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('Failed to load image'))
    img.src = url
  })
}

function canvasToJpeg(canvas: HTMLCanvasElement, quality: number): Promise<string> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('JPEG encoding failed'))
          return
        }
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result))
        reader.onerror = () => reject(new Error('JPEG encoding failed'))
        reader.readAsDataURL(blob)
      },
      'image/jpeg',
      quality,
    )
  })
}

export class AuroraEngine {
  private gl: WebGL2RenderingContext
  private canvas: HTMLCanvasElement
  private progs: Progs
  private vao: WebGLVertexArrayObject

  private running = false
  private paused = false
  private raf = 0
  private ro: ResizeObserver | null = null
  private quadBuf: WebGLBuffer | null = null
  private readonly onContextLost = (e: Event) => {
    e.preventDefault()
    this.running = false
    cancelAnimationFrame(this.raf)
    console.error('[AuroraCam] WebGL context lost')
  }

  private video: HTMLVideoElement | null = null
  private demo: DemoScene | null = null
  private srcTex: WebGLTexture
  private srcW = 0
  private srcH = 0
  private hasSourceFrame = false

  private remixA: WebGLTexture | null = null
  private remixB: WebGLTexture | null = null
  private remixActive = false
  private remixW = 0
  private remixH = 0

  private base!: Fbo
  private creative!: Fbo
  private firstExp!: Fbo
  private echo: Fbo[] = []
  private echoHead = 0
  private echoFilled = 0
  private frameCounter = 0
  private accum: Fbo[] = []
  private accumIdx = 0
  private captureA: Fbo | null = null
  private captureB: Fbo | null = null

  private lutTex: WebGLTexture
  private lutSize = 33
  private lutDomainMin: [number, number, number] = [0, 0, 0]
  private lutDomainMax: [number, number, number] = [1, 1, 1]
  lutName = proceduralLutName()

  private params: AuroraParams
  private firstCaptured = false
  private frameTimes: number[] = []
  private fps = 0
  private lastStatusEmit = 0
  private statusCb: ((s: EngineStatus) => void) | null = null

  private fullCanvas: HTMLCanvasElement
  private thumbCanvas: HTMLCanvasElement

  constructor(canvas: HTMLCanvasElement, params: AuroraParams = DEFAULT_PARAMS) {
    this.canvas = canvas
    const gl = canvas.getContext('webgl2', {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      preserveDrawingBuffer: false,
      powerPreference: 'high-performance',
    })
    if (!gl) throw new Error('WebGL2 is not available in this browser.')
    this.gl = gl
    this.params = params

    canvas.addEventListener('webglcontextlost', this.onContextLost)

    // Fullscreen quad.
    this.vao = gl.createVertexArray()!
    gl.bindVertexArray(this.vao)
    this.quadBuf = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuf)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW)
    gl.enableVertexAttribArray(0)
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0)
    gl.bindVertexArray(null)

    this.progs = {
      base: buildProgram(gl, VS_QUAD, FS_BASE, ['uSrc', 'uSrcSize', 'uDstSize', 'uMirror']),
      copy: buildProgram(gl, VS_QUAD, FS_COPY, ['uSrc']),
      blend: buildProgram(gl, VS_QUAD, FS_BLEND, ['uA', 'uB', 'uMode', 'uOpacity', 'uFlipA']),
      echo: buildProgram(gl, VS_QUAD, FS_ECHO, [
        'uCurrent',
        'uEcho0',
        'uEcho1',
        'uEcho2',
        'uEcho3',
        'uEcho4',
        'uWeights[0]',
        'uNorm',
      ]),
      trailsAccum: buildProgram(gl, VS_QUAD, FS_TRAILS_ACCUM, ['uBase', 'uPrev', 'uThreshold', 'uDecay', 'uAddMode']),
      trailsShow: buildProgram(gl, VS_QUAD, FS_TRAILS_SHOW, ['uBase', 'uAccum']),
      look: buildProgram(gl, VS_QUAD, FS_LOOK, [
        'uSrc',
        'uLut',
        'uLutScale',
        'uLutOffset',
        'uDomainMin',
        'uDomainMax',
        'uIntensity',
        'uGrain',
        'uVignette',
        'uTime',
        'uGrainScale',
      ]),
    }

    this.srcTex = createSourceTexture(gl)
    this.lutTex = gl.createTexture()!

    this.fullCanvas = document.createElement('canvas')
    this.thumbCanvas = document.createElement('canvas')

    this.rebuildFormatFbos()
    this.useProceduralLut()

    this.ro = new ResizeObserver(() => this.syncCanvasSize())
    this.ro.observe(canvas)
    this.syncCanvasSize()
  }

  // ------------------------------------------------------------------ lifecycle

  attachVideo(video: HTMLVideoElement): void {
    this.video = video
    this.demo?.dispose()
    this.demo = null
    this.remixActive = false
  }

  startDemo(): void {
    this.video = null
    this.demo = new DemoScene()
    this.remixActive = false
    this.srcW = DEMO_W
    this.srcH = DEMO_H
  }

  async loadRemixPair(firstUrl: string, secondUrl: string): Promise<void> {
    const [imgA, imgB] = await Promise.all([loadImage(firstUrl), loadImage(secondUrl)])
    const gl = this.gl
    if (this.remixA) gl.deleteTexture(this.remixA)
    if (this.remixB) gl.deleteTexture(this.remixB)
    const a = uploadImageTexture(gl, imgA)
    const b = uploadImageTexture(gl, imgB)
    this.remixA = a.tex
    this.remixB = b.tex
    this.remixW = b.w
    this.remixH = b.h
    this.video = null
    this.demo?.dispose()
    this.demo = null
    this.remixActive = true
    this.firstCaptured = true
  }

  updateParams(p: AuroraParams): void {
    const formatChanged = p.format !== this.params.format
    this.params = p
    if (formatChanged) this.rebuildFormatFbos()
  }

  onStatus(cb: (s: EngineStatus) => void): void {
    this.statusCb = cb
  }

  setPaused(paused: boolean): void {
    this.paused = paused
  }

  start(): void {
    if (this.running) return
    this.running = true
    this.raf = requestAnimationFrame(this.loop)
  }

  stop(): void {
    this.running = false
    cancelAnimationFrame(this.raf)
  }

  dispose(): void {
    this.stop()
    this.ro?.disconnect()
    this.ro = null
    this.canvas.removeEventListener('webglcontextlost', this.onContextLost)
    this.demo?.dispose()
    this.demo = null
    const gl = this.gl
    disposeFbo(gl, this.base)
    disposeFbo(gl, this.creative)
    disposeFbo(gl, this.firstExp)
    for (const f of this.echo) disposeFbo(gl, f)
    for (const f of this.accum) disposeFbo(gl, f)
    disposeFbo(gl, this.captureA)
    disposeFbo(gl, this.captureB)
    this.echo = []
    this.accum = []
    this.captureA = null
    this.captureB = null
    gl.deleteTexture(this.srcTex)
    gl.deleteTexture(this.lutTex)
    if (this.remixA) gl.deleteTexture(this.remixA)
    if (this.remixB) gl.deleteTexture(this.remixB)
    this.remixA = null
    this.remixB = null
    for (const key of Object.keys(this.progs) as (keyof Progs)[]) gl.deleteProgram(this.progs[key].program)
    gl.bindVertexArray(this.vao)
    gl.bindBuffer(gl.ARRAY_BUFFER, null)
    if (this.quadBuf) gl.deleteBuffer(this.quadBuf)
    this.quadBuf = null
    gl.bindVertexArray(null)
    gl.deleteVertexArray(this.vao)
    // NOTE: deliberately no WEBGL_lose_context.loseContext() here — the same
    // canvas element may be reused by a successor engine (source switch or
    // Fast Refresh), and a lost context can never be re-acquired via
    // getContext(). Explicit resource deletion above is sufficient cleanup.
  }

  // ------------------------------------------------------------------ LUTs

  useProceduralLut(): void {
    this.uploadLut(makeProceduralLut(33), 33, [0, 0, 0], [1, 1, 1])
    this.lutName = proceduralLutName()
  }

  setLutFromCube(name: string, text: string): { ok: true } | { ok: false; error: string } {
    let parsed: ParsedCube
    try {
      parsed = parseCube(text)
    } catch (e) {
      return { ok: false, error: e instanceof Error ? e.message : 'Failed to parse .cube file' }
    }
    this.uploadLut(parsed.data, parsed.size, parsed.domainMin, parsed.domainMax)
    this.lutName = name
    return { ok: true }
  }

  private uploadLut(
    data: Uint8Array,
    size: number,
    dmin: [number, number, number],
    dmax: [number, number, number],
  ): void {
    const gl = this.gl
    gl.bindTexture(gl.TEXTURE_3D, this.lutTex)
    gl.texImage3D(gl.TEXTURE_3D, 0, gl.RGBA8, size, size, size, 0, gl.RGBA, gl.UNSIGNED_BYTE, data)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE)
    this.lutSize = size
    this.lutDomainMin = dmin
    this.lutDomainMax = dmax
  }

  // ------------------------------------------------------------------ frame loop

  private loop = (ts: number) => {
    if (!this.running) return
    this.raf = requestAnimationFrame(this.loop)
    if (this.paused) return
    const t = ts / 1000
    try {
      this.renderFrame(t)
    } catch (err) {
      console.error('[AuroraCam] render error, stopping loop', err)
      this.stop()
      return
    }
    this.trackFps(ts)
    if (ts - this.lastStatusEmit > 500) {
      this.lastStatusEmit = ts
      this.emitStatus()
    }
  }

  private renderFrame(t: number): void {
    if (this.remixActive) {
      if (!this.remixA || !this.remixB) return
      this.srcW = this.remixW
      this.srcH = this.remixH
      this.runBase(this.base, false)
      this.runBlend(this.remixA, this.base.tex, this.creative, this.params.dx)
      this.present(this.creative.tex, t)
      return
    }

    if (this.video) {
      if (this.video.readyState < 2 || this.video.videoWidth === 0) return
      this.srcW = this.video.videoWidth
      this.srcH = this.video.videoHeight
    } else if (this.demo) {
      this.demo.render(t)
      this.srcW = DEMO_W
      this.srcH = DEMO_H
    } else {
      return
    }

    this.uploadSource()
    this.runBase(this.base, this.params.mirror)

    let current: Fbo
    switch (this.params.mode) {
      case 'dx':
        if (this.firstCaptured) {
          this.runBlend(this.firstExp.tex, this.base.tex, this.creative, this.params.dx)
          current = this.creative
        } else {
          current = this.base
        }
        break
      case 'echo': {
        this.runEcho(this.base.tex, this.creative)
        current = this.creative
        this.frameCounter++
        const delay = Math.max(1, Math.round(this.params.echo.delay))
        if (this.frameCounter % delay === 0) {
          this.runCopy(this.base.tex, this.echo[this.echoHead])
          this.echoHead = (this.echoHead + 1) % MAX_ECHO
          this.echoFilled = Math.min(this.echoFilled + 1, MAX_ECHO)
        }
        break
      }
      case 'trails': {
        const prev = this.accum[this.accumIdx]
        const next = this.accum[1 - this.accumIdx]
        this.runTrailsAccum(this.base.tex, prev.tex, next)
        this.accumIdx = 1 - this.accumIdx
        this.runTrailsShow(this.base.tex, next.tex, this.creative)
        current = this.creative
        break
      }
      default:
        current = this.base
    }

    this.present(current.tex, t)
  }

  private present(tex: WebGLTexture, t: number): void {
    if (this.params.look.enabled) {
      this.runLook(tex, null, t)
    } else {
      this.runCopy(tex, null)
    }
  }

  private uploadSource(): void {
    const gl = this.gl
    const source: HTMLVideoElement | HTMLCanvasElement = this.video ?? this.demo!.canvas
    gl.bindTexture(gl.TEXTURE_2D, this.srcTex)
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, source)
    this.hasSourceFrame = true
  }

  private get srcTexCurrent(): WebGLTexture {
    return this.remixActive && this.remixB ? this.remixB : this.srcTex
  }

  // ------------------------------------------------------------------ passes

  private begin(target: Fbo | null): void {
    const gl = this.gl
    if (target) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, target.fbo)
      gl.viewport(0, 0, target.w, target.h)
    } else {
      gl.bindFramebuffer(gl.FRAMEBUFFER, null)
      gl.viewport(0, 0, this.canvas.width, this.canvas.height)
    }
    gl.bindVertexArray(this.vao)
  }

  private drawQuad(): void {
    this.gl.drawArrays(this.gl.TRIANGLE_STRIP, 0, 4)
  }

  private runBase(target: Fbo, mirror: boolean): void {
    const gl = this.gl
    const P = this.progs.base
    this.begin(target)
    gl.useProgram(P.program)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, this.srcTexCurrent)
    gl.uniform1i(P.u['uSrc']!, 0)
    gl.uniform2f(P.u['uSrcSize']!, this.srcW || 1, this.srcH || 1)
    gl.uniform2f(P.u['uDstSize']!, target.w, target.h)
    gl.uniform1i(P.u['uMirror']!, mirror ? 1 : 0)
    this.drawQuad()
  }

  private runCopy(srcTex: WebGLTexture, target: Fbo | null): void {
    const gl = this.gl
    const P = this.progs.copy
    this.begin(target)
    gl.useProgram(P.program)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, srcTex)
    gl.uniform1i(P.u['uSrc']!, 0)
    this.drawQuad()
  }

  private runBlend(texA: WebGLTexture, texB: WebGLTexture, target: Fbo, dx: AuroraParams['dx']): void {
    const gl = this.gl
    const P = this.progs.blend
    this.begin(target)
    gl.useProgram(P.program)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, texA)
    gl.uniform1i(P.u['uA']!, 0)
    gl.activeTexture(gl.TEXTURE1)
    gl.bindTexture(gl.TEXTURE_2D, texB)
    gl.uniform1i(P.u['uB']!, 1)
    gl.uniform1i(P.u['uMode']!, Math.round(dx.blendMode))
    gl.uniform1f(P.u['uOpacity']!, dx.opacity)
    gl.uniform1i(P.u['uFlipA']!, dx.flipFirst ? 1 : 0)
    this.drawQuad()
  }

  private runEcho(currentTex: WebGLTexture, target: Fbo): void {
    const gl = this.gl
    const P = this.progs.echo
    const { count, fade } = this.params.echo
    const weights = new Float32Array(MAX_ECHO)
    const valid = Math.min(Math.max(1, Math.round(count)), MAX_ECHO)
    let norm = 1
    for (let i = 0; i < MAX_ECHO; i++) {
      const w = i < valid && i < this.echoFilled ? Math.pow(fade, i + 1) : 0
      weights[i] = w
      norm += w
    }
    this.begin(target)
    gl.useProgram(P.program)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, currentTex)
    gl.uniform1i(P.u['uCurrent']!, 0)
    for (let i = 0; i < MAX_ECHO; i++) {
      const slot = (((this.echoHead - 1 - i) % MAX_ECHO) + MAX_ECHO) % MAX_ECHO
      gl.activeTexture(gl.TEXTURE4 + i)
      gl.bindTexture(gl.TEXTURE_2D, this.echo[slot].tex)
      gl.uniform1i(P.u[`uEcho${i}`]!, 4 + i)
    }
    gl.uniform1fv(P.u['uWeights[0]']!, weights)
    gl.uniform1f(P.u['uNorm']!, norm)
    this.drawQuad()
  }

  private runTrailsAccum(baseTex: WebGLTexture, prevTex: WebGLTexture, target: Fbo): void {
    const gl = this.gl
    const P = this.progs.trailsAccum
    const { threshold, decay, addMode } = this.params.trails
    this.begin(target)
    gl.useProgram(P.program)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, baseTex)
    gl.uniform1i(P.u['uBase']!, 0)
    gl.activeTexture(gl.TEXTURE1)
    gl.bindTexture(gl.TEXTURE_2D, prevTex)
    gl.uniform1i(P.u['uPrev']!, 1)
    gl.uniform1f(P.u['uThreshold']!, threshold)
    gl.uniform1f(P.u['uDecay']!, decay)
    gl.uniform1i(P.u['uAddMode']!, addMode ? 1 : 0)
    this.drawQuad()
  }

  private runTrailsShow(baseTex: WebGLTexture, accumTex: WebGLTexture, target: Fbo): void {
    const gl = this.gl
    const P = this.progs.trailsShow
    this.begin(target)
    gl.useProgram(P.program)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, baseTex)
    gl.uniform1i(P.u['uBase']!, 0)
    gl.activeTexture(gl.TEXTURE1)
    gl.bindTexture(gl.TEXTURE_2D, accumTex)
    gl.uniform1i(P.u['uAccum']!, 1)
    this.drawQuad()
  }

  private runLook(srcTex: WebGLTexture, target: Fbo | null, t: number): void {
    const gl = this.gl
    const P = this.progs.look
    const { intensity, grain, vignette } = this.params.look
    const tw = target ? target.w : this.canvas.width
    const th = target ? target.h : this.canvas.height
    const aspect = tw / Math.max(th, 1)
    this.begin(target)
    gl.useProgram(P.program)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, srcTex)
    gl.uniform1i(P.u['uSrc']!, 0)
    gl.activeTexture(gl.TEXTURE9)
    gl.bindTexture(gl.TEXTURE_3D, this.lutTex)
    gl.uniform1i(P.u['uLut']!, 9)
    gl.uniform1f(P.u['uLutScale']!, (this.lutSize - 1) / this.lutSize)
    gl.uniform1f(P.u['uLutOffset']!, 0.5 / this.lutSize)
    gl.uniform3fv(P.u['uDomainMin']!, this.lutDomainMin)
    gl.uniform3fv(P.u['uDomainMax']!, this.lutDomainMax)
    gl.uniform1f(P.u['uIntensity']!, intensity)
    gl.uniform1f(P.u['uGrain']!, grain)
    gl.uniform1f(P.u['uVignette']!, vignette)
    gl.uniform1f(P.u['uTime']!, t % 3600)
    gl.uniform2f(P.u['uGrainScale']!, 900 * aspect, 900)
    this.drawQuad()
  }

  /** Look pass or plain copy, depending on whether the look is enabled. */
  private runGrade(srcTex: WebGLTexture, target: Fbo, t: number): void {
    if (this.params.look.enabled) this.runLook(srcTex, target, t)
    else this.runCopy(srcTex, target)
  }

  // ------------------------------------------------------------------ FBO management

  private rebuildFormatFbos(): void {
    const gl = this.gl
    const f = FORMATS[this.params.format]
    disposeFbo(gl, this.base)
    disposeFbo(gl, this.creative)
    disposeFbo(gl, this.firstExp)
    for (const e of this.echo) disposeFbo(gl, e)
    for (const a of this.accum) disposeFbo(gl, a)
    this.base = createFbo(gl, f.previewW, f.previewH)
    this.creative = createFbo(gl, f.previewW, f.previewH)
    this.firstExp = createFbo(gl, f.previewW, f.previewH)
    this.echo = Array.from({ length: MAX_ECHO }, () => createFbo(gl, f.previewW, f.previewH))
    this.accum = [createFbo(gl, f.previewW, f.previewH), createFbo(gl, f.previewW, f.previewH)]
    this.accumIdx = 0
    this.echoHead = 0
    this.echoFilled = 0
    this.frameCounter = 0
    this.firstCaptured = false
    this.clearAccum()
  }

  private clearAccum(): void {
    const gl = this.gl
    for (const a of this.accum) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, a.fbo)
      gl.clearColor(0, 0, 0, 1)
      gl.clear(gl.COLOR_BUFFER_BIT)
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, null)
  }

  clearTrails(): void {
    this.clearAccum()
  }

  private syncCanvasSize(): void {
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const w = Math.max(1, Math.round(this.canvas.clientWidth * dpr))
    const h = Math.max(1, Math.round(this.canvas.clientHeight * dpr))
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w
      this.canvas.height = h
    }
  }

  private ensureCaptureFbo(slot: 'a' | 'b', w: number, h: number): Fbo {
    const cur = slot === 'a' ? this.captureA : this.captureB
    if (cur && cur.w === w && cur.h === h) return cur
    if (cur) disposeFbo(this.gl, cur)
    const fbo = createFbo(this.gl, w, h)
    if (slot === 'a') this.captureA = fbo
    else this.captureB = fbo
    return fbo
  }

  // ------------------------------------------------------------------ user actions

  /** Double exposure stage 1: freeze the current clean frame as the first exposure. */
  captureFirst(): void {
    if (this.remixActive) return
    this.runCopy(this.base.tex, this.firstExp)
    this.firstCaptured = true
    this.emitStatus()
  }

  retakeFirst(): void {
    this.firstCaptured = false
    this.emitStatus()
  }

  private trackFps(ts: number): void {
    this.frameTimes.push(ts)
    const cutoff = ts - 2000
    while (this.frameTimes.length > 0 && this.frameTimes[0] < cutoff) this.frameTimes.shift()
    const span = this.frameTimes.length > 1 ? (this.frameTimes[this.frameTimes.length - 1] - this.frameTimes[0]) / 1000 : 0
    this.fps = span > 0 ? Math.round((this.frameTimes.length - 1) / span) : 0
  }

  private emitStatus(): void {
    this.statusCb?.({
      fps: this.fps,
      sourceW: this.srcW,
      sourceH: this.srcH,
      outW: this.base.w,
      outH: this.base.h,
      firstCaptured: this.firstCaptured,
    })
  }

  // ------------------------------------------------------------------ capture (one-shot CPU allowed here)

  async capture(): Promise<CaptureBundle> {
    if (this.remixActive && (!this.remixA || !this.remixB)) throw new Error('Remix exposures not loaded')
    if (!this.remixActive && !this.hasSourceFrame) throw new Error('Camera is still starting — try again in a moment')

    const fmt = FORMATS[this.params.format]
    const A = this.ensureCaptureFbo('a', fmt.captureW, fmt.captureH)
    const B = this.ensureCaptureFbo('b', fmt.captureW, fmt.captureH)
    const p = this.params
    const t = (performance.now() / 1000) % 3600
    const images: CaptureImage[] = []
    const dxActive = this.remixActive || (p.mode === 'dx' && this.firstCaptured)

    if (dxActive) {
      const firstTex = this.remixActive ? this.remixA! : this.firstExp.tex
      // First exposure.
      this.runCopy(firstTex, A)
      this.runGrade(A.tex, B, t)
      images.push(await this.fboToJpeg(B, 'first'))
      // Second exposure (live / stored second).
      this.runBase(A, false)
      this.runGrade(A.tex, B, t)
      images.push(await this.fboToJpeg(B, 'second'))
      // Composite.
      this.runBase(A, false)
      this.runBlend(firstTex, A.tex, B, p.dx)
      this.runGrade(B.tex, A, t)
      images.push(await this.fboToJpeg(A, 'composite'))
    } else if (p.mode === 'echo') {
      this.runBase(A, false)
      this.runEcho(A.tex, B)
      this.runGrade(B.tex, A, t)
      images.push(await this.fboToJpeg(A, 'single'))
    } else if (p.mode === 'trails') {
      this.runBase(A, false)
      this.runTrailsShow(A.tex, this.accum[this.accumIdx].tex, B)
      this.runGrade(B.tex, A, t)
      images.push(await this.fboToJpeg(A, 'single'))
    } else {
      this.runBase(A, false)
      this.runGrade(A.tex, B, t)
      images.push(await this.fboToJpeg(B, 'single'))
    }

    return {
      groupId: crypto.randomUUID(),
      format: p.format,
      mode: p.mode,
      blendMode: p.dx.blendMode,
      opacity: p.dx.opacity,
      lookIntensity: p.look.enabled ? p.look.intensity : 0,
      lutName: this.lutName,
      images,
    }
  }

  private async fboToJpeg(fbo: Fbo, kind: CaptureImage['kind']): Promise<CaptureImage> {
    const gl = this.gl
    const w = fbo.w
    const h = fbo.h
    gl.bindFramebuffer(gl.FRAMEBUFFER, fbo.fbo)
    const px = new Uint8Array(w * h * 4)
    gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, px)
    gl.bindFramebuffer(gl.FRAMEBUFFER, null)

    // GL origin is bottom-left; flip rows for canvas consumption.
    const row = w * 4
    const flipped = new Uint8Array(px.length)
    for (let y = 0; y < h; y++) {
      flipped.set(px.subarray((h - 1 - y) * row, (h - y) * row), y * row)
    }

    this.fullCanvas.width = w
    this.fullCanvas.height = h
    const ctx = this.fullCanvas.getContext('2d')
    if (!ctx) throw new Error('2D canvas unavailable for capture')
    ctx.putImageData(new ImageData(new Uint8ClampedArray(flipped.buffer, flipped.byteOffset, flipped.length), w, h), 0, 0)

    const dataUrl = await canvasToJpeg(this.fullCanvas, 0.95)

    const tw = 420
    const th = Math.max(1, Math.round((420 * h) / w))
    this.thumbCanvas.width = tw
    this.thumbCanvas.height = th
    const tctx = this.thumbCanvas.getContext('2d')
    if (!tctx) throw new Error('2D canvas unavailable for thumbnail')
    tctx.drawImage(this.fullCanvas, 0, 0, tw, th)
    const thumb = await canvasToJpeg(this.thumbCanvas, 0.8)

    return { kind, dataUrl, thumb, width: w, height: h }
  }
}

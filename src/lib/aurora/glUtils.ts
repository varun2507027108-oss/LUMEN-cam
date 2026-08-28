/**
 * Minimal WebGL2 helpers for the AuroraCam pass pipeline.
 * One fullscreen quad, one program per pass, RGBA8 FBOs everywhere.
 */

export interface Fbo {
  fbo: WebGLFramebuffer
  tex: WebGLTexture
  w: number
  h: number
}

export interface ProgramBundle {
  program: WebGLProgram
  u: Record<string, WebGLUniformLocation | null>
}

function compileShader(gl: WebGL2RenderingContext, type: number, source: string): WebGLShader {
  const shader = gl.createShader(type)
  if (!shader) throw new Error('Failed to create shader')
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(shader) ?? 'unknown error'
    gl.deleteShader(shader)
    throw new Error(`Shader compile failed: ${log}`)
  }
  return shader
}

export function buildProgram(
  gl: WebGL2RenderingContext,
  vsSource: string,
  fsSource: string,
  uniformNames: string[],
): ProgramBundle {
  const vs = compileShader(gl, gl.VERTEX_SHADER, vsSource)
  const fs = compileShader(gl, gl.FRAGMENT_SHADER, fsSource)
  const program = gl.createProgram()
  if (!program) throw new Error('Failed to create program')
  gl.attachShader(program, vs)
  gl.attachShader(program, fs)
  gl.linkProgram(program)
  gl.deleteShader(vs)
  gl.deleteShader(fs)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const log = gl.getProgramInfoLog(program) ?? 'unknown error'
    gl.deleteProgram(program)
    throw new Error(`Program link failed: ${log}`)
  }
  const u: Record<string, WebGLUniformLocation | null> = {}
  for (const name of uniformNames) u[name] = gl.getUniformLocation(program, name)
  return { program, u }
}

export function createFbo(gl: WebGL2RenderingContext, w: number, h: number): Fbo {
  const tex = gl.createTexture()
  if (!tex) throw new Error('Failed to create texture')
  gl.bindTexture(gl.TEXTURE_2D, tex)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  const fbo = gl.createFramebuffer()
  if (!fbo) throw new Error('Failed to create framebuffer')
  gl.bindFramebuffer(gl.FRAMEBUFFER, fbo)
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0)
  if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, null)
    gl.deleteFramebuffer(fbo)
    gl.deleteTexture(tex)
    throw new Error(`Framebuffer incomplete at ${w}x${h}`)
  }
  gl.bindFramebuffer(gl.FRAMEBUFFER, null)
  return { fbo, tex, w, h }
}

export function disposeFbo(gl: WebGL2RenderingContext, fbo: Fbo | null | undefined): void {
  if (!fbo) return
  gl.deleteFramebuffer(fbo.fbo)
  gl.deleteTexture(fbo.tex)
}

/** Creates an empty 2D data texture (used for the per-frame camera upload). */
export function createSourceTexture(gl: WebGL2RenderingContext): WebGLTexture {
  const tex = gl.createTexture()
  if (!tex) throw new Error('Failed to create texture')
  gl.bindTexture(gl.TEXTURE_2D, tex)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array([0, 0, 0, 255]))
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  return tex
}

/** One-shot upload of a still image (remix exposures). */
export function uploadImageTexture(
  gl: WebGL2RenderingContext,
  img: HTMLImageElement,
): { tex: WebGLTexture; w: number; h: number } {
  const tex = gl.createTexture()
  if (!tex) throw new Error('Failed to create texture')
  gl.bindTexture(gl.TEXTURE_2D, tex)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  return { tex, w: img.naturalWidth, h: img.naturalHeight }
}

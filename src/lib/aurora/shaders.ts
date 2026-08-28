/**
 * GLSL ES 3.00 sources for the AuroraCam pass pipeline.
 *
 * Fixed render order (mirrors the project architecture):
 *   OES-equivalent source sample (+ aspect-crop for format mode)
 *   → creative pass (per mode: blend / echo / trails)
 *   → look pass (tone curve → 3D LUT → grain → vignette)
 *   → screen, or FBO → readPixels on capture.
 *
 * Blend formulas are pinned to the project spec (W3C compositing):
 *   Screen = 1-(1-A)(1-B); Lighten = max(A,B); Add = min(A+B,1);
 *   Multiply = A*B; Overlay = A<0.5 ? 2AB : 1-2(1-A)(1-B);
 *   Normal = crossfade. Final = mix(B, blended, uOpacity).
 */

export const VS_QUAD = `#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main() {
  vUv = aPos * 0.5 + 0.5;
  gl_Position = vec4(aPos, 0.0, 1.0);
}
`

/** Source → format-cropped base frame. Handles Y-flip and selfie mirroring. */
export const FS_BASE = `#version 300 es
precision highp float;
uniform sampler2D uSrc;
uniform vec2 uSrcSize;
uniform vec2 uDstSize;
uniform int uMirror;
in vec2 vUv;
out vec4 fragColor;
void main() {
  vec2 uv = vec2(vUv.x, 1.0 - vUv.y);
  float srcA = uSrcSize.x / max(uSrcSize.y, 1.0);
  float dstA = uDstSize.x / max(uDstSize.y, 1.0);
  if (srcA > dstA) {
    uv.x = (uv.x - 0.5) * (dstA / srcA) + 0.5;
  } else {
    uv.y = (uv.y - 0.5) * (srcA / dstA) + 0.5;
  }
  if (uMirror == 1) {
    uv.x = 1.0 - uv.x;
  }
  fragColor = vec4(texture(uSrc, uv).rgb, 1.0);
}
`

/** Plain texture copy / blit. */
export const FS_COPY = `#version 300 es
precision highp float;
uniform sampler2D uSrc;
in vec2 vUv;
out vec4 fragColor;
void main() {
  fragColor = vec4(texture(uSrc, vUv).rgb, 1.0);
}
`

/** Live double exposure: A = first exposure, B = live frame. */
export const FS_BLEND = `#version 300 es
precision highp float;
uniform sampler2D uA;
uniform sampler2D uB;
uniform int uMode;
uniform float uOpacity;
uniform int uFlipA;
in vec2 vUv;
out vec4 fragColor;
void main() {
  vec2 uvA = uFlipA == 1 ? vec2(1.0 - vUv.x, vUv.y) : vUv;
  vec3 a = texture(uA, uvA).rgb;
  vec3 b = texture(uB, vUv).rgb;
  vec3 blended;
  if (uMode == 1) {
    blended = 1.0 - (1.0 - a) * (1.0 - b);                        // Screen
  } else if (uMode == 2) {
    blended = max(a, b);                                          // Lighten
  } else if (uMode == 3) {
    blended = min(a + b, vec3(1.0));                              // Add
  } else if (uMode == 4) {
    blended = a * b;                                              // Multiply
  } else if (uMode == 5) {
    blended = mix(2.0 * a * b, 1.0 - 2.0 * (1.0 - a) * (1.0 - b), step(vec3(0.5), a)); // Overlay
  } else {
    blended = a;                                                  // Normal
  }
  fragColor = vec4(mix(b, blended, uOpacity), 1.0);
}
`

/** Motion echo: current frame + ring-buffer ghosts with falloff. */
export const FS_ECHO = `#version 300 es
precision highp float;
uniform sampler2D uCurrent;
uniform sampler2D uEcho0;
uniform sampler2D uEcho1;
uniform sampler2D uEcho2;
uniform sampler2D uEcho3;
uniform sampler2D uEcho4;
uniform float uWeights[5];
uniform float uNorm;
in vec2 vUv;
out vec4 fragColor;
void main() {
  // Unrolled: GLSL ES 3.00 restricts sampler-array indexing to constant
  // integral expressions on several drivers (incl. ANGLE/SwiftShader).
  vec3 c = texture(uCurrent, vUv).rgb;
  c += texture(uEcho0, vUv).rgb * uWeights[0];
  c += texture(uEcho1, vUv).rgb * uWeights[1];
  c += texture(uEcho2, vUv).rgb * uWeights[2];
  c += texture(uEcho3, vUv).rgb * uWeights[3];
  c += texture(uEcho4, vUv).rgb * uWeights[4];
  fragColor = vec4(c / uNorm, 1.0);
}
`

/** Light trails accumulation: bright-pass + decay + Lighten/Add combine. */
export const FS_TRAILS_ACCUM = `#version 300 es
precision highp float;
uniform sampler2D uBase;
uniform sampler2D uPrev;
uniform float uThreshold;
uniform float uDecay;
uniform int uAddMode;
in vec2 vUv;
out vec4 fragColor;
void main() {
  vec3 b = texture(uBase, vUv).rgb;
  float l = dot(b, vec3(0.2126, 0.7152, 0.0722));
  float m = smoothstep(uThreshold, uThreshold + 0.09, l);
  vec3 bright = b * m;
  vec3 prev = texture(uPrev, vUv).rgb * uDecay;
  vec3 acc = (uAddMode == 1) ? min(prev + bright, vec3(1.0)) : max(prev, bright);
  fragColor = vec4(acc, 1.0);
}
`

/** Trails display: current frame with accumulated glow on top. */
export const FS_TRAILS_SHOW = `#version 300 es
precision highp float;
uniform sampler2D uBase;
uniform sampler2D uAccum;
in vec2 vUv;
out vec4 fragColor;
void main() {
  vec3 base = texture(uBase, vUv).rgb;
  vec3 acc = texture(uAccum, vUv).rgb;
  fragColor = vec4(max(base, acc), 1.0);
}
`

/**
 * Signature Look: tone curve (highlight shoulder + lifted matte blacks)
 * → 3D LUT (sampler3D, coord = c*(N-1)/N + 0.5/N with DOMAIN_MIN/MAX)
 * → luminance-masked animated grain (weight = 4*l*(1-l))
 * → vignette. Final = mix(clean, graded, uIntensity).
 */
export const FS_LOOK = `#version 300 es
precision highp float;
precision highp sampler3D;
uniform sampler2D uSrc;
uniform sampler3D uLut;
uniform float uLutScale;
uniform float uLutOffset;
uniform vec3 uDomainMin;
uniform vec3 uDomainMax;
uniform float uIntensity;
uniform float uGrain;
uniform float uVignette;
uniform float uTime;
uniform vec2 uGrainScale;
in vec2 vUv;
out vec4 fragColor;

const vec3 W = vec3(0.2126, 0.7152, 0.0722);

vec3 toneCurve(vec3 c) {
  // Gentle highlight shoulder.
  vec3 sh = 1.0 - pow(clamp(1.0 - c, 0.0, 1.0), vec3(1.35));
  c = mix(c, sh, 0.65);
  // Lifted matte blacks — shadow-weighted lift.
  float l = dot(c, W);
  vec3 matte = c * 0.92 + vec3(0.05, 0.047, 0.06);
  c = mix(c, matte, (1.0 - smoothstep(0.0, 0.5, l)) * 0.85);
  return c;
}

float hash(vec2 p) {
  p = fract(p * vec2(443.8975, 397.2953));
  p += dot(p, p + 21.5487);
  return fract(p.x * p.y);
}

void main() {
  vec3 clean = texture(uSrc, vUv).rgb;
  vec3 c = toneCurve(clean);
  vec3 dom = uDomainMin + c * (uDomainMax - uDomainMin);
  vec3 coord = dom * uLutScale + uLutOffset;
  c = texture(uLut, coord).rgb;
  float l = dot(c, W);
  float n = hash(vUv * uGrainScale + vec2(fract(uTime * 0.731) * 91.7, fract(uTime * 0.517) * 57.3));
  c += (n - 0.5) * uGrain * 4.0 * l * (1.0 - l);
  float d = distance(vUv, vec2(0.5));
  c *= 1.0 - uVignette * smoothstep(0.34, 0.8, d);
  fragColor = vec4(clamp(mix(clean, c, uIntensity), 0.0, 1.0), 1.0);
}
`

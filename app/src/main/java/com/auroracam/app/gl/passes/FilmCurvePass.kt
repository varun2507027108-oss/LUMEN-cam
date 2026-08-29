package com.auroracam.app.gl.passes

// Ported from verified web prototype — reference-web/src/lib/aurora/shaders.ts (FS_LOOK) + Film Halation Pass

import android.opengl.GLES30
import android.util.Log
import com.auroracam.app.gl.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FilmCurvePass {

    companion object {
        private const val TAG = "FilmCurvePass"

        private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
out vec2 vUv;

void main() {
    vUv = (aPosition + 1.0) * 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """#version 300 es
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
uniform float uHalation;
uniform float uHalationThreshold;
uniform float uTime;
uniform vec2 uGrainScale;
uniform vec2 uTexelSize;

in vec2 vUv;
out vec4 fragColor;

const vec3 W = vec3(0.2126, 0.7152, 0.0722);

vec3 toneCurve(vec3 c) {
    // 1) Highlight shoulder starting compression earlier at 0.72
    float l = dot(c, W);
    if (l > 0.72) {
        float shoulderT = (l - 0.72) / 0.28;
        float sh = 1.0 - pow(clamp(1.0 - shoulderT, 0.0, 1.0), 1.45);
        c = mix(c, vec3(0.72 + sh * 0.26), 0.70);
    }

    // 2) Lifted matte blacks with true-black anchor (deep 0.0 stays 0.0, shadow toe lifts 0.05)
    float matteLift = 0.05 * smoothstep(0.0, 0.08, l) * (1.0 - smoothstep(0.08, 0.45, l));
    c += vec3(matteLift * 1.05, matteLift * 0.95, matteLift * 1.10);
    return clamp(c, 0.0, 1.0);
}

float hash(vec2 p) {
    p = fract(p * vec2(443.8975, 397.2953));
    p += dot(p, p + 21.5487);
    return fract(p.x * p.y);
}

vec3 computeHalation(vec2 uv) {
    if (uHalation <= 0.001) return vec3(0.0);

    // Multi-tap directional sampling around highlights gated by uHalationThreshold
    vec2 stepSize = uTexelSize * 4.5;
    vec3 bloomAccum = vec3(0.0);
    float weightTotal = 0.0;
    float upperKnee = min(uHalationThreshold + 0.30, 1.0);

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = uv + vec2(float(x), float(y)) * stepSize;
            vec3 s = texture(uSrc, offset).rgb;
            float sl = dot(s, W);
            float brightFactor = smoothstep(uHalationThreshold, upperKnee, sl);
            float weight = (x == 0 && y == 0) ? 0.30 : 0.0875;
            bloomAccum += s * brightFactor * weight;
            weightTotal += weight;
        }
    }

    vec3 bloomColor = bloomAccum / max(weightTotal, 0.001);
    // Film halation amber/red tint
    vec3 halationTint = vec3(1.0, 0.85, 0.70);
    return bloomColor * halationTint * uHalation;
}

void main() {
    vec3 clean = texture(uSrc, vUv).rgb;

    // 1. Tone curve shaping
    vec3 c = toneCurve(clean);

    // 2. 3D LUT Color Grade
    vec3 dom = uDomainMin + c * (uDomainMax - uDomainMin);
    vec3 coord = dom * uLutScale + uLutOffset;
    c = texture(uLut, coord).rgb;

    // 3. Film Halation (Warm Highlight Bloom) screen-blended before grain
    vec3 halation = computeHalation(vUv);
    if (uHalation > 0.001) {
        c = 1.0 - (1.0 - c) * (1.0 - halation);
    }

    // 4. Analog Film Grain (2-Tap Multi-Scale Clumping)
    float l = dot(c, W);
    vec2 jitterOffset = vec2(fract(uTime * 0.731) * 91.7, fract(uTime * 0.517) * 57.3);
    float nFine = hash(vUv * uGrainScale + jitterOffset);
    float nCoarse = hash(vUv * uGrainScale * 0.4 + jitterOffset);
    float n = mix(nFine, nCoarse, 0.35);
    c += (n - 0.5) * uGrain * 2.2 * l * (1.0 - l);

    // 5. Optical Vignette
    float d = distance(vUv, vec2(0.5));
    c *= 1.0 - uVignette * smoothstep(0.34, 0.80, d);

    fragColor = vec4(clamp(mix(clean, c, uIntensity), 0.0, 1.0), 1.0);
}
"""
    }

    private var programId = 0
    private var uSrcLoc = -1
    private var uLutLoc = -1
    private var uLutScaleLoc = -1
    private var uLutOffsetLoc = -1
    private var uDomainMinLoc = -1
    private var uDomainMaxLoc = -1
    private var uIntensityLoc = -1
    private var uGrainLoc = -1
    private var uVignetteLoc = -1
    private var uHalationLoc = -1
    private var uHalationThresholdLoc = -1
    private var uTimeLoc = -1
    private var uGrainScaleLoc = -1
    private var uTexelSizeLoc = -1

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun init() {
        programId = ShaderCompiler.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (programId == 0) {
            Log.e(TAG, "Failed to compile FilmCurvePass shader program")
            return
        }

        uSrcLoc = GLES30.glGetUniformLocation(programId, "uSrc")
        uLutLoc = GLES30.glGetUniformLocation(programId, "uLut")
        uLutScaleLoc = GLES30.glGetUniformLocation(programId, "uLutScale")
        uLutOffsetLoc = GLES30.glGetUniformLocation(programId, "uLutOffset")
        uDomainMinLoc = GLES30.glGetUniformLocation(programId, "uDomainMin")
        uDomainMaxLoc = GLES30.glGetUniformLocation(programId, "uDomainMax")
        uIntensityLoc = GLES30.glGetUniformLocation(programId, "uIntensity")
        uGrainLoc = GLES30.glGetUniformLocation(programId, "uGrain")
        uVignetteLoc = GLES30.glGetUniformLocation(programId, "uVignette")
        uHalationLoc = GLES30.glGetUniformLocation(programId, "uHalation")
        uHalationThresholdLoc = GLES30.glGetUniformLocation(programId, "uHalationThreshold")
        uTimeLoc = GLES30.glGetUniformLocation(programId, "uTime")
        uGrainScaleLoc = GLES30.glGetUniformLocation(programId, "uGrainScale")
        uTexelSizeLoc = GLES30.glGetUniformLocation(programId, "uTexelSize")

        Log.i(TAG, "FilmCurvePass initialized with Halation & GLES 3.0 sampler3D")
    }

    fun render(
        srcTextureId: Int,
        lutTextureId: Int,
        lutSize: Int = 33,
        domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
        domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
        intensity: Float = 1.0f,
        grain: Float = 0.04f,
        vignette: Float = 0.12f,
        halation: Float = 0.20f,
        halationThreshold: Float = 0.75f,
        timeSeconds: Float = 0f,
        aspectRatio: Float = 1.0f,
        width: Int = 1080,
        height: Int = 1920
    ) {
        if (programId == 0) return

        GLES30.glUseProgram(programId)

        // Texture Unit 0: 2D Source
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTextureId)
        GLES30.glUniform1i(uSrcLoc, 0)

        // Texture Unit 1: 3D LUT
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glUniform1i(uLutLoc, 1)

        val lutScale = (lutSize - 1.0f) / lutSize.toFloat()
        val lutOffset = 0.5f / lutSize.toFloat()

        GLES30.glUniform1f(uLutScaleLoc, lutScale)
        GLES30.glUniform1f(uLutOffsetLoc, lutOffset)
        GLES30.glUniform3fv(uDomainMinLoc, 1, domainMin, 0)
        GLES30.glUniform3fv(uDomainMaxLoc, 1, domainMax, 0)
        GLES30.glUniform1f(uIntensityLoc, intensity.coerceIn(0f, 1f))
        GLES30.glUniform1f(uGrainLoc, grain.coerceAtLeast(0f))
        GLES30.glUniform1f(uVignetteLoc, vignette.coerceIn(0f, 1f))
        GLES30.glUniform1f(uHalationLoc, halation.coerceIn(0f, 1f))
        GLES30.glUniform1f(uHalationThresholdLoc, halationThreshold.coerceIn(0.10f, 1.0f))
        GLES30.glUniform1f(uTimeLoc, timeSeconds % 3600f)
        GLES30.glUniform2f(uGrainScaleLoc, 900.0f * aspectRatio, 900.0f)
        GLES30.glUniform2f(uTexelSizeLoc, 1.0f / maxOf(1, width).toFloat(), 1.0f / maxOf(1, height).toFloat())

        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}

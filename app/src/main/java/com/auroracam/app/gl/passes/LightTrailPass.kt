package com.auroracam.app.gl.passes

import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

/**
 * Light Trail Mode Shader Pass.
 *
 * Accumulates bright moving lights (car headlights, light painting, city bokeh)
 * across frames into streaks and luminous light trails.
 *
 * Supports soft-knee thresholding and selectable accumulation blend modes:
 * - 0: MAX (non-destructive maximum intensity hold)
 * - 1: ADD (additive accumulation with energy build-up)
 * - 2: SCREEN (soft photographic highlight roll-off)
 */
class LightTrailPass {

    // Pass A: Accumulate bright pixels with temporal decay and selectable blend
    private val accumVertexCode = """
        #version 300 es
        layout(location = 0) in vec2 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        out vec2 vTexCoord;

        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val accumFragmentCode = """
        #version 300 es
        precision highp float;

        uniform sampler2D uTexCurr;
        uniform sampler2D uTexAccum;
        uniform float uThreshold;       // e.g. 0.55
        uniform float uKnee;            // e.g. 0.10 (softness knee)
        uniform float uDecay;           // e.g. 0.94
        uniform float uTrailIntensity;  // e.g. 1.25
        uniform int uBlendMode;         // 0: MAX, 1: ADD, 2: SCREEN

        in vec2 vTexCoord;
        out vec4 fragColor;

        void main() {
            vec4 curr = texture(uTexCurr, vTexCoord);
            vec4 prevAccum = texture(uTexAccum, vTexCoord);

            // Compute relative luminance
            float luma = dot(curr.rgb, vec3(0.2126, 0.7152, 0.0722));

            // Soft-knee threshold isolation for luminous elements
            float knee = max(0.02, uKnee);
            float brightFactor = smoothstep(uThreshold - knee, uThreshold + knee, luma);
            vec3 brightColor = curr.rgb * (brightFactor * uTrailIntensity);

            vec3 faded = prevAccum.rgb * uDecay;
            vec3 newAccum;

            if (uBlendMode == 1) {
                // 1. Additive accumulation with saturation clamping
                newAccum = min(vec3(1.0), faded + brightColor);
            } else if (uBlendMode == 2) {
                // 2. Screen accumulation (soft photographic highlight rolloff)
                newAccum = vec3(1.0) - (vec3(1.0) - faded) * (vec3(1.0) - clamp(brightColor, 0.0, 1.0));
            } else {
                // 0. Max accumulation (default clean light painting)
                newAccum = max(faded, brightColor);
            }

            fragColor = vec4(clamp(newAccum, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    // Pass B: Combine live scene + accumulated light streaks
    private val combineFragmentCode = """
        #version 300 es
        precision highp float;

        uniform sampler2D uTexCurr;
        uniform sampler2D uTexAccum;
        uniform float uMix;

        in vec2 vTexCoord;
        out vec4 fragColor;

        void main() {
            vec4 curr = texture(uTexCurr, vTexCoord);
            vec4 accum = texture(uTexAccum, vTexCoord);

            // Screen/Lighten blend of light trails over live scene
            vec3 combined = 1.0 - (1.0 - curr.rgb) * (1.0 - clamp(accum.rgb * uMix, 0.0, 1.0));
            fragColor = vec4(clamp(combined, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    private var accumProgram = 0
    private var uTexCurrAccumLoc = 0
    private var uTexAccumLoc = 0
    private var uThresholdLoc = 0
    private var uKneeLoc = 0
    private var uDecayLoc = 0
    private var uTrailIntensityLoc = 0
    private var uBlendModeLoc = 0

    private var combineProgram = 0
    private var uTexCurrCombineLoc = 0
    private var uTexAccumCombineLoc = 0
    private var uMixLoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        accumProgram = ShaderCompiler.createProgram(accumVertexCode, accumFragmentCode)
        uTexCurrAccumLoc = GLES30.glGetUniformLocation(accumProgram, "uTexCurr")
        uTexAccumLoc = GLES30.glGetUniformLocation(accumProgram, "uTexAccum")
        uThresholdLoc = GLES30.glGetUniformLocation(accumProgram, "uThreshold")
        uKneeLoc = GLES30.glGetUniformLocation(accumProgram, "uKnee")
        uDecayLoc = GLES30.glGetUniformLocation(accumProgram, "uDecay")
        uTrailIntensityLoc = GLES30.glGetUniformLocation(accumProgram, "uTrailIntensity")
        uBlendModeLoc = GLES30.glGetUniformLocation(accumProgram, "uBlendMode")

        combineProgram = ShaderCompiler.createProgram(accumVertexCode, combineFragmentCode)
        uTexCurrCombineLoc = GLES30.glGetUniformLocation(combineProgram, "uTexCurr")
        uTexAccumCombineLoc = GLES30.glGetUniformLocation(combineProgram, "uTexAccum")
        uMixLoc = GLES30.glGetUniformLocation(combineProgram, "uMix")

        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)

        vaoId = vaos[0]
        vboId = vbos[0]

        val vertexBuffer: FloatBuffer = GLConstants.createFloatBuffer(GLConstants.QUAD_VERTICES)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            GLConstants.QUAD_VERTICES.size * GLConstants.FLOAT_SIZE,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            2,
            GLES30.GL_FLOAT,
            false,
            GLConstants.VERTEX_STRIDE,
            GLConstants.POSITION_OFFSET
        )

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            2,
            GLES30.GL_FLOAT,
            false,
            GLConstants.VERTEX_STRIDE,
            GLConstants.TEXCOORD_OFFSET
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)

        GLConstants.checkGLError("LightTrailPass.init")
        isInitialized = true
    }

    fun renderAccumulation(
        currTexId: Int,
        prevAccumTexId: Int,
        threshold: Float = 0.55f,
        knee: Float = 0.10f,
        decay: Float = 0.94f,
        intensity: Float = 1.25f,
        blendMode: Int = 0 // 0: MAX, 1: ADD, 2: SCREEN
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(accumProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currTexId)
        GLES30.glUniform1i(uTexCurrAccumLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prevAccumTexId)
        GLES30.glUniform1i(uTexAccumLoc, 1)

        GLES30.glUniform1f(uThresholdLoc, threshold)
        GLES30.glUniform1f(uKneeLoc, knee)
        GLES30.glUniform1f(uDecayLoc, decay)
        GLES30.glUniform1f(uTrailIntensityLoc, intensity)
        GLES30.glUniform1i(uBlendModeLoc, blendMode)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    fun renderCombine(
        currTexId: Int,
        accumTexId: Int,
        mix: Float = 1.0f
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(combineProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currTexId)
        GLES30.glUniform1i(uTexCurrCombineLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, accumTexId)
        GLES30.glUniform1i(uTexAccumCombineLoc, 1)

        GLES30.glUniform1f(uMixLoc, mix)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    fun release() {
        if (!isInitialized) return
        GLES30.glDeleteProgram(accumProgram)
        GLES30.glDeleteProgram(combineProgram)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
        isInitialized = false
    }
}

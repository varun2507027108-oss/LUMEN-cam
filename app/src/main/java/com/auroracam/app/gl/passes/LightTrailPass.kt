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
 */
class LightTrailPass {

    // Pass A: Accumulate bright pixels with temporal decay
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
        uniform float uDecay;           // e.g. 0.94
        uniform float uTrailIntensity;  // e.g. 1.25

        in vec2 vTexCoord;
        out vec4 fragColor;

        void main() {
            vec4 curr = texture(uTexCurr, vTexCoord);
            vec4 prevAccum = texture(uTexAccum, vTexCoord);

            // Compute relative luminance
            float luma = dot(curr.rgb, vec3(0.2126, 0.7152, 0.0722));

            // Smooth threshold isolation for luminous elements
            float brightFactor = smoothstep(uThreshold, min(1.0, uThreshold + 0.18), luma);
            vec3 brightColor = curr.rgb * (brightFactor * uTrailIntensity);

            // Decay previous accumulation and take max/additive for pristine light streaks
            vec3 decayedAccum = prevAccum.rgb * uDecay;
            vec3 newAccum = max(decayedAccum, brightColor);

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
    private var uDecayLoc = 0
    private var uTrailIntensityLoc = 0

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
        uDecayLoc = GLES30.glGetUniformLocation(accumProgram, "uDecay")
        uTrailIntensityLoc = GLES30.glGetUniformLocation(accumProgram, "uTrailIntensity")

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

    /**
     * Step 1: Render new accumulated light into accumulation FBO.
     */
    fun renderAccumulation(
        currTexId: Int,
        prevAccumTexId: Int,
        threshold: Float = 0.55f,
        decay: Float = 0.94f,
        intensity: Float = 1.25f
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(accumProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currTexId)
        GLES30.glUniform1i(uTexCurrAccumLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prevAccumTexId)
        GLES30.glUniform1i(uTexAccumLoc, 1)

        GLES30.glUniform1f(uThresholdLoc, threshold.coerceIn(0.1f, 0.95f))
        GLES30.glUniform1f(uDecayLoc, decay.coerceIn(0.50f, 0.99f))
        GLES30.glUniform1f(uTrailIntensityLoc, intensity.coerceIn(0.5f, 2.5f))

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    /**
     * Step 2: Combine live frame with accumulated light streaks into target scene FBO.
     */
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

        GLES30.glUniform1f(uMixLoc, mix.coerceIn(0.0f, 1.5f))

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    fun release() {
        if (isInitialized) {
            GLES30.glDeleteProgram(accumProgram)
            GLES30.glDeleteProgram(combineProgram)
            val vaos = intArrayOf(vaoId)
            val vbos = intArrayOf(vboId)
            GLES30.glDeleteVertexArrays(1, vaos, 0)
            GLES30.glDeleteBuffers(1, vbos, 0)
            isInitialized = false
        }
    }
}

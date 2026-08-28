package com.auroracam.app.gl.passes

import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

/**
 * Motion-Only Exposure Shader Pass.
 *
 * Computes inter-frame temporal difference to isolate moving subjects and
 * blend dynamic motion trails while keeping the static background razor-sharp.
 *
 * Uses chromaticity-compensated difference and soft knee thresholding with
 * noise floor rejection to prevent camera auto-exposure changes from triggering
 * false motion across the entire frame.
 */
class MotionExposurePass {

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec2 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        out vec2 vTexCoord;

        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        precision highp float;

        uniform sampler2D uTexCurr;
        uniform sampler2D uTexPrev;
        uniform float uThreshold;   // e.g. 0.08 (0.01 to 0.35)
        uniform float uSoftness;    // e.g. 0.06 (knee width)
        uniform float uNoiseFloor;  // e.g. 0.02 (sensor noise deadband)
        uniform float uBlend;       // 0.1 to 1.0
        uniform int uStyle;         // 0: Classic Ghost, 1: High-Contrast Luminous

        in vec2 vTexCoord;
        out vec4 fragColor;

        void main() {
            vec4 curr = texture(uTexCurr, vTexCoord);
            vec4 prev = texture(uTexPrev, vTexCoord);

            // 1. Compute perceptual luminance
            float currLuma = dot(curr.rgb, vec3(0.299, 0.587, 0.114));
            float prevLuma = dot(prev.rgb, vec3(0.299, 0.587, 0.114));
            float rawLumaDiff = abs(currLuma - prevLuma);

            // 2. Chromaticity difference: invariant to global exposure/brightness changes
            vec3 currChrom = curr.rgb / max(currLuma + 0.02, 0.05);
            vec3 prevChrom = prev.rgb / max(prevLuma + 0.02, 0.05);
            float chromDiff = length(currChrom - prevChrom);

            // Combined robust motion metric
            float motionMetric = max(rawLumaDiff, chromDiff * 0.35);

            // 3. Noise Floor Deadband: reject subtle sensor grain fluctuations
            motionMetric = max(0.0, motionMetric - uNoiseFloor);

            // 4. Soft Threshold Masking with organic knee transition
            float knee = max(0.01, uSoftness);
            float mask = smoothstep(uThreshold, uThreshold + knee, motionMetric);

            vec3 motionColor;
            if (uStyle == 1) {
                // High-contrast luminous trail
                vec3 glow = prev.rgb * 1.35;
                motionColor = 1.0 - (1.0 - curr.rgb) * (1.0 - clamp(glow, 0.0, 1.0));
            } else {
                // Classic multi-exposure ghost
                motionColor = mix(prev.rgb, curr.rgb, 0.40);
            }

            // Keep stationary background identical to curr, mix motion where detected
            vec3 outRGB = mix(curr.rgb, motionColor, mask * uBlend);

            fragColor = vec4(clamp(outRGB, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    private var program = 0
    private var uTexCurrLoc = 0
    private var uTexPrevLoc = 0
    private var uThresholdLoc = 0
    private var uSoftnessLoc = 0
    private var uNoiseFloorLoc = 0
    private var uBlendLoc = 0
    private var uStyleLoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)

        uTexCurrLoc = GLES30.glGetUniformLocation(program, "uTexCurr")
        uTexPrevLoc = GLES30.glGetUniformLocation(program, "uTexPrev")
        uThresholdLoc = GLES30.glGetUniformLocation(program, "uThreshold")
        uSoftnessLoc = GLES30.glGetUniformLocation(program, "uSoftness")
        uNoiseFloorLoc = GLES30.glGetUniformLocation(program, "uNoiseFloor")
        uBlendLoc = GLES30.glGetUniformLocation(program, "uBlend")
        uStyleLoc = GLES30.glGetUniformLocation(program, "uStyle")

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

        GLConstants.checkGLError("MotionExposurePass.init")
        isInitialized = true
    }

    fun render(
        currTexId: Int,
        prevTexId: Int,
        threshold: Float = 0.08f,
        softness: Float = 0.06f,
        noiseFloor: Float = 0.02f,
        blend: Float = 0.85f,
        style: Int = 0
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currTexId)
        GLES30.glUniform1i(uTexCurrLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prevTexId)
        GLES30.glUniform1i(uTexPrevLoc, 1)

        GLES30.glUniform1f(uThresholdLoc, threshold)
        GLES30.glUniform1f(uSoftnessLoc, softness)
        GLES30.glUniform1f(uNoiseFloorLoc, noiseFloor)
        GLES30.glUniform1f(uBlendLoc, blend)
        GLES30.glUniform1i(uStyleLoc, style)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    fun release() {
        if (!isInitialized) return
        GLES30.glDeleteProgram(program)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
        isInitialized = false
    }
}

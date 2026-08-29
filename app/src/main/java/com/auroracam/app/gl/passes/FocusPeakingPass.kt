package com.auroracam.app.gl.passes

import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

/**
 * Focus Peaking Shader Pass.
 *
 * Real-time GPU edge-detection overlay using a 3x3 Sobel kernel over luminance.
 * Highlights high-contrast, in-focus focal planes with an optic focus tint (Mint/Cyan)
 * to provide immediate tactile manual focus confirmation.
 */
class FocusPeakingPass {

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

        uniform sampler2D uScene;
        uniform float uThreshold;    // Edge detection sensitivity threshold (e.g. 0.15 to 0.35)
        uniform float uIntensity;    // 0.0 to 1.0 overlay intensity
        uniform vec2 uTexelSize;     // 1.0 / vec2(width, height)
        uniform vec3 uPeakColor;     // Focus highlight tint (e.g. Focus Mint vec3(0.0, 1.0, 0.65))

        in vec2 vTexCoord;
        out vec4 fragColor;

        const vec3 W = vec3(0.2126, 0.7152, 0.0722);

        float getLuma(vec2 offset) {
            vec3 rgb = texture(uScene, clamp(vTexCoord + offset * uTexelSize, vec2(0.001), vec2(0.999))).rgb;
            return dot(rgb, W);
        }

        void main() {
            vec4 base = texture(uScene, vTexCoord);
            if (uIntensity <= 0.001) {
                fragColor = base;
                return;
            }

            // 3x3 Sobel luminance sampling
            float s00 = getLuma(vec2(-1.0, -1.0));
            float s10 = getLuma(vec2( 0.0, -1.0));
            float s20 = getLuma(vec2( 1.0, -1.0));

            float s01 = getLuma(vec2(-1.0,  0.0));
            float s21 = getLuma(vec2( 1.0,  0.0));

            float s02 = getLuma(vec2(-1.0,  1.0));
            float s12 = getLuma(vec2( 0.0,  1.0));
            float s22 = getLuma(vec2( 1.0,  1.0));

            float gx = -s00 + s20 - 2.0 * s01 + 2.0 * s21 - s02 + s22;
            float gy = -s00 - 2.0 * s10 - s20 + s02 + 2.0 * s12 + s22;

            float edge = sqrt(gx * gx + gy * gy);
            float peakFactor = smoothstep(uThreshold, uThreshold + 0.12, edge);

            vec3 blended = mix(base.rgb, uPeakColor, peakFactor * uIntensity * 0.90);
            fragColor = vec4(blended, base.a);
        }
    """.trimIndent()

    private var program = 0
    private var uSceneLoc = 0
    private var uThresholdLoc = 0
    private var uIntensityLoc = 0
    private var uTexelSizeLoc = 0
    private var uPeakColorLoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)

        uSceneLoc = GLES30.glGetUniformLocation(program, "uScene")
        uThresholdLoc = GLES30.glGetUniformLocation(program, "uThreshold")
        uIntensityLoc = GLES30.glGetUniformLocation(program, "uIntensity")
        uTexelSizeLoc = GLES30.glGetUniformLocation(program, "uTexelSize")
        uPeakColorLoc = GLES30.glGetUniformLocation(program, "uPeakColor")

        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)

        vaoId = vaos[0]
        vboId = vbos[0]

        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)

        val quadBuffer = GLConstants.createFloatBuffer(GLConstants.QUAD_VERTICES)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            GLConstants.QUAD_VERTICES.size * GLConstants.FLOAT_SIZE,
            quadBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, GLConstants.VERTEX_STRIDE, GLConstants.POSITION_OFFSET)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, GLConstants.VERTEX_STRIDE, GLConstants.TEXCOORD_OFFSET)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)

        isInitialized = true
    }

    fun render(
        sceneTexId: Int,
        threshold: Float = 0.18f,
        intensity: Float = 1.0f,
        width: Int,
        height: Int,
        peakColorR: Float = 0.0f,
        peakColorG: Float = 1.0f,
        peakColorB: Float = 0.65f
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTexId)
        GLES30.glUniform1i(uSceneLoc, 0)

        GLES30.glUniform1f(uThresholdLoc, threshold.coerceIn(0.05f, 0.80f))
        GLES30.glUniform1f(uIntensityLoc, intensity.coerceIn(0.0f, 1.0f))
        val tw = if (width > 0) 1.0f / width.toFloat() else 1.0f / 1080f
        val th = if (height > 0) 1.0f / height.toFloat() else 1.0f / 1920f
        GLES30.glUniform2f(uTexelSizeLoc, tw, th)
        GLES30.glUniform3f(uPeakColorLoc, peakColorR, peakColorG, peakColorB)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    fun release() {
        if (!isInitialized) return
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
        isInitialized = false
    }
}

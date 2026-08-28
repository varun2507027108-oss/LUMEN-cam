package com.auroracam.app.gl.passes

import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

/**
 * Temporal Echo (Ghost Trails) Shader Pass.
 *
 * Blends the current camera frame with a ring buffer of 1–3 previous frames,
 * creating dreamy, organic trailing motion echoes.
 */
class TemporalEchoPass {

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
        uniform sampler2D uTexPrev1;
        uniform sampler2D uTexPrev2;
        uniform sampler2D uTexPrev3;
        uniform int uTrailLength;  // 1, 2, or 3
        uniform float uDecay;      // 0.0 to 0.95
        uniform int uBlendMode;    // 0: Normal / Trail, 1: Screen / Additive Glow

        in vec2 vTexCoord;
        out vec4 fragColor;

        void main() {
            vec4 curr = texture(uTexCurr, vTexCoord);
            vec4 p1 = texture(uTexPrev1, vTexCoord);
            vec4 p2 = texture(uTexPrev2, vTexCoord);
            vec4 p3 = texture(uTexPrev3, vTexCoord);

            vec3 result = curr.rgb;
            float d1 = uDecay;
            float d2 = uDecay * uDecay;
            float d3 = d2 * uDecay;

            if (uBlendMode == 1) {
                // Screen / Luminous Additive Echo
                vec3 echo = p1.rgb * (d1 * 0.5);
                if (uTrailLength >= 2) echo += p2.rgb * (d2 * 0.35);
                if (uTrailLength >= 3) echo += p3.rgb * (d3 * 0.25);
                result = 1.0 - (1.0 - curr.rgb) * (1.0 - clamp(echo, 0.0, 1.0));
            } else {
                // Natural Motion Ghost Trail
                if (uTrailLength == 1) {
                    result = mix(curr.rgb, p1.rgb, d1 * 0.55);
                } else if (uTrailLength == 2) {
                    vec3 trail = mix(p2.rgb, p1.rgb, 0.6);
                    result = mix(curr.rgb, trail, d1 * 0.65);
                } else {
                    vec3 trail = p1.rgb * 0.5 + p2.rgb * 0.3 + p3.rgb * 0.2;
                    result = mix(curr.rgb, trail, d1 * 0.75);
                }
            }

            fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    private var program = 0
    private var uTexCurrLoc = 0
    private var uTexPrev1Loc = 0
    private var uTexPrev2Loc = 0
    private var uTexPrev3Loc = 0
    private var uTrailLengthLoc = 0
    private var uDecayLoc = 0
    private var uBlendModeLoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)

        uTexCurrLoc = GLES30.glGetUniformLocation(program, "uTexCurr")
        uTexPrev1Loc = GLES30.glGetUniformLocation(program, "uTexPrev1")
        uTexPrev2Loc = GLES30.glGetUniformLocation(program, "uTexPrev2")
        uTexPrev3Loc = GLES30.glGetUniformLocation(program, "uTexPrev3")
        uTrailLengthLoc = GLES30.glGetUniformLocation(program, "uTrailLength")
        uDecayLoc = GLES30.glGetUniformLocation(program, "uDecay")
        uBlendModeLoc = GLES30.glGetUniformLocation(program, "uBlendMode")

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

        GLConstants.checkGLError("TemporalEchoPass.init")
        isInitialized = true
    }

    fun render(
        currTexId: Int,
        prev1TexId: Int,
        prev2TexId: Int,
        prev3TexId: Int,
        trailLength: Int = 2,
        decay: Float = 0.75f,
        blendMode: Int = 0
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currTexId)
        GLES30.glUniform1i(uTexCurrLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prev1TexId)
        GLES30.glUniform1i(uTexPrev1Loc, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prev2TexId)
        GLES30.glUniform1i(uTexPrev2Loc, 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prev3TexId)
        GLES30.glUniform1i(uTexPrev3Loc, 3)

        GLES30.glUniform1i(uTrailLengthLoc, trailLength.coerceIn(1, 3))
        GLES30.glUniform1f(uDecayLoc, decay.coerceIn(0.0f, 0.98f))
        GLES30.glUniform1i(uBlendModeLoc, blendMode)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    fun release() {
        if (isInitialized) {
            GLES30.glDeleteProgram(program)
            val vaos = intArrayOf(vaoId)
            val vbos = intArrayOf(vboId)
            GLES30.glDeleteVertexArrays(1, vaos, 0)
            GLES30.glDeleteBuffers(1, vbos, 0)
            isInitialized = false
        }
    }
}

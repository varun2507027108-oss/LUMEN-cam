package com.auroracam.app.gl.passes

import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

/**
 * Chromatic Aberration Shader Pass.
 *
 * Simulates vintage camera lens dispersion by shifting the Red and Blue channels
 * radially relative to the optical center.
 */
class ChromaticAberrationPass {

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
        uniform float uIntensity;   // 0.0 to 1.0
        uniform float uAspectRatio; // width / height

        in vec2 vTexCoord;
        out vec4 fragColor;

        void main() {
            if (uIntensity <= 0.001) {
                fragColor = texture(uScene, vTexCoord);
                return;
            }

            vec2 center = vec2(0.5, 0.5);
            vec2 dir = vTexCoord - center;
            dir.x *= uAspectRatio;
            float dist = length(dir);

            // Radial barrel distortion curve: strongest at outer edges
            vec2 offset = normalize(dir + 0.00001) * (dist * dist) * (uIntensity * 0.022);
            offset.x /= uAspectRatio;

            float r = texture(uScene, vTexCoord + offset).r;
            float g = texture(uScene, vTexCoord).g;
            float b = texture(uScene, vTexCoord - offset).b;
            float a = texture(uScene, vTexCoord).a;

            fragColor = vec4(r, g, b, a);
        }
    """.trimIndent()

    private var program = 0
    private var uSceneLoc = 0
    private var uIntensityLoc = 0
    private var uAspectRatioLoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)

        uSceneLoc = GLES30.glGetUniformLocation(program, "uScene")
        uIntensityLoc = GLES30.glGetUniformLocation(program, "uIntensity")
        uAspectRatioLoc = GLES30.glGetUniformLocation(program, "uAspectRatio")

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

        GLConstants.checkGLError("ChromaticAberrationPass.init")
        isInitialized = true
    }

    fun render(
        sceneTexId: Int,
        intensity: Float = 0.40f,
        aspectRatio: Float = 1.0f
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTexId)
        GLES30.glUniform1i(uSceneLoc, 0)

        GLES30.glUniform1f(uIntensityLoc, intensity.coerceIn(0.0f, 1.0f))
        GLES30.glUniform1f(uAspectRatioLoc, if (aspectRatio > 0f) aspectRatio else 1.0f)

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

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
 *
 * Uses a perceptually non-linear quadratic mapping for subpixel precision at low values
 * and clamps sample coordinates to avoid boundary edge stretching/wrapping artifacts.
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

            // Perceptually non-linear strength scaling (value^2 * maxOffset)
            // Provides fine subtle control below 0.3 and expressive styling at higher values
            float strength = uIntensity * uIntensity * 0.032;

            // Radial dispersion offset scaled by squared distance from optical center
            vec2 offset = normalize(dir + 0.00001) * (dist * dist) * strength;
            offset.x /= uAspectRatio;

            // Strict clamp to valid UV domain prevents texture clamping / border line artifacts
            vec2 rCoord = clamp(vTexCoord + offset, vec2(0.001), vec2(0.999));
            vec2 gCoord = vTexCoord;
            vec2 bCoord = clamp(vTexCoord - offset, vec2(0.001), vec2(0.999));

            float r = texture(uScene, rCoord).r;
            float g = texture(uScene, gCoord).g;
            float b = texture(uScene, bCoord).b;
            float a = texture(uScene, gCoord).a;

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
        intensity: Float = 0.35f,
        aspectRatio: Float = 1.0f
    ) {
        if (!isInitialized) init()

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTexId)
        GLES30.glUniform1i(uSceneLoc, 0)

        GLES30.glUniform1f(uIntensityLoc, intensity)
        GLES30.glUniform1f(uAspectRatioLoc, aspectRatio)

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

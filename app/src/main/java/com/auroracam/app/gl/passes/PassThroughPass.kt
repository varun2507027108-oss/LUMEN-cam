package com.auroracam.app.gl.passes

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

class PassThroughPass {

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec2 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        uniform mat4 uTransformMatrix;
        uniform mat4 uAspectMatrix;

        out vec2 vTexCoord;

        void main() {
            gl_Position = uAspectMatrix * vec4(aPosition, 0.0, 1.0);
            vec4 tc = uTransformMatrix * vec4(aTexCoord, 0.0, 1.0);
            vTexCoord = tc.xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision mediump float;

        uniform samplerExternalOES uTexture;
        in vec2 vTexCoord;
        out vec4 fragColor;

        void main() {
            fragColor = texture(uTexture, vTexCoord);
        }
    """.trimIndent()

    private var program = 0
    private var uTransformMatrixLoc = 0
    private var uAspectMatrixLoc = 0
    private var uTextureLoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)

        uTransformMatrixLoc = GLES30.glGetUniformLocation(program, "uTransformMatrix")
        uAspectMatrixLoc = GLES30.glGetUniformLocation(program, "uAspectMatrix")
        uTextureLoc = GLES30.glGetUniformLocation(program, "uTexture")

        // VAO & VBO setup
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

        // Attribute 0: aPosition (vec2)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            2,
            GLES30.GL_FLOAT,
            false,
            GLConstants.VERTEX_STRIDE,
            GLConstants.POSITION_OFFSET
        )

        // Attribute 1: aTexCoord (vec2)
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

        GLConstants.checkGLError("PassThroughPass.init")
        isInitialized = true
    }

    fun render(
        textureId: Int,
        transformMatrix: FloatArray,
        aspectMatrix: FloatArray
    ) {
        if (!isInitialized) return

        GLES30.glUseProgram(program)

        // Bind OES texture to unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(uTextureLoc, 0)

        // Pass matrices
        GLES30.glUniformMatrix4fv(uTransformMatrixLoc, 1, false, transformMatrix, 0)
        GLES30.glUniformMatrix4fv(uAspectMatrixLoc, 1, false, aspectMatrix, 0)

        // Draw quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glUseProgram(0)

        GLConstants.checkGLError("PassThroughPass.render")
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

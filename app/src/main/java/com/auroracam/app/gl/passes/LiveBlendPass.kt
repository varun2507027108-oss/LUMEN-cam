package com.auroracam.app.gl.passes

// Ported from verified web prototype — reference-web/src/lib/aurora/shaders.ts

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

class LiveBlendPass {

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec2 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        uniform mat4 uTransformMatrix;
        uniform mat4 uAspectMatrix;

        out vec2 vUv;
        out vec2 vLiveUv;

        void main() {
            gl_Position = uAspectMatrix * vec4(aPosition, 0.0, 1.0);
            vUv = aTexCoord;
            vec4 tc = uTransformMatrix * vec4(aTexCoord, 0.0, 1.0);
            vLiveUv = tc.xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        // Ported from verified web prototype — reference-web/src/lib/aurora/shaders.ts
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float;

        uniform samplerExternalOES uLiveFrame;
        uniform sampler2D uFirst;
        uniform int uMode;
        uniform float uOpacity;
        uniform int uFlipFirst;

        in vec2 vUv;
        in vec2 vLiveUv;
        out vec4 fragColor;

        void main() {
            vec2 uvA = uFlipFirst == 1 ? vec2(1.0 - vUv.x, vUv.y) : vUv;
            vec3 a = texture(uFirst, uvA).rgb;
            vec3 b = texture(uLiveFrame, vLiveUv).rgb;
            vec3 blended;
            if (uMode == 1) {
                blended = 1.0 - (1.0 - a) * (1.0 - b);
            } else if (uMode == 2) {
                blended = max(a, b);
            } else if (uMode == 3) {
                blended = min(a + b, vec3(1.0));
            } else if (uMode == 4) {
                blended = a * b;
            } else if (uMode == 5) {
                blended = mix(2.0 * a * b, 1.0 - 2.0 * (1.0 - a) * (1.0 - b), step(vec3(0.5), a));
            } else {
                blended = a;
            }
            fragColor = vec4(mix(b, blended, uOpacity), 1.0);
        }
    """.trimIndent()

    private var program = 0
    private var uLiveFrameLoc = 0
    private var uFirstLoc = 0
    private var uModeLoc = 0
    private var uOpacityLoc = 0
    private var uFlipFirstLoc = 0
    private var uTransformMatrixLoc = 0
    private var uAspectMatrixLoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)

        uLiveFrameLoc = GLES30.glGetUniformLocation(program, "uLiveFrame")
        uFirstLoc = GLES30.glGetUniformLocation(program, "uFirst")
        uModeLoc = GLES30.glGetUniformLocation(program, "uMode")
        uOpacityLoc = GLES30.glGetUniformLocation(program, "uOpacity")
        uFlipFirstLoc = GLES30.glGetUniformLocation(program, "uFlipFirst")
        uTransformMatrixLoc = GLES30.glGetUniformLocation(program, "uTransformMatrix")
        uAspectMatrixLoc = GLES30.glGetUniformLocation(program, "uAspectMatrix")

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

        GLConstants.checkGLError("LiveBlendPass.init")
        isInitialized = true
    }

    fun render(
        liveOesTextureId: Int,
        firstExposureTextureId: Int,
        transformMatrix: FloatArray,
        aspectMatrix: FloatArray,
        mode: Int,
        opacity: Float,
        flipFirst: Boolean
    ) {
        if (!isInitialized) return

        GLES30.glUseProgram(program)

        // Texture Unit 0: Live OES Camera Frame
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, liveOesTextureId)
        GLES30.glUniform1i(uLiveFrameLoc, 0)

        // Texture Unit 1: First Exposure 2D Frame
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, firstExposureTextureId)
        GLES30.glUniform1i(uFirstLoc, 1)

        // Uniforms
        GLES30.glUniform1i(uModeLoc, mode)
        GLES30.glUniform1f(uOpacityLoc, opacity)
        GLES30.glUniform1i(uFlipFirstLoc, if (flipFirst) 1 else 0)

        GLES30.glUniformMatrix4fv(uTransformMatrixLoc, 1, false, transformMatrix, 0)
        GLES30.glUniformMatrix4fv(uAspectMatrixLoc, 1, false, aspectMatrix, 0)

        // Draw Quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)

        GLConstants.checkGLError("LiveBlendPass.render")
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

package com.auroracam.app.gl.passes

// Ported from verified web prototype — reference-web/src/lib/aurora/shaders.ts

import android.opengl.GLES30
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.FloatBuffer

class CompositeBlendPass {

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec2 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        out vec2 vUv;

        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vUv = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        // Ported from verified web prototype — reference-web/src/lib/aurora/shaders.ts
        precision highp float;

        uniform sampler2D uA;
        uniform sampler2D uB;
        uniform int uMode;
        uniform float uOpacity;
        uniform int uFlipA;

        in vec2 vUv;
        out vec4 fragColor;

        void main() {
            vec2 uvA = uFlipA == 1 ? vec2(1.0 - vUv.x, vUv.y) : vUv;
            // uA comes from FBO (origin bottom-left); uB comes from texImage2D (row 0 at top)
            vec2 uvB = vec2(vUv.x, 1.0 - vUv.y);
            
            vec3 a = texture(uA, uvA).rgb;
            vec3 b = texture(uB, uvB).rgb;
            vec3 blended;
            if (uMode == 1) {
                blended = 1.0 - (1.0 - a) * (1.0 - b);                        // Screen
            } else if (uMode == 2) {
                blended = max(a, b);                                          // Lighten
            } else if (uMode == 3) {
                blended = min(a + b, vec3(1.0));                              // Add
            } else if (uMode == 4) {
                blended = a * b;                                              // Multiply
            } else if (uMode == 5) {
                blended = mix(2.0 * a * b, 1.0 - 2.0 * (1.0 - a) * (1.0 - b), step(vec3(0.5), a)); // Overlay
            } else {
                blended = a;                                                  // Normal
            }
            fragColor = vec4(mix(b, blended, uOpacity), 1.0);
        }
    """.trimIndent()

    private var program = 0
    private var uALoc = 0
    private var uBLoc = 0
    private var uModeLoc = 0
    private var uOpacityLoc = 0
    private var uFlipALoc = 0

    private var vaoId = 0
    private var vboId = 0
    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)

        uALoc = GLES30.glGetUniformLocation(program, "uA")
        uBLoc = GLES30.glGetUniformLocation(program, "uB")
        uModeLoc = GLES30.glGetUniformLocation(program, "uMode")
        uOpacityLoc = GLES30.glGetUniformLocation(program, "uOpacity")
        uFlipALoc = GLES30.glGetUniformLocation(program, "uFlipA")

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

        GLConstants.checkGLError("CompositeBlendPass.init")
        isInitialized = true
    }

    fun render(
        textureA: Int,
        textureB: Int,
        mode: Int,
        opacity: Float,
        flipA: Boolean
    ) {
        if (!isInitialized) return

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureA)
        GLES30.glUniform1i(uALoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureB)
        GLES30.glUniform1i(uBLoc, 1)

        GLES30.glUniform1i(uModeLoc, mode)
        GLES30.glUniform1f(uOpacityLoc, opacity)
        GLES30.glUniform1i(uFlipALoc, if (flipA) 1 else 0)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)

        GLConstants.checkGLError("CompositeBlendPass.render")
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

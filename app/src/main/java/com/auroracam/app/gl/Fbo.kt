package com.auroracam.app.gl

import android.opengl.GLES30
import android.util.Log

class Fbo(
    val width: Int,
    val height: Int,
    val useHalfFloat: Boolean = false
) {
    companion object {
        private const val TAG = "AuroraFbo"
    }

    var fboId: Int = 0
        private set
    var textureId: Int = 0
        private set
    var isHalfFloat: Boolean = false
        private set

    init {
        val fbos = IntArray(1)
        val textures = IntArray(1)

        GLES30.glGenFramebuffers(1, fbos, 0)
        GLES30.glGenTextures(1, textures, 0)

        fboId = fbos[0]
        textureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        var complete = false
        if (useHalfFloat) {
            // GL SPEC RULE: OpenGL ES 3.0 Section 4.3.1:
            // The GL_RGBA/GL_UNSIGNED_BYTE readback guarantee applies ONLY to normalized fixed-point surfaces (GL_RGBA8).
            // Reading back from a floating-point surface (GL_RGBA16F) using GL_UNSIGNED_BYTE is not spec-guaranteed
            // and returns GL_INVALID_OPERATION (error 1282) on Adreno 619 GPUs, yielding an unpopulated black buffer (avgLuma=0.0).
            // GL_RGBA16F may ONLY be used for intermediate render targets; any FBO targeted by glReadPixels must be GL_RGBA8.
            //
            // Allocate 2D RGBA16F half-float texture for intermediate look precision
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA16F,
                width,
                height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_HALF_FLOAT,
                null
            )

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                textureId,
                0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                complete = true
                isHalfFloat = true
                Log.i(TAG, "Allocated GL_RGBA16F FBO (${width}x${height}) successfully")
            } else {
                Log.w(TAG, "GL_RGBA16F FBO incomplete (0x${Integer.toHexString(status)}), falling back to GL_RGBA8")
            }
        }

        if (!complete) {
            // Allocate standard 2D RGBA8 texture
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA8,
                width,
                height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                textureId,
                0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Framebuffer incomplete: status 0x${Integer.toHexString(status)}")
                throw RuntimeException("Framebuffer incomplete: 0x${Integer.toHexString(status)}")
            }
            isHalfFloat = false
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLConstants.checkGLError("Fbo.create (${width}x${height}, halfFloat=$isHalfFloat)")
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, width, height)
    }

    fun unbind(defaultWidth: Int, defaultHeight: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, defaultWidth, defaultHeight)
    }

    fun release() {
        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = 0
        }
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }
}

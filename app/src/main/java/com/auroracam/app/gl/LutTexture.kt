package com.auroracam.app.gl

import android.opengl.GLES30
import android.util.Log
import com.auroracam.app.gl.lut.ParsedCube

/**
 * Encapsulates an OpenGL ES 3.0 3D texture (GL_TEXTURE_3D) for color grading LUTs.
 */
class LutTexture {
    companion object {
        private const val TAG = "LutTexture"
    }

    var textureId: Int = 0
        private set

    var size: Int = 33
        private set

    var domainMin: FloatArray = floatArrayOf(0f, 0f, 0f)
        private set

    var domainMax: FloatArray = floatArrayOf(1f, 1f, 1f)
        private set

    fun init() {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)

        Log.i(TAG, "LutTexture initialized (textureId=$textureId)")
    }

    fun upload(cube: ParsedCube) {
        if (textureId == 0) init()

        size = cube.size
        domainMin = cube.domainMin.clone()
        domainMax = cube.domainMax.clone()

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureId)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGBA8,
            cube.size,
            cube.size,
            cube.size,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            cube.data
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)

        Log.i(TAG, "Uploaded 3D LUT texture: size=${cube.size}x${cube.size}x${cube.size}")
    }

    fun release() {
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }
}

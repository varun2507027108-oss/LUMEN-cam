package com.auroracam.app.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GLConstants {
    private const val TAG = "AuroraGL"

    // Fullscreen quad: 4 vertices (X, Y, U, V)
    // Coords from -1 to 1 in NDC, UV from 0 to 1
    val QUAD_VERTICES = floatArrayOf(
        // X,     Y,    U,    V
        -1.0f, -1.0f, 0.0f, 0.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f, 1.0f,
         1.0f,  1.0f, 1.0f, 1.0f
    )

    const val FLOAT_SIZE = 4
    const val VERTEX_STRIDE = 4 * FLOAT_SIZE // 2 pos + 2 tex
    const val POSITION_OFFSET = 0
    const val TEXCOORD_OFFSET = 2 * FLOAT_SIZE

    fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    fun checkGLError(op: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            val msg = "$op: glError 0x${Integer.toHexString(error)}"
            Log.w(TAG, msg)
        }
    }
}

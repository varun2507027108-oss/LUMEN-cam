package com.auroracam.app.gl

import android.opengl.GLES30
import android.util.Log

object ShaderCompiler {
    private const val TAG = "AuroraShaderCompiler"

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Error creating shader of type $type")
        }

        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            val typeStr = if (type == GLES30.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"
            Log.e(TAG, "Compilation error in $typeStr shader:\n$log\nSource:\n$shaderCode")
            throw RuntimeException("Error compiling $typeStr shader: $log")
        }

        return shader
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            throw RuntimeException("Error creating GL program")
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            Log.e(TAG, "Linking error in GL program:\n$log")
            throw RuntimeException("Error linking GL program: $log")
        }

        // Shaders are linked into program, flag for deletion when program is deleted
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        return program
    }
}

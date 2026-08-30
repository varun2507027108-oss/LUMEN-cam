package com.auroracam.app.gl.passes

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import com.auroracam.app.camera.burst.BurstAligner
import com.auroracam.app.gl.GLConstants
import com.auroracam.app.gl.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BurstMergePass {
    companion object {
        private const val TAG = "BurstMergePass"
    }

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
        precision highp float;
        precision highp sampler2DArray;

        uniform sampler2DArray uYArray;
        uniform sampler2DArray uUArray;
        uniform sampler2DArray uVArray;
        uniform sampler2DArray uMaskArray;

        uniform vec2 uOffsets[6];
        uniform int uFrameCount;
        uniform int uRefIndex;
        uniform int uChromaSoften;

        in vec2 vUv;
        out vec4 fragColor;

        vec3 yuvToRgb(float y, float u, float v) {
            // ITU-R BT.601 color matrix (matches single-encode YUV path)
            float r = y + 1.402 * v;
            float g = y - 0.344136 * u - 0.714136 * v;
            float b = y + 1.772 * u;
            return clamp(vec3(r, g, b), 0.0, 1.0);
        }

        void main() {
            vec3 accumRgb = vec3(0.0);
            float accumWeight = 0.0;
            vec3 refRgb = vec3(0.0);

            for (int i = 0; i < uFrameCount; i++) {
                vec2 shiftedUv = vUv + uOffsets[i];
                
                // Discard samples landing outside frame boundary
                if (shiftedUv.x < 0.0 || shiftedUv.x > 1.0 || shiftedUv.y < 0.0 || shiftedUv.y > 1.0) {
                    continue;
                }
                
                // Lookup continuous mask weight
                float rawMask = texture(uMaskArray, vec3(vUv, float(i))).r;
                
                // Non-linear confidence weighting: suppress ghost trails in motion zones (gamma = 2.0)
                // Reference frame is strictly pinned to 1.0 for guaranteed fallback
                float w = (i == uRefIndex) ? 1.0 : (rawMask * rawMask);

                float y = texture(uYArray, vec3(shiftedUv, float(i))).r;
                float u = texture(uUArray, vec3(shiftedUv, float(i))).r - 0.5;
                float v = texture(uVArray, vec3(shiftedUv, float(i))).r - 0.5;

                vec3 rgb = yuvToRgb(y, u, v);
                if (i == uRefIndex) {
                    refRgb = rgb;
                }

                accumRgb += rgb * w;
                accumWeight += w;
            }

            vec3 finalRgb = (accumWeight > 0.0) ? (accumRgb / accumWeight) : refRgb;
            fragColor = vec4(finalRgb, 1.0);
        }
    """.trimIndent()

    private var program = 0
    private var uYArrayLoc = -1
    private var uUArrayLoc = -1
    private var uVArrayLoc = -1
    private var uMaskArrayLoc = -1
    private var uOffsetsLoc = -1
    private var uFrameCountLoc = -1
    private var uRefIndexLoc = -1
    private var uChromaSoftenLoc = -1

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
            position(0)
        }

    init {
        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)
        uYArrayLoc = GLES30.glGetUniformLocation(program, "uYArray")
        uUArrayLoc = GLES30.glGetUniformLocation(program, "uUArray")
        uVArrayLoc = GLES30.glGetUniformLocation(program, "uVArray")
        uMaskArrayLoc = GLES30.glGetUniformLocation(program, "uMaskArray")
        uOffsetsLoc = GLES30.glGetUniformLocation(program, "uOffsets")
        uFrameCountLoc = GLES30.glGetUniformLocation(program, "uFrameCount")
        uRefIndexLoc = GLES30.glGetUniformLocation(program, "uRefIndex")
        uChromaSoftenLoc = GLES30.glGetUniformLocation(program, "uChromaSoften")
        Log.i(TAG, "BurstMergePass GLES 3.0 shader initialized successfully (program=$program)")
    }

    /**
     * Uploads N aligned frames into 2D Texture Arrays and executes GPU merge.
     */
    fun renderMerge(
        alignedResult: BurstAligner.AlignmentResult,
        width: Int,
        height: Int,
        chromaSoften: Boolean = true
    ) {
        val startMs = SystemClock.elapsedRealtime()
        val frames = alignedResult.alignedFrames
        val n = frames.size
        require(n >= 1) { "Cannot merge 0 frames" }

        val uvWidth = width / 2
        val uvHeight = height / 2
        val maskWidth = BurstAligner.TILES_X
        val maskHeight = BurstAligner.TILES_Y

        // 1. Create and allocate 4 Texture Arrays (Y, U, V, Mask)
        val texIds = IntArray(4)
        GLES30.glGenTextures(4, texIds, 0)
        val yTexArray = texIds[0]
        val uTexArray = texIds[1]
        val vTexArray = texIds[2]
        val maskTexArray = texIds[3]

        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)

        // Allocate Y Array (width x height x N)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, yTexArray)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_R8, width, height, n, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)

        // Allocate U Array (uvWidth x uvHeight x N)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, uTexArray)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_R8, uvWidth, uvHeight, n, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)

        // Allocate V Array (uvWidth x uvHeight x N)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, vTexArray)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_R8, uvWidth, uvHeight, n, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)

        // Allocate Mask Array (maskWidth x maskHeight x N) with GL_LINEAR for seamless spatial blending
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, maskTexArray)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_R8, maskWidth, maskHeight, n, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)

        // 2. Upload plane slices for each frame into respective 3D texture array layer
        val offsetsArray = FloatArray(12) // 6 * 2
        for (i in 0 until n) {
            val frame = frames[i]
            // Normalized UV offsets (-dx/width, -dy/height)
            val uOffX = -frame.dxFull / width.toFloat()
            val uOffY = -frame.dyFull / height.toFloat()
            offsetsArray[i * 2] = uOffX
            offsetsArray[i * 2 + 1] = uOffY

            // Y layer
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, yTexArray)
            frame.rawFrame.yBuffer.position(0)
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, width, height, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, frame.rawFrame.yBuffer)

            // U layer
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, uTexArray)
            frame.rawFrame.uBuffer.position(0)
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, uvWidth, uvHeight, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, frame.rawFrame.uBuffer)

            // V layer
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, vTexArray)
            frame.rawFrame.vBuffer.position(0)
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, uvWidth, uvHeight, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, frame.rawFrame.vBuffer)

            // Mask layer
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, maskTexArray)
            frame.maskBuffer.position(0)
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, maskWidth, maskHeight, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, frame.maskBuffer)
        }

        // 3. Render merge pass
        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, yTexArray)
        GLES30.glUniform1i(uYArrayLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, uTexArray)
        GLES30.glUniform1i(uUArrayLoc, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, vTexArray)
        GLES30.glUniform1i(uVArrayLoc, 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, maskTexArray)
        GLES30.glUniform1i(uMaskArrayLoc, 3)

        GLES30.glUniform2fv(uOffsetsLoc, 6, offsetsArray, 0)
        GLES30.glUniform1i(uFrameCountLoc, n)
        GLES30.glUniform1i(uRefIndexLoc, alignedResult.refIndex)
        GLES30.glUniform1i(uChromaSoftenLoc, if (chromaSoften) 1 else 0)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)

        // 4. Cleanup OpenGL texture array allocations
        GLES30.glDeleteTextures(4, texIds, 0)

        val mergeTimeMs = SystemClock.elapsedRealtime() - startMs
        Log.i(TAG, "BURST MERGE: fused $n frames (${width}x${height}) in ${mergeTimeMs}ms (refIdx=${alignedResult.refIndex})")
    }

    fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }
}

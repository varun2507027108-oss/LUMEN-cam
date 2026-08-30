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
        uniform int uDebugMode; // 0: Normal, 1: Reference, 2: Aligned Frame 0, 3: Tile Mask, 4: Temporal Heatmap

        in vec2 vUv;
        out vec4 fragColor;

        const float TEMPORAL_LOW = 0.035;
        const float TEMPORAL_HIGH = 0.12;

        vec3 yuvToRgb(float y, float u, float v) {
            // ITU-R BT.601 color matrix (matches single-encode YUV path)
            float r = y + 1.402 * v;
            float g = y - 0.344136 * u - 0.714136 * v;
            float b = y + 1.772 * u;
            return clamp(vec3(r, g, b), 0.0, 1.0);
        }

        void main() {
            vec2 texelSize = 1.0 / vec2(textureSize(uYArray, 0).xy);

            // Reference frame cross-neighborhood sampling for edge-safe temporal gating
            float refYCenter = texture(uYArray, vec3(vUv, float(uRefIndex))).r;
            float refYLeft   = texture(uYArray, vec3(vUv + vec2(-texelSize.x, 0.0), float(uRefIndex))).r;
            float refYRight  = texture(uYArray, vec3(vUv + vec2( texelSize.x, 0.0), float(uRefIndex))).r;
            float refYUp     = texture(uYArray, vec3(vUv + vec2(0.0, -texelSize.y), float(uRefIndex))).r;
            float refYDown   = texture(uYArray, vec3(vUv + vec2(0.0,  texelSize.y), float(uRefIndex))).r;

            float refU = texture(uUArray, vec3(vUv, float(uRefIndex))).r - 0.5;
            float refV = texture(uVArray, vec3(vUv, float(uRefIndex))).r - 0.5;
            vec3 refRgb = yuvToRgb(refYCenter, refU, refV);

            if (uDebugMode == 1) {
                // Diagnostic: Reference frame only
                fragColor = vec4(refRgb, 1.0);
                return;
            }

            vec3 accumRgb = vec3(0.0);
            float accumWeight = 0.0;
            float maxTemporalResidual = 0.0;
            float avgTileConfidence = 0.0;
            vec3 firstNonRefRgb = vec3(0.0);
            bool hasFirstNonRef = false;

            for (int i = 0; i < uFrameCount; i++) {
                vec2 shiftedUv = vUv + uOffsets[i];
                
                // Discard samples landing outside frame boundary
                if (shiftedUv.x < 0.0 || shiftedUv.x > 1.0 || shiftedUv.y < 0.0 || shiftedUv.y > 1.0) {
                    continue;
                }

                float yCenter = texture(uYArray, vec3(shiftedUv, float(i))).r;
                float u = texture(uUArray, vec3(shiftedUv, float(i))).r - 0.5;
                float v = texture(uVArray, vec3(shiftedUv, float(i))).r - 0.5;
                vec3 rgb = yuvToRgb(yCenter, u, v);

                if (i != uRefIndex && !hasFirstNonRef) {
                    firstNonRefRgb = rgb;
                    hasFirstNonRef = true;
                }

                float w;
                if (i == uRefIndex) {
                    // Reference frame is strictly pinned to 1.0 for guaranteed fallback
                    w = 1.0;
                } else {
                    // Existing tile-level continuous confidence (gamma = 2.0)
                    float rawMask = texture(uMaskArray, vec3(vUv, float(i))).r;
                    float tileConfidence = rawMask * rawMask;
                    avgTileConfidence += tileConfidence;

                    // 5-point cross neighborhood luminance residual check
                    float yLeft   = texture(uYArray, vec3(shiftedUv + vec2(-texelSize.x, 0.0), float(i))).r;
                    float yRight  = texture(uYArray, vec3(shiftedUv + vec2( texelSize.x, 0.0), float(i))).r;
                    float yUp     = texture(uYArray, vec3(shiftedUv + vec2(0.0, -texelSize.y), float(i))).r;
                    float yDown   = texture(uYArray, vec3(shiftedUv + vec2(0.0,  texelSize.y), float(i))).r;

                    float diffCenter = abs(yCenter - refYCenter);
                    float diffLeft   = abs(yLeft - refYLeft);
                    float diffRight  = abs(yRight - refYRight);
                    float diffUp     = abs(yUp - refYUp);
                    float diffDown   = abs(yDown - refYDown);

                    float temporalResidual = max(diffCenter, max(max(diffLeft, diffRight), max(diffUp, diffDown)));
                    maxTemporalResidual = max(maxTemporalResidual, temporalResidual);

                    float temporalConfidence = 1.0 - smoothstep(TEMPORAL_LOW, TEMPORAL_HIGH, temporalResidual);

                    // Both tests must agree before non-reference frame contributes
                    w = tileConfidence * temporalConfidence;
                }

                accumRgb += rgb * w;
                accumWeight += w;
            }

            if (uDebugMode == 2) {
                // Diagnostic: Shifted non-reference frame
                fragColor = vec4(hasFirstNonRef ? firstNonRefRgb : refRgb, 1.0);
                return;
            }

            if (uDebugMode == 3) {
                // Diagnostic: Tile confidence visualization
                float nonRefCount = max(float(uFrameCount - 1), 1.0);
                float normConfidence = clamp(avgTileConfidence / nonRefCount, 0.0, 1.0);
                fragColor = vec4(vec3(normConfidence), 1.0);
                return;
            }

            if (uDebugMode == 4) {
                // Diagnostic: Temporal residual heatmap (Green = static, Red = motion)
                float resNormalized = clamp(maxTemporalResidual / TEMPORAL_HIGH, 0.0, 1.0);
                vec3 heatmap = mix(vec3(0.0, 1.0, 0.0), vec3(1.0, 0.0, 0.0), resNormalized);
                fragColor = vec4(heatmap, 1.0);
                return;
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
    private var uDebugModeLoc = -1

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
        uDebugModeLoc = GLES30.glGetUniformLocation(program, "uDebugMode")
        Log.i(TAG, "BurstMergePass GLES 3.0 shader initialized successfully (program=$program)")
    }

    /**
     * Uploads N aligned frames into 2D Texture Arrays and executes GPU merge.
     */
    fun renderMerge(
        alignedResult: BurstAligner.AlignmentResult,
        width: Int,
        height: Int,
        chromaSoften: Boolean = true,
        debugMode: Int = 0
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
        GLES30.glUniform1i(uDebugModeLoc, debugMode)

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

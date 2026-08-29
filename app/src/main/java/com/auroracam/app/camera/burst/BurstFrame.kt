package com.auroracam.app.camera.burst

import android.media.Image
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class RawYuvFrame(
    val width: Int,
    val height: Int,
    val yBuffer: ByteBuffer,
    val uBuffer: ByteBuffer,
    val vBuffer: ByteBuffer,
    val yRowStride: Int,
    val uvRowStride: Int,
    val uvPixelStride: Int,
    val timestampNs: Long,
    val exposureTimeNs: Long,
    val iso: Int
) {
    companion object {
        private const val TAG = "BurstFrame"
        private var hasLoggedFormat = false

        /**
         * Extracts tight, contiguous direct ByteBuffers from a YUV_420_888 Image.
         * Handles rowStride != width and pixelStride in {1, 2}.
         * Caller MUST close the original image immediately after calling fromImage().
         */
        fun fromImage(
            image: Image,
            timestampNs: Long = image.timestamp,
            exposureTimeNs: Long = 0L,
            iso: Int = 0
        ): RawYuvFrame {
            val width = image.width
            val height = image.height
            val planes = image.planes

            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride

            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride

            if (!hasLoggedFormat) {
                hasLoggedFormat = true
                Log.i(TAG, "CAPTURE burst-format: width=$width, height=$height, yRowStride=$yRowStride, yPixStride=$yPixelStride, uvRowStride=$uRowStride, uvPixStride=$uPixelStride, path=${if (uPixelStride == 2) "DEINTERLEAVE" else "DIRECT"}")
            }

            // 1. Extract Y plane into contiguous direct buffer (width x height)
            val yDirect = ByteBuffer.allocateDirect(width * height).order(ByteOrder.nativeOrder())
            val yBuf = yPlane.buffer
            if (yRowStride == width) {
                val origPos = yBuf.position()
                val origLim = yBuf.limit()
                yBuf.position(0)
                yBuf.limit(width * height)
                yDirect.put(yBuf)
                yBuf.position(origPos)
                yBuf.limit(origLim)
            } else {
                // Copy row by row to discard row padding
                val rowBytes = ByteArray(width)
                for (row in 0 until height) {
                    yBuf.position(row * yRowStride)
                    yBuf.get(rowBytes, 0, width)
                    yDirect.put(rowBytes, 0, width)
                }
            }
            yDirect.rewind()

            // 2. Extract U and V planes into contiguous direct buffers (width/2 x height/2)
            val uvWidth = width / 2
            val uvHeight = height / 2
            val uDirect = ByteBuffer.allocateDirect(uvWidth * uvHeight).order(ByteOrder.nativeOrder())
            val vDirect = ByteBuffer.allocateDirect(uvWidth * uvHeight).order(ByteOrder.nativeOrder())

            val uBuf = uPlane.buffer
            val vBuf = vPlane.buffer

            if (uPixelStride == 1 && vPixelStride == 1 && uRowStride == uvWidth && vRowStride == uvWidth) {
                // Direct contiguous copy
                uBuf.position(0)
                uBuf.limit(uvWidth * uvHeight)
                uDirect.put(uBuf)

                vBuf.position(0)
                vBuf.limit(uvWidth * uvHeight)
                vDirect.put(vBuf)
            } else if (uPixelStride == 2) {
                // Semi-planar / interleaved UV (e.g. NV21 or NV12)
                val uRowBytes = ByteArray(uRowStride)
                val vRowBytes = ByteArray(vRowStride)
                val uOutRow = ByteArray(uvWidth)
                val vOutRow = ByteArray(uvWidth)
                for (row in 0 until uvHeight) {
                    uBuf.position(row * uRowStride)
                    val uLen = minOf(uRowStride, uBuf.remaining())
                    uBuf.get(uRowBytes, 0, uLen)

                    vBuf.position(row * vRowStride)
                    val vLen = minOf(vRowStride, vBuf.remaining())
                    vBuf.get(vRowBytes, 0, vLen)

                    for (col in 0 until uvWidth) {
                        uOutRow[col] = uRowBytes[col * 2]
                        vOutRow[col] = vRowBytes[col * 2]
                    }
                    uDirect.put(uOutRow, 0, uvWidth)
                    vDirect.put(vOutRow, 0, uvWidth)
                }
            } else {
                // pixelStride == 1 but rowStride != uvWidth
                val uRowBytes = ByteArray(uvWidth)
                val vRowBytes = ByteArray(uvWidth)
                for (row in 0 until uvHeight) {
                    uBuf.position(row * uRowStride)
                    uBuf.get(uRowBytes, 0, uvWidth)
                    uDirect.put(uRowBytes, 0, uvWidth)

                    vBuf.position(row * vRowStride)
                    vBuf.get(vRowBytes, 0, uvWidth)
                    vDirect.put(vRowBytes, 0, uvWidth)
                }
            }

            uDirect.rewind()
            vDirect.rewind()

            return RawYuvFrame(
                width = width,
                height = height,
                yBuffer = yDirect,
                uBuffer = uDirect,
                vBuffer = vDirect,
                yRowStride = width,
                uvRowStride = uvWidth,
                uvPixelStride = 1,
                timestampNs = timestampNs,
                exposureTimeNs = exposureTimeNs,
                iso = iso
            )
        }
    }
}

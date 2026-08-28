package com.auroracam.app.ui

import android.graphics.Bitmap
import android.graphics.Rect

enum class FormatMode(val label: String, val aspectWidth: Float, val aspectHeight: Float) {
    RATIO_4_3("4:3", 3.0f, 4.0f),
    RATIO_1_1("1:1", 1.0f, 1.0f),
    XPAN("XPAN", 24.0f, 65.0f); // 65:24 in portrait is 24:65; in landscape is 65:24

    /**
     * Calculates the centered crop rectangle for a source image of given width and height.
     */
    fun calculateCropRect(srcWidth: Int, srcHeight: Int): Rect {
        if (srcWidth <= 0 || srcHeight <= 0) return Rect(0, 0, srcWidth, srcHeight)

        val isPortrait = srcHeight >= srcWidth
        val targetAspect = if (isPortrait) {
            when (this) {
                RATIO_4_3 -> 3.0f / 4.0f
                RATIO_1_1 -> 1.0f / 1.0f
                XPAN -> 24.0f / 65.0f // Narrow vertical strip in portrait or 65:24
            }
        } else {
            when (this) {
                RATIO_4_3 -> 4.0f / 3.0f
                RATIO_1_1 -> 1.0f / 1.0f
                XPAN -> 65.0f / 24.0f // Cinematic wide in landscape
            }
        }

        val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()

        var cropW = srcWidth
        var cropH = srcHeight

        if (srcAspect > targetAspect) {
            // Source is wider than target: crop width
            cropW = (srcHeight * targetAspect).toInt().coerceIn(1, srcWidth)
        } else {
            // Source is taller than target: crop height
            cropH = (srcWidth / targetAspect).toInt().coerceIn(1, srcHeight)
        }

        val left = (srcWidth - cropW) / 2
        val top = (srcHeight - cropH) / 2

        return Rect(left, top, left + cropW, top + cropH)
    }

    /**
     * Crops a bitmap to the target format. Returns the same bitmap if no crop is needed.
     */
    fun cropBitmap(source: Bitmap): Bitmap {
        val rect = calculateCropRect(source.width, source.height)
        if (rect.left == 0 && rect.top == 0 && rect.width() == source.width && rect.height() == source.height) {
            return source
        }
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
    }
}

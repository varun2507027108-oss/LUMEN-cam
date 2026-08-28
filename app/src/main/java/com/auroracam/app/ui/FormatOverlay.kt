package com.auroracam.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Viewfinder format guides overlay (Compose only — never baked into saved captures).
 * Renders orange frame guide lines and translucent letterboxing for XPAN (65:24) and 1:1.
 */
@Composable
fun FormatOverlay(
    formatMode: FormatMode,
    modifier: Modifier = Modifier
) {
    if (formatMode == FormatMode.RATIO_4_3) return

    val guideOrange = Color(0xFFFF9800)
    val maskColor = Color(0x77000000)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val isPortrait = h >= w
        val targetAspect = if (isPortrait) {
            when (formatMode) {
                FormatMode.RATIO_4_3 -> 3.0f / 4.0f
                FormatMode.RATIO_1_1 -> 1.0f / 1.0f
                FormatMode.XPAN -> 24.0f / 65.0f
            }
        } else {
            when (formatMode) {
                FormatMode.RATIO_4_3 -> 4.0f / 3.0f
                FormatMode.RATIO_1_1 -> 1.0f / 1.0f
                FormatMode.XPAN -> 65.0f / 24.0f
            }
        }

        val screenAspect = w / h
        val activeW: Float
        val activeH: Float

        if (screenAspect > targetAspect) {
            activeH = h
            activeW = h * targetAspect
        } else {
            activeW = w
            activeH = w / targetAspect
        }

        val left = (w - activeW) / 2f
        val top = (h - activeH) / 2f
        val right = left + activeW
        val bottom = top + activeH

        // Draw translucent masks outside framing area
        if (top > 0) {
            drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(w, top))
            drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
        }
        if (left > 0) {
            drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, activeH))
            drawRect(color = maskColor, topLeft = Offset(right, top), size = Size(w - right, activeH))
        }

        // Draw framing guides
        val strokeWidth = 2.dp.toPx()
        val frameColor = if (formatMode == FormatMode.XPAN) guideOrange else Color(0xCCFFFFFF)

        drawRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(activeW, activeH),
            style = Stroke(width = strokeWidth)
        )

        // Corner accents for XPAN
        if (formatMode == FormatMode.XPAN) {
            val accentLen = 16.dp.toPx()
            val accentStroke = 3.dp.toPx()

            // Top-left
            drawLine(guideOrange, Offset(left, top), Offset(left + accentLen, top), accentStroke)
            drawLine(guideOrange, Offset(left, top), Offset(left, top + accentLen), accentStroke)
            // Top-right
            drawLine(guideOrange, Offset(right, top), Offset(right - accentLen, top), accentStroke)
            drawLine(guideOrange, Offset(right, top), Offset(right, top + accentLen), accentStroke)
            // Bottom-left
            drawLine(guideOrange, Offset(left, bottom), Offset(left + accentLen, bottom), accentStroke)
            drawLine(guideOrange, Offset(left, bottom), Offset(left, bottom - accentLen), accentStroke)
            // Bottom-right
            drawLine(guideOrange, Offset(right, bottom), Offset(right - accentLen, bottom), accentStroke)
            drawLine(guideOrange, Offset(right, bottom), Offset(right, bottom - accentLen), accentStroke)
        }
    }
}

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
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.HairlineSubtle
import com.auroracam.app.ui.theme.ParchmentWhite

/**
 * Viewfinder Optical Format Guides Overlay.
 *
 * Renders precision optical framing guides and translucent masks for XPAN (65:24) and 1:1.
 * Frame corner registration brackets match AuroraCam's geometric motif.
 */
@Composable
fun FormatOverlay(
    formatMode: FormatMode,
    modifier: Modifier = Modifier
) {
    if (formatMode == FormatMode.RATIO_4_3) return

    val maskColor = Color(0x990C0D0F) // Translucent darkroom mask

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

        // 1. Darkroom Letterbox Masks
        if (top > 0) {
            drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(w, top))
            drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
        }
        if (left > 0) {
            drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, activeH))
            drawRect(color = maskColor, topLeft = Offset(right, top), size = Size(w - right, activeH))
        }

        // 2. Optical Hairline Frame Boundary
        val strokeWidth = 1.dp.toPx()
        val frameColor = if (formatMode == FormatMode.XPAN) BurntBrass else HairlineSubtle

        drawRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(activeW, activeH),
            style = Stroke(width = strokeWidth)
        )

        // 3. Optical Corner Registration Brackets
        val bracketLen = 14.dp.toPx()
        val bracketStroke = 1.5.dp.toPx()

        // Top-Left ┌
        drawLine(frameColor, Offset(left, top), Offset(left + bracketLen, top), bracketStroke)
        drawLine(frameColor, Offset(left, top), Offset(left, top + bracketLen), bracketStroke)
        // Top-Right ┐
        drawLine(frameColor, Offset(right, top), Offset(right - bracketLen, top), bracketStroke)
        drawLine(frameColor, Offset(right, top), Offset(right, top + bracketLen), bracketStroke)
        // Bottom-Left └
        drawLine(frameColor, Offset(left, bottom), Offset(left + bracketLen, bottom), bracketStroke)
        drawLine(frameColor, Offset(left, bottom), Offset(left, bottom - bracketLen), bracketStroke)
        // Bottom-Right ┘
        drawLine(frameColor, Offset(right, bottom), Offset(right - bracketLen, bottom), bracketStroke)
        drawLine(frameColor, Offset(right, bottom), Offset(right, bottom - bracketLen), bracketStroke)
    }
}

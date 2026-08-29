package com.auroracam.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// =========================================================================
// AURORACAM PHOTOGRAPHIC CONTROL SURFACE DESIGN SYSTEM
// Precise geometry, restrained materials, and calm photographic motion.
// =========================================================================

object AuroraDesignSystem {
    // 1. Precision Boundaries & Strokes
    val StrokeHairline = 0.75.dp
    val StrokeBoundary = 1.0.dp
    val StrokeFocus = 1.5.dp
    val StrokeIndicator = 2.0.dp

    // 2. Corner Geometry (Restrained, photographic frame corners)
    val CornerNone = RoundedCornerShape(0.dp)
    val CornerSharp = RoundedCornerShape(2.dp)
    val CornerFrame = RoundedCornerShape(4.dp)
    val CornerChip = RoundedCornerShape(6.dp)
    val CornerConsole = RoundedCornerShape(14.dp)
    val CornerFull = RoundedCornerShape(100.dp)

    // 3. Dial Sizing
    val DialDiameterLarge = 72.dp
    val DialDiameterMedium = 60.dp
    val DialDiameterSmall = 48.dp

    // 4. Motion Curves (Direct, snappy, photographic tactile feedback)
    val TactileEase = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    fun <T> tactileTween(durationMillis: Int = 120) = tween<T>(
        durationMillis = durationMillis,
        easing = TactileEase
    )

    // 5. Layout Fractions (Strictly protecting camera preview dominance)
    const val MaxConsoleHeightFraction = 0.32f // 25-32% maximum
}

package com.auroracam.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// =========================================================================
// AURORA INSTRUMENT DESIGN TOKENS
// Geometry, stroke rules, and mechanical motion specs.
// =========================================================================

object AuroraInstrumentTokens {
    // Hairline & Structural Strokes
    val StrokeHairline = 0.75.dp
    val StrokeBoundary = 1.0.dp
    val StrokeRegistration = 1.5.dp
    val StrokeIndexNeedle = 2.0.dp

    // Instrument Corners (Milled corners, minimal radius, no giant bubbles)
    val CornerNone = RoundedCornerShape(0.dp)
    val CornerMicro = RoundedCornerShape(2.dp)
    val CornerInstrument = RoundedCornerShape(4.dp)
    val CornerPlate = RoundedCornerShape(6.dp)
    val CornerDeck = RoundedCornerShape(12.dp)

    // Mechanical Motion Curves (Direct, responsive, 120-180ms)
    val MechanicalEase = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    fun <T> mechanicalTween(durationMillis: Int = 140) = tween<T>(
        durationMillis = durationMillis,
        easing = MechanicalEase
    )

    // Viewfinder Overlays & Heights
    val TopStripHeight = 36.dp
    val ShutterDeckHeight = 84.dp
    val CreativeRailCollapsedHeight = 44.dp
    val CreativeRailMaxHeightFraction = 0.35f
}

package com.auroracam.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =========================================================================
// AURORACAM PHOTOGRAPHIC TYPOGRAPHY HIERARCHY
// 1. Large: Current Value (Monospace Tabular Numerals)
// 2. Medium: Parameter / Category (Clean Humanist Sans)
// 3. Tiny: Low-Emphasis Metadata
// =========================================================================

// Numeric Values (Strictly Monospaced)
val PhotoValueLarge = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    letterSpacing = 0.5.sp,
    color = WarmSilver
)

val PhotoValueMedium = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    letterSpacing = 0.6.sp,
    color = WarmSilver
)

val PhotoValueSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 0.4.sp,
    color = WarmSilver
)

// Parameter & Category Labels (Clean Sans / Humanist)
val PhotoLabelPrimary = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 0.8.sp,
    color = WarmSilver
)

val PhotoLabelSecondary = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 10.5.sp,
    letterSpacing = 0.6.sp,
    color = Ash
)

val PhotoLabelMicro = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 9.sp,
    letterSpacing = 0.4.sp,
    color = MutedText
)

// Legacy / Compatibility Typographic Mappings
val InstrumentTelemetry = PhotoValueSmall
val InstrumentTelemetryLarge = PhotoValueMedium
val InstrumentLabel = PhotoLabelSecondary
val InstrumentLabelMicro = PhotoLabelMicro
val InstrumentTitle = PhotoLabelPrimary

val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = WarmSilver
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
        color = WarmSilver
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp,
        color = Ash
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
        color = Ash
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.4.sp,
        color = MutedText
    )
)

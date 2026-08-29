package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.camera.FlashMode
import com.auroracam.app.ui.model.ContextualTelemetryMode
import com.auroracam.app.ui.theme.AuroraInstrumentTokens
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.HairlineSubtle
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.OpticalGreen
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.SmokedScrim
import com.auroracam.app.ui.theme.SurfaceRecess
import com.auroracam.app.ui.theme.TextDisabled
import com.auroracam.app.ui.theme.WarmSlate

/**
 * Aurora Contextual Instrument Telemetry Strip.
 *
 * Replaces generic pill buttons with a clean, hairline-measured instrument strip.
 *
 * 1. Left section: Adaptive Measurement Readout
 *    - Default: "24 FPS │ 1/125s │ ISO 400"
 *    - EV Drag: "EV +2.0 +1.0 ◄ +0.7 0.0 -1.0"
 *    - Manual Focus: "LENS 0.8m ───────●────── ∞"
 * 2. Right section: Optical State Registration
 *    - [ RAW ] (Highlit in Burnt Brass when raw negative stream active)
 *    - [ HDR ] (Burst stack registration)
 *    - [ PEAK ] (Focus peaking edge overlay)
 *    - [ AF / MF ] (Autofocus or Manual Focus indicator)
 */
@Composable
fun AuroraInstrumentStrip(
    currentFps: Double,
    isoValue: Int = 400,
    shutterFractionText: String = "1/125",
    evBias: Float = 0.0f,
    focusDistanceDiopters: Float = 0.0f,
    isManualFocus: Boolean = false,
    telemetryMode: ContextualTelemetryMode = ContextualTelemetryMode.DEFAULT,
    isRawSupported: Boolean = false,
    isRawEnabled: Boolean = false,
    onRawToggled: () -> Unit = {},
    isHdrEnabled: Boolean = false,
    onHdrToggled: () -> Unit = {},
    isFocusPeakingEnabled: Boolean = false,
    onFocusPeakingToggled: () -> Unit = {},
    flashMode: FlashMode = FlashMode.OFF,
    onFlashModeToggled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(SmokedScrim, AuroraInstrumentTokens.CornerInstrument)
            .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerInstrument)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Left: Contextual Measurement Readout
            AnimatedContent(
                targetState = telemetryMode,
                transitionSpec = {
                    fadeIn(tween(100)) togetherWith fadeOut(tween(80))
                },
                label = "TelemetryReadoutAnim",
                modifier = Modifier.weight(1f, fill = false)
            ) { mode ->
                when (mode) {
                    ContextualTelemetryMode.EV_ADJUST -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "EV",
                                color = BurntBrass,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (evBias >= 0f) "+${"%.1f".format(evBias)}" else "%.1f".format(evBias),
                                color = ParchmentWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│ +2.0 ··· ◄ ··· -2.0",
                                color = MutedText,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    ContextualTelemetryMode.FOCUS_ADJUST -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LENS",
                                color = OpticalGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            val distText = if (focusDistanceDiopters <= 0.05f) "∞ INF" else "${"%.2f".format(focusDistanceDiopters)} DPT"
                            Text(
                                text = distText,
                                color = ParchmentWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│ 0.0 DPT ──●── 10.0 DPT",
                                color = MutedText,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    ContextualTelemetryMode.ISO_ADJUST -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ISO",
                                color = BurntBrass,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "$isoValue",
                                color = ParchmentWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│ 100 · 200 · 400 · 800 · 1600",
                                color = MutedText,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    ContextualTelemetryMode.DEFAULT -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // FPS
                            Text(
                                text = "${"%.0f".format(currentFps)}FPS",
                                color = WarmSlate,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│",
                                color = HairlineBorder,
                                fontSize = 10.sp
                            )
                            // Shutter speed
                            Text(
                                text = "${shutterFractionText}s",
                                color = ParchmentWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│",
                                color = HairlineBorder,
                                fontSize = 10.sp
                            )
                            // ISO
                            Text(
                                text = "ISO $isoValue",
                                color = WarmSlate,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            if (evBias != 0.0f) {
                                Text(
                                    text = "│",
                                    color = HairlineBorder,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = if (evBias > 0) "+${"%.1f".format(evBias)}" else "%.1f".format(evBias),
                                    color = BurntBrass,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // 2. Right: Optical Registration State Indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // [ FLASH ] (Contextual: only shows icon/mode)
                val flashLabel = when (flashMode) {
                    FlashMode.OFF -> "FLASH OFF"
                    FlashMode.AUTO -> "FLASH AUTO"
                    FlashMode.ON -> "FLASH ON"
                    FlashMode.TORCH -> "TORCH"
                }
                val isFlashActive = flashMode != FlashMode.OFF
                Box(
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                            onFlashModeToggled()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = flashLabel,
                        color = if (isFlashActive) BurntBrass else TextDisabled,
                        fontSize = 9.5.sp,
                        fontWeight = if (isFlashActive) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // [ RAW ]
                if (isRawSupported) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onRawToggled()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isRawEnabled) "RAW" else "JPG",
                            color = if (isRawEnabled) BurntBrass else TextDisabled,
                            fontSize = 9.5.sp,
                            fontWeight = if (isRawEnabled) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // [ HDR ]
                Box(
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onHdrToggled()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HDR",
                        color = if (isHdrEnabled) BurntBrass else TextDisabled,
                        fontSize = 9.5.sp,
                        fontWeight = if (isHdrEnabled) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // [ PEAK ]
                Box(
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onFocusPeakingToggled()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PEAK",
                        color = if (isFocusPeakingEnabled) OpticalGreen else TextDisabled,
                        fontSize = 9.5.sp,
                        fontWeight = if (isFocusPeakingEnabled) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // [ AF / MF ]
                Text(
                    text = if (isManualFocus) "MF" else "AF",
                    color = if (isManualFocus) OpticalGreen else WarmSlate,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

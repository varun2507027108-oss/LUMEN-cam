package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.camera.FlashMode
import com.auroracam.app.ui.BlendMode
import com.auroracam.app.ui.CameraMode
import com.auroracam.app.ui.FormatMode
import com.auroracam.app.ui.theme.Ash
import com.auroracam.app.ui.theme.AuroraBrass
import com.auroracam.app.ui.theme.DarkGraphite
import com.auroracam.app.ui.theme.FocusGreen
import com.auroracam.app.ui.theme.GraphiteSurface
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.HairlineSubtle
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.SmokedScrim
import com.auroracam.app.ui.theme.WarmSilver
import java.util.Locale
import kotlin.math.abs

/**
 * AuroraAdjustPanel — Compact Floating Creative Adjustment & Pro Control Panel.
 *
 * Provides responsive, tactile, lag-free photographic parameter controls with:
 * - Quick 1-tap preset option chips per parameter
 * - Hardware-accelerated precision horizontal sliders with - / + steppers
 * - Parameter selector bar to focus on single parameters or view all simultaneously
 * - Clean tactile Pro controls (Manual Focus, RAW, HDR burst, 16-Bit FBO, Format, Flash)
 */
@Composable
fun AuroraAdjustPanel(
    isOpen: Boolean,
    onClose: () -> Unit,
    cameraMode: CameraMode,
    activeLutName: String,
    // Look Uniforms
    lookIntensity: Float,
    onLookIntensityChanged: (Float) -> Unit,
    lookGrain: Float,
    onLookGrainChanged: (Float) -> Unit,
    lookHalation: Float,
    onLookHalationChanged: (Float) -> Unit,
    lookVignette: Float,
    onLookVignetteChanged: (Float) -> Unit,
    onResetUniforms: () -> Unit,
    // Creative Effect Uniforms
    temporalEchoDecay: Float,
    onTemporalEchoDecayChanged: (Float) -> Unit,
    motionThreshold: Float,
    onMotionThresholdChanged: (Float) -> Unit,
    lightTrailDecay: Float,
    onLightTrailDecayChanged: (Float) -> Unit,
    doubleExposureOpacity: Float,
    onDoubleExposureOpacityChanged: (Float) -> Unit,
    doubleExposureBlendMode: BlendMode,
    onDoubleExposureBlendModeChanged: (BlendMode) -> Unit,
    // Pro Controls
    isManualFocus: Boolean,
    onManualFocusToggled: (Boolean) -> Unit,
    focusDistanceDiopters: Float,
    onFocusDistanceChanged: (Float) -> Unit,
    isFocusPeakingEnabled: Boolean,
    onFocusPeakingToggled: () -> Unit,
    isRawEnabled: Boolean,
    onRawToggled: () -> Unit,
    isSimpleHdrEnabled: Boolean = false,
    onSimpleHdrToggled: () -> Unit = {},
    isHdrStackEnabled: Boolean = false,
    onHdrStackToggled: () -> Unit = {},
    is16BitFboEnabled: Boolean,
    on16BitFboToggled: () -> Unit,
    formatMode: FormatMode,
    onFormatModeChanged: (FormatMode) -> Unit,
    flashMode: FlashMode,
    onFlashModeChanged: (FlashMode) -> Unit,
    onOpenLabConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Look/Effect Parameters, 1 = Pro Controls
    var activeLookParamIndex by remember { mutableIntStateOf(0) } // 0 = Intensity, 1 = Grain, 2 = Halation, 3 = Vignette, 4 = All

    if (isOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.50f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )
    }

    AnimatedVisibility(
        visible = isOpen,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(SmokedScrim)
                .border(0.5.dp, HairlineSubtle, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .navigationBarsPadding()
        ) {
            // ==========================================
            // HEADER & MAIN TAB SWITCHER
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Segmented Tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkGraphite)
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tab1Title = if (cameraMode == CameraMode.STANDARD) "LOOK PARAMETERS" else "EFFECT CONTROLS"
                    Text(
                        text = tab1Title,
                        color = if (selectedTab == 0) WarmSilver else Ash,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selectedTab == 0) GraphiteSurface else Color.Transparent)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedTab = 0
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )

                    Text(
                        text = "PRO CONTROLS",
                        color = if (selectedTab == 1) WarmSilver else Ash,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selectedTab == 1) GraphiteSurface else Color.Transparent)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedTab = 1
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // Close Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(DarkGraphite)
                        .border(0.75.dp, HairlineBorder, CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = WarmSilver,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // TAB 0: LOOK / EFFECT PARAMETERS
            // ==========================================
            if (selectedTab == 0) {
                if (cameraMode == CameraMode.STANDARD) {
                    // Look Parameters: Category Selector Bar
                    val paramTabs = listOf("INTENSITY", "GRAIN", "HALATION", "VIGNETTE", "ALL")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        paramTabs.forEachIndexed { idx, title ->
                            val isSel = activeLookParamIndex == idx
                            Text(
                                text = title,
                                color = if (isSel) AuroraBrass else Ash,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) GraphiteSurface else DarkGraphite)
                                    .border(0.75.dp, if (isSel) AuroraBrass else HairlineBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        activeLookParamIndex = idx
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Parameter Controls Container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. INTENSITY
                        if (activeLookParamIndex == 0 || activeLookParamIndex == 4) {
                            AuroraTactileParamSlider(
                                label = "Intensity",
                                value = lookIntensity,
                                onValueChange = onLookIntensityChanged,
                                valueRange = 0.0f..2.0f,
                                defaultValue = 1.0f,
                                options = listOf(
                                    "0% OFF" to 0.0f,
                                    "50% MILD" to 0.50f,
                                    "100% STD" to 1.00f,
                                    "150% RICH" to 1.50f,
                                    "200% MAX" to 2.00f
                                ),
                                displayFormatter = { String.format(Locale.US, "%.0f%%", it * 100f) },
                                stepIncrement = 0.05f,
                                accentColor = AuroraBrass
                            )
                        }

                        // 2. FILM GRAIN
                        if (activeLookParamIndex == 1 || activeLookParamIndex == 4) {
                            AuroraTactileParamSlider(
                                label = "Film Grain",
                                value = lookGrain,
                                onValueChange = onLookGrainChanged,
                                valueRange = 0.0f..0.15f,
                                defaultValue = 0.035f,
                                options = listOf(
                                    "OFF" to 0.0f,
                                    "ISO 100" to 0.015f,
                                    "ISO 400" to 0.040f,
                                    "ISO 1600" to 0.080f,
                                    "HEAVY" to 0.140f
                                ),
                                displayFormatter = {
                                    when {
                                        it <= 0.005f -> "OFF"
                                        it < 0.025f -> "ISO 100"
                                        it < 0.06f -> "ISO 400"
                                        it < 0.10f -> "ISO 1600"
                                        else -> "ISO 3200+"
                                    }
                                },
                                stepIncrement = 0.005f,
                                accentColor = WarmSilver
                            )
                        }

                        // 3. HALATION GLOW
                        if (activeLookParamIndex == 2 || activeLookParamIndex == 4) {
                            AuroraTactileParamSlider(
                                label = "Halation Glow",
                                value = lookHalation,
                                onValueChange = onLookHalationChanged,
                                valueRange = 0.0f..0.80f,
                                defaultValue = 0.20f,
                                options = listOf(
                                    "OFF" to 0.0f,
                                    "MILD 15%" to 0.15f,
                                    "WARM 30%" to 0.30f,
                                    "GLOW 50%" to 0.50f,
                                    "VINTAGE 75%" to 0.75f
                                ),
                                displayFormatter = {
                                    if (it <= 0.01f) "OFF" else String.format(Locale.US, "%.0f%%", it * 100f)
                                },
                                stepIncrement = 0.02f,
                                accentColor = AuroraBrass
                            )
                        }

                        // 4. VIGNETTE
                        if (activeLookParamIndex == 3 || activeLookParamIndex == 4) {
                            AuroraTactileParamSlider(
                                label = "Vignette",
                                value = lookVignette,
                                onValueChange = onLookVignetteChanged,
                                valueRange = 0.0f..0.60f,
                                defaultValue = 0.15f,
                                options = listOf(
                                    "OFF" to 0.0f,
                                    "SOFT 10%" to 0.10f,
                                    "MED 25%" to 0.25f,
                                    "HEAVY 45%" to 0.45f
                                ),
                                displayFormatter = {
                                    if (it <= 0.01f) "OFF" else String.format(Locale.US, "%.0f%%", it * 100f)
                                },
                                stepIncrement = 0.02f,
                                accentColor = WarmSilver
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Reset All Look Parameters to Defaults
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGraphite)
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onResetUniforms()
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reset",
                                tint = AuroraBrass,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "RESET $activeLutName TO FACTORY DEFAULTS",
                                color = WarmSilver,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Creative Modes Effect Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (cameraMode) {
                            CameraMode.TEMPORAL_ECHO -> {
                                AuroraTactileParamSlider(
                                    label = "Echo Decay Rate",
                                    value = temporalEchoDecay,
                                    onValueChange = onTemporalEchoDecayChanged,
                                    valueRange = 0.10f..0.98f,
                                    defaultValue = 0.75f,
                                    options = listOf(
                                        "SHORT" to 0.50f,
                                        "NORMAL" to 0.75f,
                                        "DREAMY" to 0.90f,
                                        "ENDLESS" to 0.98f
                                    ),
                                    displayFormatter = { String.format(Locale.US, "%.2f", it) },
                                    stepIncrement = 0.02f,
                                    accentColor = AuroraBrass
                                )
                            }
                            CameraMode.MOTION_EXPOSURE -> {
                                AuroraTactileParamSlider(
                                    label = "Motion Sensitivity Threshold",
                                    value = motionThreshold,
                                    onValueChange = onMotionThresholdChanged,
                                    valueRange = 0.01f..0.30f,
                                    defaultValue = 0.08f,
                                    options = listOf(
                                        "HIGH SENS" to 0.03f,
                                        "BALANCED" to 0.08f,
                                        "SUBTLE" to 0.15f,
                                        "GHOST ONLY" to 0.25f
                                    ),
                                    displayFormatter = { String.format(Locale.US, "%.2f", it) },
                                    stepIncrement = 0.01f,
                                    accentColor = AuroraBrass
                                )
                            }
                            CameraMode.LIGHT_TRAILS -> {
                                AuroraTactileParamSlider(
                                    label = "Trail Accumulation Decay",
                                    value = lightTrailDecay,
                                    onValueChange = onLightTrailDecayChanged,
                                    valueRange = 0.50f..0.99f,
                                    defaultValue = 0.94f,
                                    options = listOf(
                                        "FAST" to 0.80f,
                                        "SMOOTH" to 0.90f,
                                        "LONG" to 0.95f,
                                        "INFINITE" to 0.99f
                                    ),
                                    displayFormatter = { String.format(Locale.US, "%.2f", it) },
                                    stepIncrement = 0.01f,
                                    accentColor = AuroraBrass
                                )
                            }
                            CameraMode.DOUBLE_EXPOSURE -> {
                                AuroraTactileParamSlider(
                                    label = "Layer 2 Blend Opacity",
                                    value = doubleExposureOpacity,
                                    onValueChange = onDoubleExposureOpacityChanged,
                                    valueRange = 0.0f..1.0f,
                                    defaultValue = 1.0f,
                                    options = listOf(
                                        "25%" to 0.25f,
                                        "50%" to 0.50f,
                                        "75%" to 0.75f,
                                        "100%" to 1.00f
                                    ),
                                    displayFormatter = { String.format(Locale.US, "%.0f%%", it * 100f) },
                                    stepIncrement = 0.05f,
                                    accentColor = AuroraBrass
                                )

                                // Blend Mode Chips
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkGraphite)
                                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "EXPOSURE BLEND MODE",
                                        color = Ash,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        BlendMode.values().forEach { mode ->
                                            val isSel = doubleExposureBlendMode == mode
                                            Text(
                                                text = mode.name,
                                                color = if (isSel) AuroraBrass else Ash,
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) GraphiteSurface else DarkGraphite)
                                                    .border(0.5.dp, if (isSel) AuroraBrass else HairlineBorder, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        onDoubleExposureBlendModeChanged(mode)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            } else {
                // ==========================================
                // TAB 1: PRO PHOTOGRAPHIC CONTROLS
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Focus Mode (AF | MF | PEAKING)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FOCUS CONTROL",
                            color = Ash,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGraphite)
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "AUTO (AF)",
                                color = if (!isManualFocus && !isFocusPeakingEnabled) WarmSilver else Ash,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (!isManualFocus && !isFocusPeakingEnabled) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!isManualFocus && !isFocusPeakingEnabled) GraphiteSurface else Color.Transparent)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onManualFocusToggled(false)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            Text(
                                text = "MANUAL (MF)",
                                color = if (isManualFocus) AuroraBrass else Ash,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isManualFocus) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isManualFocus) GraphiteSurface else Color.Transparent)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onManualFocusToggled(true)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            Text(
                                text = "PEAKING",
                                color = if (isFocusPeakingEnabled) FocusGreen else Ash,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isFocusPeakingEnabled) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isFocusPeakingEnabled) GraphiteSurface else Color.Transparent)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onFocusPeakingToggled()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // If Manual Focus active, show focus distance slider with presets
                    if (isManualFocus) {
                        AuroraTactileParamSlider(
                            label = "Manual Focus Distance",
                            value = focusDistanceDiopters,
                            onValueChange = onFocusDistanceChanged,
                            valueRange = 0.0f..10.0f,
                            defaultValue = 0.0f,
                            options = listOf(
                                "INF ∞" to 0.0f,
                                "3.0m" to 0.33f,
                                "1.5m" to 0.67f,
                                "0.5m" to 2.0f,
                                "MACRO" to 8.0f
                            ),
                            displayFormatter = {
                                if (it <= 0.05f) "INFINITY (∞)"
                                else String.format(Locale.US, "%.1f D (%.2fm)", it, 1.0f / it)
                            },
                            stepIncrement = 0.2f,
                            accentColor = AuroraBrass
                        )
                    }

                    // Capture Pipeline (RAW | HDR | 16-BIT)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CAPTURE PIPELINE",
                            color = Ash,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            // RAW Toggle
                            Text(
                                text = "RAW DNG",
                                color = if (isRawEnabled) AuroraBrass else Ash,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isRawEnabled) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isRawEnabled) GraphiteSurface else DarkGraphite)
                                    .border(0.5.dp, if (isRawEnabled) AuroraBrass else HairlineBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onRawToggled()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )

                            // Simple Single-Shot HDR Toggle
                            Text(
                                text = "HDR",
                                color = if (isSimpleHdrEnabled) AuroraBrass else Ash,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSimpleHdrEnabled) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSimpleHdrEnabled) GraphiteSurface else DarkGraphite)
                                    .border(0.5.dp, if (isSimpleHdrEnabled) AuroraBrass else HairlineBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onSimpleHdrToggled()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )

                            // Multi-Frame Burst Stack HDR Toggle
                            Text(
                                text = "HDR STACK",
                                color = if (isHdrStackEnabled) AuroraBrass else Ash,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isHdrStackEnabled) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isHdrStackEnabled) GraphiteSurface else DarkGraphite)
                                    .border(0.5.dp, if (isHdrStackEnabled) AuroraBrass else HairlineBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onHdrStackToggled()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )

                            // 16-Bit FBO Toggle
                            Text(
                                text = "16-BIT FBO",
                                color = if (is16BitFboEnabled) AuroraBrass else Ash,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (is16BitFboEnabled) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (is16BitFboEnabled) GraphiteSurface else DarkGraphite)
                                    .border(0.5.dp, if (is16BitFboEnabled) AuroraBrass else HairlineBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        on16BitFboToggled()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Aspect Ratio / Frame Crop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FRAME RATIO",
                            color = Ash,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGraphite)
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            FormatMode.values().forEach { mode ->
                                val isSel = formatMode == mode
                                Text(
                                    text = mode.label,
                                    color = if (isSel) WarmSilver else Ash,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) GraphiteSurface else Color.Transparent)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onFormatModeChanged(mode)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Flash Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FLASH / ILLUMINATION",
                            color = Ash,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGraphite)
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            FlashMode.values().forEach { fMode ->
                                val isSel = flashMode == fMode
                                Text(
                                    text = fMode.name,
                                    color = if (isSel) AuroraBrass else Ash,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) GraphiteSurface else Color.Transparent)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onFlashModeChanged(fMode)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Lab Diagnostics Link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkGraphite)
                            .border(0.75.dp, HairlineBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onOpenLabConsole()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Science,
                            contentDescription = "Lab Console",
                            tint = AuroraBrass,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OPEN ENGINEERING LAB CONSOLE",
                            color = WarmSilver,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * AuroraTactileParamSlider — High-performance, tactile photographic parameter control.
 *
 * Features:
 * - Header with parameter name, formatted active readout, and default reset button
 * - Quick 1-tap option / preset chips for instant setting
 * - Precision hardware-accelerated slider with - / + single-tap steppers
 * - Zero touch collision, generous hitboxes, and tactile feedback
 */
@Composable
fun AuroraTactileParamSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    options: List<Pair<String, Float>>,
    displayFormatter: (Float) -> String,
    stepIncrement: Float = (valueRange.endInclusive - valueRange.start) / 20f,
    accentColor: Color = AuroraBrass,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkGraphite)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // 1. Header: Name, Formatted Value, and Reset Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label.uppercase(Locale.US),
                    color = Ash,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Value Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GraphiteSurface)
                        .border(0.5.dp, HairlineBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = displayFormatter(value),
                        color = accentColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Reset Button if value differs from default
                if (abs(value - defaultValue) > 0.001f) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset to Default",
                        tint = Ash,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onValueChange(defaultValue)
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Preset Options Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            options.forEach { (optLabel, optValue) ->
                val isClose = abs(value - optValue) <= (stepIncrement * 0.75f)
                Text(
                    text = optLabel,
                    color = if (isClose) accentColor else MutedText,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isClose) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isClose) GraphiteSurface else Color.Black.copy(alpha = 0.35f))
                        .border(0.5.dp, if (isClose) accentColor.copy(alpha = 0.7f) else HairlineBorder, RoundedCornerShape(5.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onValueChange(optValue)
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Precision Slider + Steppers Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Minus Stepper
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(GraphiteSurface)
                    .border(0.5.dp, HairlineBorder, CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val next = (value - stepIncrement).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease",
                    tint = WarmSilver,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Hardware-accelerated Smooth Slider
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { newVal ->
                    onValueChange(newVal)
                },
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = HairlineBorder
                ),
                modifier = Modifier.weight(1f)
            )

            // Plus Stepper
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(GraphiteSurface)
                    .border(0.5.dp, HairlineBorder, CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val next = (value + stepIncrement).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase",
                    tint = WarmSilver,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

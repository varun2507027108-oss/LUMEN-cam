package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.components.DoubleExposureSurface
import com.auroracam.app.ui.components.InstrumentLinearScale
import com.auroracam.app.ui.components.LightTrailSurface
import com.auroracam.app.ui.components.MotionExposureSurface
import com.auroracam.app.ui.components.TemporalEchoSurface
import com.auroracam.app.ui.theme.AuroraInstrumentTokens
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.BurntBrassDim
import com.auroracam.app.ui.theme.Graphite
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.Obsidian
import com.auroracam.app.ui.theme.OpticalGreen
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.SurfaceRaised
import com.auroracam.app.ui.theme.SurfaceRecess
import com.auroracam.app.ui.theme.TextDisabled
import com.auroracam.app.ui.theme.WarmSlate
import java.io.File

enum class ShelfTab(val title: String) {
    MODES("MODES"),
    LOOKS("LOOKS"),
    EFFECTS("EFFECTS"),
    PRO("PRO")
}

enum class ProSubSection(val title: String) {
    FOCUS("FOCUS"),
    CAPTURE("CAPTURE"),
    FRAME("FRAME"),
    LAB("LAB")
}

/**
 * Aurora Control Rail — Compact Bottom Sheet.
 *
 * Constrained height (<= 35% screen height) so live camera viewfinder remains dominant.
 * Features 4 clean instrument tabs: MODES, LOOKS, EFFECTS, PRO.
 */
@Composable
fun CreativeDrawer(
    cameraMode: CameraMode,
    onModeChanged: (CameraMode) -> Unit,
    // Look & Grading
    isLookEnabled: Boolean,
    onLookEnabledChanged: (Boolean) -> Unit,
    activeLutName: String,
    onSelectPreset: (String) -> Unit,
    onPickLutFile: () -> Unit,
    onGenerateDebugLuts: () -> Unit,
    availableLuts: List<File>,
    onSelectCachedLut: (File) -> Unit,
    onResetToDefaultLut: () -> Unit,
    lookIntensity: Float,
    onLookIntensityChanged: (Float) -> Unit,
    lookHalation: Float,
    onLookHalationChanged: (Float) -> Unit,
    lookGrain: Float = 0.04f,
    onLookGrainChanged: (Float) -> Unit = {},
    lookVignette: Float = 0.12f,
    onLookVignetteChanged: (Float) -> Unit = {},
    onResetLookUniforms: () -> Unit = {},
    // Focus & Peaking Controls
    isManualFocus: Boolean = false,
    onManualFocusToggled: (Boolean) -> Unit = {},
    focusDistanceDiopters: Float = 0.0f,
    onFocusDistanceChanged: (Float) -> Unit = {},
    isFocusPeakingEnabled: Boolean = false,
    onFocusPeakingToggled: () -> Unit = {},
    focusPeakingSensitivity: Float = 0.20f,
    onFocusPeakingSensitivityChanged: (Float) -> Unit = {},
    focusPeakingColorIndex: Int = 0,
    onFocusPeakingColorIndexChanged: (Int) -> Unit = {},
    // Effects & Creative Controls
    temporalEchoDecay: Float = 0.75f,
    onTemporalEchoDecayChanged: (Float) -> Unit = {},
    motionThreshold: Float = 0.08f,
    onMotionThresholdChanged: (Float) -> Unit = {},
    lightTrailDecay: Float = 0.94f,
    onLightTrailDecayChanged: (Float) -> Unit = {},
    lightTrailBlendMode: Int = 0,
    onLightTrailBlendModeChanged: (Int) -> Unit = {},
    chromaticAberration: Float = 0.0f,
    onChromaticAberrationChanged: (Float) -> Unit = {},
    onClearLightTrails: () -> Unit = {},
    currentWheelParam: WheelParameter = WheelParameter.LOOK_INTENSITY,
    onSelectWheelParam: (WheelParameter) -> Unit = {},
    // Exposure & Format
    evBias: Float,
    onEvBiasChanged: (Float) -> Unit,
    currentFormat: FormatMode,
    onFormatChanged: (FormatMode) -> Unit,
    // Advanced Toggles
    isBurstStack: Boolean,
    onBurstStackToggled: () -> Unit,
    isRawSupported: Boolean = false,
    isRawEnabled: Boolean = false,
    onRawToggled: () -> Unit = {},
    isLegacyJpeg: Boolean,
    onLegacyJpegToggled: () -> Unit,
    isLookPrecision16f: Boolean,
    onLookPrecision16fToggled: () -> Unit,
    isPreviewBufferHd: Boolean,
    onPreviewBufferHdToggled: () -> Unit,
    showGpuOverlay: Boolean = false,
    onShowGpuOverlayToggled: () -> Unit = {},
    onCaptureContactSheet7Up: () -> Unit = {},
    onOpenLabConsole: () -> Unit = {},
    // Double Exposure
    dxStage: DxStage = DxStage.STAGE_1_EMPTY,
    dxBlendMode: BlendMode = BlendMode.SCREEN,
    onBlendModeSelected: (BlendMode) -> Unit = {},
    onCaptureFirstExposure: () -> Unit = {},
    onResetDoubleExposure: () -> Unit = {},
    dxOpacity: Float = 1.0f,
    onOpacityChanged: (Float) -> Unit = {},
    dxIsFlipped: Boolean = false,
    onFlipToggled: () -> Unit = {},
    onRetakeClicked: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var currentTab by remember { mutableStateOf(ShelfTab.MODES) }
    var proSubSection by remember { mutableStateOf(ProSubSection.FOCUS) }
    var showCustomLutMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .navigationBarsPadding()
            .clip(AuroraInstrumentTokens.CornerDeck)
            .background(Obsidian)
            .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerDeck)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // 1. Drag Handle & Dismiss Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .clip(AuroraInstrumentTokens.CornerMicro)
                    .background(HairlineBorder)
                    .clickable { onClose() }
            )
        }

        // 2. Monochromatic Segmented Tab Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShelfTab.entries.forEach { tab ->
                val isSelected = (currentTab == tab)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                            currentTab = tab
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = tab.title,
                        color = if (isSelected) ParchmentWhite else MutedText,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(1.5.dp)
                            .background(if (isSelected) BurntBrass else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }

        // 3. Tab Body Content
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                fadeIn(tween(100)) togetherWith fadeOut(tween(80))
            },
            label = "DrawerTabContentAnim",
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        ) { tab ->
            when (tab) {
                ShelfTab.MODES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CameraMode.entries.forEach { mode ->
                            val isSelected = (cameraMode == mode)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) SurfaceRaised else Graphite,
                                        AuroraInstrumentTokens.CornerInstrument
                                    )
                                    .border(
                                        AuroraInstrumentTokens.StrokeHairline,
                                        if (isSelected) BurntBrass else HairlineBorder,
                                        AuroraInstrumentTokens.CornerInstrument
                                    )
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onModeChanged(mode)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.title.uppercase(),
                                    color = if (isSelected) ParchmentWhite else WarmSlate,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (isSelected) {
                                    Text(
                                        text = "● ACTIVE",
                                        color = BurntBrass,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                ShelfTab.LOOKS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InstrumentLinearScale(
                            label = "LOOK INTENSITY",
                            value = lookIntensity,
                            onValueChanged = onLookIntensityChanged,
                            minVal = 0.0f,
                            maxVal = 1.0f,
                            unitSuffix = "%",
                            displayMultiplier = 100f
                        )
                        InstrumentLinearScale(
                            label = "FILM GRAIN DENSITY",
                            value = lookGrain,
                            onValueChanged = onLookGrainChanged,
                            minVal = 0.0f,
                            maxVal = 0.20f,
                            unitSuffix = "%",
                            displayMultiplier = 500f
                        )
                        InstrumentLinearScale(
                            label = "RADIAL VIGNETTE",
                            value = lookVignette,
                            onValueChanged = onLookVignetteChanged,
                            minVal = 0.0f,
                            maxVal = 0.50f,
                            unitSuffix = "%",
                            displayMultiplier = 200f
                        )
                        InstrumentLinearScale(
                            label = "OPTICAL HALATION GLOW",
                            value = lookHalation,
                            onValueChanged = onLookHalationChanged,
                            minVal = 0.0f,
                            maxVal = 0.80f,
                            unitSuffix = "%",
                            displayMultiplier = 125f
                        )

                        // Custom LUT & Reset Uniforms actions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onPickLutFile()
                                    }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Import .CUBE LUT",
                                    tint = BurntBrass,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "IMPORT .CUBE",
                                    color = BurntBrass,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = "[ RESET DEFAULTS ]",
                                color = WarmSlate,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onResetLookUniforms()
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                ShelfTab.EFFECTS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (cameraMode) {
                            CameraMode.TEMPORAL_ECHO -> {
                                TemporalEchoSurface(
                                    decay = temporalEchoDecay,
                                    onDecayChanged = onTemporalEchoDecayChanged
                                )
                            }
                            CameraMode.MOTION_EXPOSURE -> {
                                MotionExposureSurface(
                                    threshold = motionThreshold,
                                    onThresholdChanged = onMotionThresholdChanged
                                )
                            }
                            CameraMode.DOUBLE_EXPOSURE -> {
                                DoubleExposureSurface(
                                    dxStage = dxStage,
                                    dxBlendMode = dxBlendMode,
                                    onBlendModeSelected = onBlendModeSelected,
                                    dxOpacity = dxOpacity,
                                    onOpacityChanged = onOpacityChanged,
                                    onResetDx = onResetDoubleExposure
                                )
                            }
                            CameraMode.LIGHT_TRAILS -> {
                                LightTrailSurface(
                                    decay = lightTrailDecay,
                                    onDecayChanged = onLightTrailDecayChanged,
                                    blendMode = lightTrailBlendMode,
                                    onBlendModeChanged = onLightTrailBlendModeChanged,
                                    onClearTrails = onClearLightTrails
                                )
                            }
                            CameraMode.STANDARD -> {
                                Text(
                                    text = "STANDARD DIRECT PASS (NO MULTI-FRAME EFFECT ACTIVE)",
                                    color = WarmSlate,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }

                        InstrumentLinearScale(
                            label = "OPTICAL CHROMATIC ABERRATION",
                            value = chromaticAberration,
                            onValueChanged = onChromaticAberrationChanged,
                            minVal = 0.0f,
                            maxVal = 0.015f,
                            unitSuffix = "%",
                            displayMultiplier = 6666f
                        )
                    }
                }

                ShelfTab.PRO -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sub-selector: FOCUS | CAPTURE | FRAME | LAB
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceRecess, AuroraInstrumentTokens.CornerMicro)
                                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerMicro)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProSubSection.entries.forEach { section ->
                                val isSelected = (proSubSection == section)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isSelected) SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent, AuroraInstrumentTokens.CornerMicro)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                                            if (section == ProSubSection.LAB) {
                                                onOpenLabConsole()
                                            } else {
                                                proSubSection = section
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = section.title,
                                        color = if (isSelected) BurntBrass else WarmSlate,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        when (proSubSection) {
                            ProSubSection.FOCUS -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "FOCUS MODE", color = WarmSlate, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        Text(
                                            text = if (isManualFocus) "[ MANUAL FOCUS ]" else "[ AUTOFOCUS ]",
                                            color = if (isManualFocus) OpticalGreen else ParchmentWhite,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onManualFocusToggled(!isManualFocus)
                                            }
                                        )
                                    }
                                    if (isManualFocus) {
                                        InstrumentLinearScale(
                                            label = "MANUAL DIOPTER DISTANCE",
                                            value = focusDistanceDiopters,
                                            onValueChanged = onFocusDistanceChanged,
                                            minVal = 0.0f,
                                            maxVal = 10.0f,
                                            unitSuffix = " DPT",
                                            displayMultiplier = 1f
                                        )
                                    }
                                    InstrumentLinearScale(
                                        label = "PEAKING SENSITIVITY",
                                        value = focusPeakingSensitivity,
                                        onValueChanged = onFocusPeakingSensitivityChanged,
                                        minVal = 0.05f,
                                        maxVal = 0.60f,
                                        unitSuffix = "%",
                                        displayMultiplier = 166f
                                    )
                                }
                            }

                            ProSubSection.CAPTURE -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ProSettingRow(
                                        title = "SENSOR RAW (DNG STREAM)",
                                        subtitle = "Dual RAW_SENSOR DNG negative capture",
                                        enabled = isRawEnabled,
                                        onToggle = onRawToggled
                                    )
                                    ProSettingRow(
                                        title = "MULTI-FRAME HDR STACKING",
                                        subtitle = "4-frame low-noise exposure merge",
                                        enabled = isBurstStack,
                                        onToggle = onBurstStackToggled
                                    )
                                }
                            }

                            ProSubSection.FRAME -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FormatMode.entries.forEach { format ->
                                        val isSelected = (currentFormat == format)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (isSelected) SurfaceRaised else Graphite, AuroraInstrumentTokens.CornerInstrument)
                                                .border(
                                                    AuroraInstrumentTokens.StrokeHairline,
                                                    if (isSelected) BurntBrass else HairlineBorder,
                                                    AuroraInstrumentTokens.CornerInstrument
                                                )
                                                .clickable {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    onFormatChanged(format)
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = format.label,
                                                color = if (isSelected) BurntBrass else WarmSlate,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }

                            ProSubSection.LAB -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceRaised, AuroraInstrumentTokens.CornerMicro)
                                        .border(AuroraInstrumentTokens.StrokeHairline, BurntBrass, AuroraInstrumentTokens.CornerMicro)
                                        .clickable { onOpenLabConsole() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "LAUNCH ENGINEERING LAB CONSOLE →",
                                        color = BurntBrass,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProSettingRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Graphite, AuroraInstrumentTokens.CornerMicro)
            .border(
                AuroraInstrumentTokens.StrokeHairline,
                if (enabled) BurntBrass else HairlineBorder,
                AuroraInstrumentTokens.CornerMicro
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggle()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = title, color = ParchmentWhite, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(text = subtitle, color = WarmSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        Text(
            text = if (enabled) "[ ON ]" else "[ OFF ]",
            color = if (enabled) BurntBrass else MutedText,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

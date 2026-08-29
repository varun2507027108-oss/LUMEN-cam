package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.foundation.verticalScroll
import com.auroracam.app.capture.LutManager
import com.auroracam.app.ui.theme.BorderHairline
import com.auroracam.app.ui.theme.NeutralSlate
import com.auroracam.app.ui.theme.PureWhite
import com.auroracam.app.ui.theme.SurfaceActiveCard
import com.auroracam.app.ui.theme.SurfaceDark
import com.auroracam.app.ui.theme.SurfaceElevated
import com.auroracam.app.ui.theme.TextMuted
import com.auroracam.app.ui.theme.WarmAmber
import com.auroracam.app.ui.theme.WarmAmberDim
import java.io.File

enum class ShelfTab(val title: String) {
    MODES("MODES"),
    LOOKS("LOOKS"),
    EFFECTS("EFFECTS"),
    PRO("PRO")
}

/**
 * Leica / Hasselblad Compact Creative Controls Bottom Sheet.
 *
 * 1. Height constrained to <= 35% screen height (~280dp) so viewfinder remains open for framing.
 * 2. Single minimal drag handle at the top + tap outside to dismiss (no floating yellow X).
 * 3. Monochromatic segmented tab strip with animated Warm Amber indicator underline.
 * 4. Monochromatic mode selection cards with clean active state (SurfaceActiveCard + 1.dp WarmAmber border).
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
    isLegacyJpeg: Boolean,
    onLegacyJpegToggled: () -> Unit,
    isLookPrecision16f: Boolean,
    onLookPrecision16fToggled: () -> Unit,
    isPreviewBufferHd: Boolean,
    onPreviewBufferHdToggled: () -> Unit,
    showGpuOverlay: Boolean = false,
    onShowGpuOverlayToggled: () -> Unit = {},
    onCaptureContactSheet7Up: () -> Unit = {},
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
    var showCustomLutMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 285.dp)
            .navigationBarsPadding()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(SurfaceDark.copy(alpha = 0.96f))
            .border(1.dp, BorderHairline, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Minimal Dismiss Drag Handle
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NeutralSlate.copy(alpha = 0.35f))
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Sleek Segmented Tab Navigation Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShelfTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            currentTab = tab
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) PureWhite else NeutralSlate
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isSelected) WarmAmber else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Content Area
        when (currentTab) {
            ShelfTab.MODES -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Unified Monochromatic Mode Selection Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(CameraMode.STANDARD, "Standard", Icons.Default.CameraAlt),
                            Triple(CameraMode.TEMPORAL_ECHO, "Temporal", Icons.Default.History),
                            Triple(CameraMode.MOTION_EXPOSURE, "Motion", Icons.AutoMirrored.Filled.DirectionsRun),
                            Triple(CameraMode.DOUBLE_EXPOSURE, "Double Exp", Icons.Default.Layers),
                            Triple(CameraMode.LIGHT_TRAILS, "Light Trails", Icons.Default.Flare)
                        ).forEach { (mode, title, icon) ->
                            val isSelected = cameraMode == mode
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) SurfaceActiveCard else SurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) WarmAmber else BorderHairline,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onModeChanged(mode)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) WarmAmber else NeutralSlate,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) PureWhite else NeutralSlate
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
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Presets Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // RAW / Natural chip
                        FilterChip(
                            selected = !isLookEnabled,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onLookEnabledChanged(false)
                            },
                            label = { Text("RAW / Natural", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmAmber,
                                selectedLabelColor = SurfaceDark,
                                containerColor = SurfaceElevated,
                                labelColor = NeutralSlate
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = !isLookEnabled,
                                borderColor = BorderHairline,
                                selectedBorderColor = WarmAmber
                            ),
                            modifier = Modifier.height(28.dp)
                        )

                        // Built-in procedural presets
                        LutManager.BUILTIN_PRESETS.forEach { preset ->
                            val isSelected = isLookEnabled && activeLutName == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    if (!isLookEnabled) onLookEnabledChanged(true)
                                    onSelectPreset(preset)
                                },
                                label = {
                                    Text(
                                        preset,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WarmAmber,
                                    selectedLabelColor = SurfaceDark,
                                    containerColor = SurfaceElevated,
                                    labelColor = PureWhite
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = BorderHairline,
                                    selectedBorderColor = WarmAmber
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        // .CUBE File Picker
                        Box {
                            val isCustomActive = isLookEnabled && !LutManager.BUILTIN_PRESETS.contains(activeLutName)
                            FilterChip(
                                selected = isCustomActive,
                                onClick = { showCustomLutMenu = true },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.FileUpload,
                                            contentDescription = "Import",
                                            tint = if (isCustomActive) SurfaceDark else WarmAmber,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            if (isCustomActive) activeLutName else ".CUBE",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WarmAmber,
                                    selectedLabelColor = SurfaceDark,
                                    containerColor = SurfaceElevated,
                                    labelColor = WarmAmber
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isCustomActive,
                                    borderColor = BorderHairline,
                                    selectedBorderColor = WarmAmber
                                ),
                                modifier = Modifier.height(28.dp)
                            )

                            DropdownMenu(
                                expanded = showCustomLutMenu,
                                onDismissRequest = { showCustomLutMenu = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import .cube file...", color = PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = WarmAmber) },
                                    onClick = {
                                        onPickLutFile()
                                        showCustomLutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Generate test LUTs (Mono, Invert)", color = PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = PureWhite) },
                                    onClick = {
                                        onGenerateDebugLuts()
                                        showCustomLutMenu = false
                                    }
                                )
                                if (availableLuts.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Cached LUTs:", color = NeutralSlate, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                        onClick = {},
                                        enabled = false
                                    )
                                    availableLuts.forEach { file ->
                                        DropdownMenuItem(
                                            text = { Text(file.nameWithoutExtension, color = PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = WarmAmber) },
                                            onClick = {
                                                onSelectCachedLut(file)
                                                showCustomLutMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Look Profile Header & Reset Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OPTICAL PROFILE: ${if (isLookEnabled) activeLutName.uppercase() else "RAW"}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = NeutralSlate
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onResetLookUniforms()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Profile",
                                tint = WarmAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "RESET",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = WarmAmber
                            )
                        }
                    }

                    // Look Sliders: Mix, Glow, Grain, Vignette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mix ${(lookIntensity * 100).toInt()}%",
                            color = NeutralSlate,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(72.dp)
                        )
                        Slider(
                            value = lookIntensity,
                            onValueChange = onLookIntensityChanged,
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = WarmAmber,
                                activeTrackColor = WarmAmber,
                                inactiveTrackColor = SurfaceElevated
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Glow ${(lookHalation * 100).toInt()}%",
                            color = NeutralSlate,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(72.dp)
                        )
                        Slider(
                            value = lookHalation,
                            onValueChange = onLookHalationChanged,
                            valueRange = 0.0f..0.60f,
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = WarmAmber,
                                activeTrackColor = WarmAmber,
                                inactiveTrackColor = SurfaceElevated
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grain ${(lookGrain * 100).toInt()}%",
                            color = NeutralSlate,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(72.dp)
                        )
                        Slider(
                            value = lookGrain,
                            onValueChange = onLookGrainChanged,
                            valueRange = 0.0f..0.20f,
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = WarmAmber,
                                activeTrackColor = WarmAmber,
                                inactiveTrackColor = SurfaceElevated
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vignette ${(lookVignette * 100).toInt()}%",
                            color = NeutralSlate,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(72.dp)
                        )
                        Slider(
                            value = lookVignette,
                            onValueChange = onLookVignetteChanged,
                            valueRange = 0.0f..0.50f,
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = WarmAmber,
                                activeTrackColor = WarmAmber,
                                inactiveTrackColor = SurfaceElevated
                            )
                        )
                    }
                }
            }

            ShelfTab.EFFECTS -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val currentParamValue = when (currentWheelParam) {
                        WheelParameter.ECHO_DECAY -> temporalEchoDecay
                        WheelParameter.MOTION_THRESHOLD -> motionThreshold
                        WheelParameter.LIGHT_DECAY -> lightTrailDecay
                        WheelParameter.CHROMATIC_ABERRATION -> chromaticAberration
                        WheelParameter.LOOK_INTENSITY -> lookIntensity
                        WheelParameter.HALATION_GLOW -> lookHalation
                        WheelParameter.GRAIN -> lookGrain
                        WheelParameter.VIGNETTE -> lookVignette
                    }

                    ParameterWheel(
                        currentParam = currentWheelParam,
                        paramValue = currentParamValue,
                        onParamChanged = { param, value ->
                            when (param) {
                                WheelParameter.ECHO_DECAY -> onTemporalEchoDecayChanged(value)
                                WheelParameter.MOTION_THRESHOLD -> onMotionThresholdChanged(value)
                                WheelParameter.LIGHT_DECAY -> onLightTrailDecayChanged(value)
                                WheelParameter.CHROMATIC_ABERRATION -> onChromaticAberrationChanged(value)
                                WheelParameter.LOOK_INTENSITY -> onLookIntensityChanged(value)
                                WheelParameter.HALATION_GLOW -> onLookHalationChanged(value)
                                WheelParameter.GRAIN -> onLookGrainChanged(value)
                                WheelParameter.VIGNETTE -> onLookVignetteChanged(value)
                            }
                        },
                        onSelectParam = onSelectWheelParam
                    )

                    // Contextual Double Exposure controls
                    if (cameraMode == CameraMode.DOUBLE_EXPOSURE) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BlendMode.values().forEach { mode ->
                                val isSelected = dxBlendMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onBlendModeSelected(mode) },
                                    label = { Text(mode.label, fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = WarmAmber,
                                        selectedLabelColor = SurfaceDark,
                                        containerColor = SurfaceElevated,
                                        labelColor = PureWhite
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = BorderHairline,
                                        selectedBorderColor = WarmAmber
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }

                    // Contextual Light Trails controls
                    if (cameraMode == CameraMode.LIGHT_TRAILS) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(0 to "MAX", 1 to "ADD", 2 to "SCREEN").forEach { (modeId, label) ->
                                val isSelected = lightTrailBlendMode == modeId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onLightTrailBlendModeChanged(modeId) },
                                    label = { Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = WarmAmber,
                                        selectedLabelColor = SurfaceDark,
                                        containerColor = SurfaceElevated,
                                        labelColor = PureWhite
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = BorderHairline,
                                        selectedBorderColor = WarmAmber
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                            }

                            FilterChip(
                                selected = false,
                                onClick = onClearLightTrails,
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Reset", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = WarmAmberDim,
                                    labelColor = WarmAmber
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = WarmAmber.copy(alpha = 0.4f),
                                    selectedBorderColor = WarmAmber
                                ),
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                }
            }

            ShelfTab.PRO -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Aspect Ratio formats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FORMAT", color = NeutralSlate, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(64.dp))
                        FormatMode.values().forEach { mode ->
                            val isSelected = currentFormat == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onFormatChanged(mode) },
                                label = { Text(mode.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WarmAmber,
                                    selectedLabelColor = SurfaceDark,
                                    containerColor = SurfaceElevated,
                                    labelColor = PureWhite
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = BorderHairline,
                                    selectedBorderColor = WarmAmber
                                ),
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }

                    // Architecture Toggles Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isLookPrecision16f,
                            onClick = onLookPrecision16fToggled,
                            label = { Text(if (isLookPrecision16f) "16-Bit Float FBO" else "8-Bit RGBA", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmAmber,
                                selectedLabelColor = SurfaceDark,
                                containerColor = SurfaceElevated,
                                labelColor = PureWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isLookPrecision16f,
                                borderColor = BorderHairline,
                                selectedBorderColor = WarmAmber
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        FilterChip(
                            selected = isPreviewBufferHd,
                            onClick = onPreviewBufferHdToggled,
                            label = { Text(if (isPreviewBufferHd) "HD Viewfinder" else "Standard Viewfinder", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmAmber,
                                selectedLabelColor = SurfaceDark,
                                containerColor = SurfaceElevated,
                                labelColor = PureWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isPreviewBufferHd,
                                borderColor = BorderHairline,
                                selectedBorderColor = WarmAmber
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        FilterChip(
                            selected = showGpuOverlay,
                            onClick = onShowGpuOverlayToggled,
                            label = { Text(if (showGpuOverlay) "GPU HUD ON" else "GPU HUD OFF", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmAmber,
                                selectedLabelColor = SurfaceDark,
                                containerColor = SurfaceElevated,
                                labelColor = PureWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = showGpuOverlay,
                                borderColor = BorderHairline,
                                selectedBorderColor = WarmAmber
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        FilterChip(
                            selected = isLegacyJpeg,
                            onClick = onLegacyJpegToggled,
                            label = { Text(if (isLegacyJpeg) "JPEG Stream" else "Direct YUV420", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmAmber,
                                selectedLabelColor = SurfaceDark,
                                containerColor = SurfaceElevated,
                                labelColor = PureWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isLegacyJpeg,
                                borderColor = BorderHairline,
                                selectedBorderColor = WarmAmber
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }

                    // Developer & Color Science Tools Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DEV TOOLS", color = NeutralSlate, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(64.dp))
                        FilterChip(
                            selected = false,
                            onClick = onCaptureContactSheet7Up,
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("7-UP CONTACT SHEET", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WarmAmber)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceElevated,
                                labelColor = WarmAmber
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = WarmAmber.copy(alpha = 0.5f),
                                selectedBorderColor = WarmAmber
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }
        }
    }
}

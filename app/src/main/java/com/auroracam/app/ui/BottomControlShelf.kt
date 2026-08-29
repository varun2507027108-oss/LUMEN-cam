package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.auroracam.app.capture.LutManager
import com.auroracam.app.ui.theme.AmberGold
import com.auroracam.app.ui.theme.AmberGoldDim
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.ElevatedSurface
import com.auroracam.app.ui.theme.SlateBorder
import com.auroracam.app.ui.theme.TextMuted
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White
import java.io.File

enum class ShelfTab(val title: String, val accentColor: Color) {
    MODES("MODES", com.auroracam.app.ui.theme.SolarGold),
    LOOKS("LOOKS", com.auroracam.app.ui.theme.LookWarmth),
    EFFECTS("EFFECTS", com.auroracam.app.ui.theme.OpticCyan),
    PRO("PRO", com.auroracam.app.ui.theme.FocusMint)
}

/**
 * Minimalist Cinema Control Drawer.
 *
 * Elegantly groups Camera Modes (Standard, Temporal, Motion, Double Exp, Light Trails),
 * Film LUT Color Grading, Rotary Effect Tuning, and Pro Architecture Options
 * into an ergonomic panel positioned safely above Android system gestures.
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
            .navigationBarsPadding()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(DarkSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Drawer Header with Grab Handle & Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(currentTab.accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CREATIVE CONTROLS",
                    color = White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            // Close button
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ElevatedSurface)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Drawer",
                    tint = White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Tab Navigation Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ElevatedSurface)
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShelfTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) tab.accentColor else Color.Transparent)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            currentTab = tab
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) DarkBackground else TextSecondary
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
                    // Creative Camera Modes Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(CameraMode.STANDARD, "Standard", Pair(Icons.Default.CameraAlt, com.auroracam.app.ui.theme.HyperSilver)),
                            Triple(CameraMode.TEMPORAL_ECHO, "Temporal", Pair(Icons.Default.History, com.auroracam.app.ui.theme.LookNostalgia)),
                            Triple(CameraMode.MOTION_EXPOSURE, "Motion", Pair(Icons.AutoMirrored.Filled.DirectionsRun, com.auroracam.app.ui.theme.FocusMint)),
                            Triple(CameraMode.DOUBLE_EXPOSURE, "Double Exp", Pair(Icons.Default.Layers, com.auroracam.app.ui.theme.OpticCyan)),
                            Triple(CameraMode.LIGHT_TRAILS, "Light Trails", Pair(Icons.Default.Flare, com.auroracam.app.ui.theme.SolarOrange))
                        ).forEach { (mode, title, iconAndColor) ->
                            val (icon, modeColor) = iconAndColor
                            val isSelected = cameraMode == mode
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) modeColor else ElevatedSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) modeColor else SlateBorder,
                                        RoundedCornerShape(14.dp)
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
                                    tint = if (isSelected) DarkBackground else modeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DarkBackground else White
                                )
                            }
                        }
                    }
                }
            }

            ShelfTab.LOOKS -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Presets Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Natural / Off chip
                        FilterChip(
                            selected = !isLookEnabled,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onLookEnabledChanged(false)
                            },
                            label = { Text("RAW / Natural", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = DarkBackground,
                                containerColor = ElevatedSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = !isLookEnabled,
                                borderColor = SlateBorder,
                                selectedBorderColor = AmberGold
                            ),
                            modifier = Modifier.height(30.dp)
                        )

                        // Built-in presets
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
                                    selectedContainerColor = AmberGold,
                                    selectedLabelColor = DarkBackground,
                                    containerColor = ElevatedSurface,
                                    labelColor = White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = SlateBorder,
                                    selectedBorderColor = AmberGold
                                ),
                                modifier = Modifier.height(30.dp)
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
                                            tint = if (isCustomActive) DarkBackground else AmberGold,
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
                                    selectedContainerColor = AmberGold,
                                    selectedLabelColor = DarkBackground,
                                    containerColor = ElevatedSurface,
                                    labelColor = AmberGold
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isCustomActive,
                                    borderColor = SlateBorder,
                                    selectedBorderColor = AmberGold
                                ),
                                modifier = Modifier.height(30.dp)
                            )

                            DropdownMenu(
                                expanded = showCustomLutMenu,
                                onDismissRequest = { showCustomLutMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import .cube file...") },
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AmberGold) },
                                    onClick = {
                                        onPickLutFile()
                                        showCustomLutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Generate test LUTs (Mono, Invert)") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = White) },
                                    onClick = {
                                        onGenerateDebugLuts()
                                        showCustomLutMenu = false
                                    }
                                )
                                if (availableLuts.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Cached LUTs:", color = TextSecondary, fontSize = 11.sp) },
                                        onClick = {},
                                        enabled = false
                                    )
                                    availableLuts.forEach { file ->
                                        DropdownMenuItem(
                                            text = { Text(file.nameWithoutExtension) },
                                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = AmberGold) },
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

                    // Look Intensity & Halation Sliders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mix ${(lookIntensity * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(68.dp)
                        )
                        Slider(
                            value = lookIntensity,
                            onValueChange = onLookIntensityChanged,
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = AmberGold,
                                activeTrackColor = AmberGold,
                                inactiveTrackColor = ElevatedSurface
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Glow ${(lookHalation * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(68.dp)
                        )
                        Slider(
                            value = lookHalation,
                            onValueChange = onLookHalationChanged,
                            valueRange = 0.0f..0.60f,
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = AmberGold,
                                activeTrackColor = AmberGold,
                                inactiveTrackColor = ElevatedSurface
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
                            }
                        },
                        onSelectParam = onSelectWheelParam
                    )

                    // Contextual Double Exposure controls
                    if (cameraMode == CameraMode.DOUBLE_EXPOSURE) {
                        Spacer(modifier = Modifier.height(6.dp))
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
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = DarkBackground,
                                        containerColor = ElevatedSurface,
                                        labelColor = White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = SlateBorder,
                                        selectedBorderColor = AmberGold
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }

                    // Contextual Light Trails controls
                    if (cameraMode == CameraMode.LIGHT_TRAILS) {
                        Spacer(modifier = Modifier.height(6.dp))
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
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = DarkBackground,
                                        containerColor = ElevatedSurface,
                                        labelColor = White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = SlateBorder,
                                        selectedBorderColor = AmberGold
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }

                            FilterChip(
                                selected = false,
                                onClick = onClearLightTrails,
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AmberGold, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Reset", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = AmberGoldDim,
                                    labelColor = AmberGold
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = AmberGold.copy(alpha = 0.4f),
                                    selectedBorderColor = AmberGold
                                ),
                                modifier = Modifier.height(28.dp)
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
                        Text("FORMAT", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(70.dp))
                        FormatMode.values().forEach { mode ->
                            val isSelected = currentFormat == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onFormatChanged(mode) },
                                label = { Text(mode.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGold,
                                    selectedLabelColor = DarkBackground,
                                    containerColor = ElevatedSurface,
                                    labelColor = White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = SlateBorder,
                                    selectedBorderColor = AmberGold
                                ),
                                modifier = Modifier.height(28.dp)
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
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = DarkBackground,
                                containerColor = ElevatedSurface,
                                labelColor = White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isLookPrecision16f,
                                borderColor = SlateBorder,
                                selectedBorderColor = AmberGold
                            ),
                            modifier = Modifier.height(28.dp)
                        )

                        FilterChip(
                            selected = isPreviewBufferHd,
                            onClick = onPreviewBufferHdToggled,
                            label = { Text(if (isPreviewBufferHd) "HD Viewfinder" else "Standard Viewfinder", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = DarkBackground,
                                containerColor = ElevatedSurface,
                                labelColor = White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isPreviewBufferHd,
                                borderColor = SlateBorder,
                                selectedBorderColor = AmberGold
                            ),
                            modifier = Modifier.height(28.dp)
                        )

                        FilterChip(
                            selected = showGpuOverlay,
                            onClick = onShowGpuOverlayToggled,
                            label = { Text(if (showGpuOverlay) "GPU HUD ON" else "GPU HUD OFF", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = DarkBackground,
                                containerColor = ElevatedSurface,
                                labelColor = White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = showGpuOverlay,
                                borderColor = SlateBorder,
                                selectedBorderColor = AmberGold
                            ),
                            modifier = Modifier.height(28.dp)
                        )

                        FilterChip(
                            selected = isLegacyJpeg,
                            onClick = onLegacyJpegToggled,
                            label = { Text(if (isLegacyJpeg) "JPEG Stream" else "Direct YUV420", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = DarkBackground,
                                containerColor = ElevatedSurface,
                                labelColor = White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isLegacyJpeg,
                                borderColor = SlateBorder,
                                selectedBorderColor = AmberGold
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.auroracam.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.capture.LutManager
import com.auroracam.app.ui.theme.AuroraAmber
import com.auroracam.app.ui.theme.AuroraCyan
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White
import java.io.File

enum class ShelfTab(val title: String) {
    LOOKS("Looks"),
    EFFECTS("Effects"),
    FINE_TUNE("Tune"),
    CAPTURE("Pro"),
    DOUBLE_EXP("Blend")
}

@Composable
fun BottomControlShelf(
    cameraMode: CameraMode,
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
    // Double Exposure
    dxStage: DxStage = DxStage.STAGE_1_EMPTY,
    dxBlendMode: BlendMode = BlendMode.SCREEN,
    onBlendModeSelected: (BlendMode) -> Unit = {},
    dxOpacity: Float = 1.0f,
    onOpacityChanged: (Float) -> Unit = {},
    dxIsFlipped: Boolean = false,
    onFlipToggled: () -> Unit = {},
    onRetakeClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(ShelfTab.LOOKS) }
    var showCustomLutMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xE6101010))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tab Navigation Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = when (cameraMode) {
                CameraMode.DOUBLE_EXPOSURE -> listOf(ShelfTab.DOUBLE_EXP, ShelfTab.LOOKS, ShelfTab.EFFECTS, ShelfTab.FINE_TUNE, ShelfTab.CAPTURE)
                CameraMode.LIGHT_TRAILS, CameraMode.TEMPORAL_ECHO, CameraMode.MOTION_EXPOSURE -> listOf(ShelfTab.EFFECTS, ShelfTab.LOOKS, ShelfTab.FINE_TUNE, ShelfTab.CAPTURE)
                else -> listOf(ShelfTab.LOOKS, ShelfTab.EFFECTS, ShelfTab.FINE_TUNE, ShelfTab.CAPTURE)
            }

            tabs.forEach { tab ->
                val isSelected = currentTab == tab
                Text(
                    text = tab.title,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) AuroraCyan else TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { currentTab = tab }
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }

        // Tab Content Area
        when (currentTab) {
            ShelfTab.LOOKS -> {
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
                        onClick = { onLookEnabledChanged(false) },
                        label = { Text("RAW / Natural", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF455A64),
                            selectedLabelColor = White,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    // Built-in presets
                    LutManager.BUILTIN_PRESETS.forEach { preset ->
                        val isSelected = isLookEnabled && activeLutName == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isLookEnabled) onLookEnabledChanged(true)
                                onSelectPreset(preset)
                            },
                            label = {
                                Text(
                                    preset,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuroraCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E1E1E),
                                labelColor = White
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
                                        tint = if (isCustomActive) Color.Black else AuroraAmber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        if (isCustomActive) activeLutName else ".CUBE",
                                        fontSize = 10.sp
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuroraAmber,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E1E1E),
                                labelColor = AuroraAmber
                            ),
                            modifier = Modifier.height(28.dp)
                        )

                        DropdownMenu(
                            expanded = showCustomLutMenu,
                            onDismissRequest = { showCustomLutMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📁 Import .cube from Storage...") },
                                onClick = {
                                    onPickLutFile()
                                    showCustomLutMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⚡ Generate Test LUTs (Mono, Invert)") },
                                onClick = {
                                    onGenerateDebugLuts()
                                    showCustomLutMenu = false
                                }
                            )
                            if (availableLuts.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Cached LUTs:") },
                                    onClick = {},
                                    enabled = false
                                )
                                availableLuts.forEach { file ->
                                    DropdownMenuItem(
                                        text = { Text("🎨 ${file.nameWithoutExtension}") },
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
            }

            ShelfTab.EFFECTS -> {
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
            }

            ShelfTab.FINE_TUNE -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Look Intensity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mix ${(lookIntensity * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.width(76.dp)
                        )
                        Slider(
                            value = lookIntensity,
                            onValueChange = onLookIntensityChanged,
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = AuroraCyan,
                                activeTrackColor = AuroraCyan,
                                inactiveTrackColor = Color(0xFF333333)
                            )
                        )
                    }

                    // Halation Glow Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Glow ${(lookHalation * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.width(76.dp)
                        )
                        Slider(
                            value = lookHalation,
                            onValueChange = onLookHalationChanged,
                            valueRange = 0.0f..0.60f,
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = AuroraAmber,
                                activeTrackColor = AuroraAmber,
                                inactiveTrackColor = Color(0xFF333333)
                            )
                        )
                    }

                    // Chromatic Aberration Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aberr ${(chromaticAberration * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.width(76.dp)
                        )
                        Slider(
                            value = chromaticAberration,
                            onValueChange = onChromaticAberrationChanged,
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF4081),
                                activeTrackColor = Color(0xFFFF4081),
                                inactiveTrackColor = Color(0xFF333333)
                            )
                        )
                    }
                }
            }

            ShelfTab.CAPTURE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // QuickStack Burst Toggle
                    FilterChip(
                        selected = isBurstStack,
                        onClick = onBurstStackToggled,
                        label = {
                            Text(
                                if (isBurstStack) "⚡ Burst Stack N=6" else "Single Capture",
                                fontSize = 10.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AuroraCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = White
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    // 16F Precision Toggle
                    FilterChip(
                        selected = isLookPrecision16f,
                        onClick = onLookPrecision16fToggled,
                        label = {
                            Text(
                                if (isLookPrecision16f) "16-Bit Float FBO" else "8-Bit RGBA",
                                fontSize = 10.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AuroraAmber,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = White
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    // Clear Light Trails Reset Action
                    if (cameraMode == CameraMode.LIGHT_TRAILS) {
                        FilterChip(
                            selected = false,
                            onClick = onClearLightTrails,
                            label = { Text("🧹 Reset Trails", fontSize = 10.sp, color = AuroraAmber) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF2A1B00),
                                labelColor = AuroraAmber
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Format Chips
                    FormatMode.values().forEach { mode ->
                        FilterChip(
                            selected = currentFormat == mode,
                            onClick = { onFormatChanged(mode) },
                            label = { Text(mode.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (mode == FormatMode.XPAN) Color(0xFFD84315) else Color(0xFF37474F),
                                selectedLabelColor = White,
                                containerColor = Color(0xFF1E1E1E),
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            ShelfTab.DOUBLE_EXP -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Blend mode chips
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
                                label = { Text(mode.label, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AuroraCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E1E1E),
                                    labelColor = White
                                ),
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }

                    // Opacity & Retake Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mix ${(dxOpacity * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.width(55.dp)
                        )
                        Slider(
                            value = dxOpacity,
                            onValueChange = onOpacityChanged,
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = AuroraCyan,
                                activeTrackColor = AuroraCyan,
                                inactiveTrackColor = Color(0xFF333333)
                            )
                        )

                        if (dxStage == DxStage.STAGE_2_LOCKED) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Retake",
                                color = AuroraAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onRetakeClicked() }
                                    .background(Color(0xFF2E2000))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


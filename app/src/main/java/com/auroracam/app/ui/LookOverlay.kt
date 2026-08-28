package com.auroracam.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.capture.LutManager
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LookOverlay(
    isLookEnabled: Boolean,
    onLookEnabledChanged: (Boolean) -> Unit,
    intensity: Float,
    onIntensityChanged: (Float) -> Unit,
    halation: Float,
    onHalationChanged: (Float) -> Unit,
    activeLutName: String,
    onSelectPreset: (String) -> Unit,
    onPickLutFile: () -> Unit,
    onGenerateDebugLuts: () -> Unit,
    availableLuts: List<File>,
    onSelectCachedLut: (File) -> Unit,
    onResetToDefaultLut: () -> Unit,
    currentFormat: FormatMode,
    onFormatChanged: (FormatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLutMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xDD141414))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: Header (Title on left, Switch on right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Signature Look",
                    tint = if (isLookEnabled) Color(0xFFFFB74D) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Signature Look",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Switch(
                checked = isLookEnabled,
                onCheckedChange = onLookEnabledChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFFB74D),
                    checkedTrackColor = Color(0xFF5D4037)
                )
            )
        }

        // Row 2: Preset Chips (Warm, Chrome, Mono, Custom) + Format Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Built-in Presets
            LutManager.BUILTIN_PRESETS.forEach { preset ->
                val isSelected = isLookEnabled && activeLutName == preset
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isLookEnabled) onLookEnabledChanged(true)
                        onSelectPreset(preset)
                    },
                    label = { Text(preset, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9800),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF222222),
                        labelColor = Color(0xFFDDDDDD)
                    )
                )
            }

            // Custom / Import .cube dropdown chip
            Box {
                val isCustomActive = isLookEnabled && !LutManager.BUILTIN_PRESETS.contains(activeLutName)
                FilterChip(
                    selected = isCustomActive,
                    onClick = { showLutMenu = true },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCustomActive) Icons.Default.Palette else Icons.Default.FileUpload,
                                contentDescription = "Import",
                                modifier = Modifier.size(13.dp),
                                tint = if (isCustomActive) Color.Black else Color(0xFFFFB74D)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isCustomActive) activeLutName else ".CUBE",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFB74D),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF2A2A2A),
                        labelColor = Color(0xFFFFB74D)
                    )
                )

                DropdownMenu(
                    expanded = showLutMenu,
                    onDismissRequest = { showLutMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("📁 Import .cube file from Storage...") },
                        onClick = {
                            onPickLutFile()
                            showLutMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("⚡ Generate Debug LUTs (mono, invert)") },
                        onClick = {
                            onGenerateDebugLuts()
                            showLutMenu = false
                        }
                    )
                    if (availableLuts.isNotEmpty()) {
                        Text(
                            text = "Cached / Test LUTs",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        availableLuts.forEach { file ->
                            DropdownMenuItem(
                                text = { Text("🎨 ${file.nameWithoutExtension}") },
                                onClick = {
                                    onSelectCachedLut(file)
                                    showLutMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Aspect Ratio Format Chips
            FormatMode.values().forEach { mode ->
                FilterChip(
                    selected = currentFormat == mode,
                    onClick = { onFormatChanged(mode) },
                    label = { Text(mode.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (mode == FormatMode.XPAN) Color(0xFFD84315) else Color(0xFF455A64),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF222222),
                        labelColor = Color(0xFFAAAAAA)
                    )
                )
            }
        }

        // Row 3: Intensity & Halation Sliders (Visible when Look is enabled)
        if (isLookEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Look ${(intensity * 100).toInt()}%",
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    modifier = Modifier.width(68.dp)
                )
                Slider(
                    value = intensity,
                    onValueChange = onIntensityChanged,
                    valueRange = 0.0f..1.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFB74D),
                        activeTrackColor = Color(0xFFFFB74D),
                        inactiveTrackColor = Color(0xFF444444)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(68.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = "Glow",
                        tint = if (halation > 0.01f) Color(0xFFFF7043) else Color.Gray,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Glow ${(halation * 100).toInt()}%",
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.sp
                    )
                }
                Slider(
                    value = halation,
                    onValueChange = onHalationChanged,
                    valueRange = 0.0f..0.60f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF7043),
                        activeTrackColor = Color(0xFFFF7043),
                        inactiveTrackColor = Color(0xFF444444)
                    )
                )
            }
        }
    }
}

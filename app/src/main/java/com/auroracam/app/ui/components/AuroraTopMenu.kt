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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.gl.lut.AuroraWarmLut
import com.auroracam.app.gl.lut.ChromeLut
import com.auroracam.app.gl.lut.FujiClassicChromeLut
import com.auroracam.app.gl.lut.HasselbladNaturalLut
import com.auroracam.app.gl.lut.KodakPortra400Lut
import com.auroracam.app.gl.lut.LeicaCharacterLut
import com.auroracam.app.gl.lut.MonoLut
import com.auroracam.app.ui.CameraMode
import com.auroracam.app.ui.theme.Ash
import com.auroracam.app.ui.theme.AuroraBrass
import com.auroracam.app.ui.theme.DarkGraphite
import com.auroracam.app.ui.theme.GraphiteSurface
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.HairlineSubtle
import com.auroracam.app.ui.theme.SmokedScrim
import com.auroracam.app.ui.theme.WarmSilver

data class LookDisplayItem(
    val presetName: String,
    val title: String,
    val subtitle: String,
    val swatchGradient: List<Color>,
    val isCustomImporter: Boolean = false
)

val AuroraLooksCatalogue = listOf(
    LookDisplayItem(
        presetName = HasselbladNaturalLut.LUT_NAME,
        title = "HASSELBLAD",
        subtitle = "Medium Format Smooth",
        swatchGradient = listOf(Color(0xFF1E2226), Color(0xFF637482), Color(0xFFE2E4DE))
    ),
    LookDisplayItem(
        presetName = LeicaCharacterLut.LUT_NAME,
        title = "LEICA M",
        subtitle = "High Microcontrast",
        swatchGradient = listOf(Color(0xFF18181A), Color(0xFF5E6068), Color(0xFFECE5D8))
    ),
    LookDisplayItem(
        presetName = KodakPortra400Lut.LUT_NAME,
        title = "PORTRA 400",
        subtitle = "Warm Pastel Skin Tone",
        swatchGradient = listOf(Color(0xFF2E241E), Color(0xFFB8825D), Color(0xFFF0DAC1))
    ),
    LookDisplayItem(
        presetName = FujiClassicChromeLut.LUT_NAME,
        title = "CLASSIC CHROME",
        subtitle = "Documentary Muted",
        swatchGradient = listOf(Color(0xFF22262B), Color(0xFF6B727A), Color(0xFFD4D0C5))
    ),
    LookDisplayItem(
        presetName = AuroraWarmLut.LUT_NAME,
        title = "GOLDEN WARM",
        subtitle = "Golden Hour Rich Halation",
        swatchGradient = listOf(Color(0xFF382414), Color(0xFFC77732), Color(0xFFFFD499))
    ),
    LookDisplayItem(
        presetName = ChromeLut.LUT_NAME,
        title = "KODACHROME",
        subtitle = "Deep Rich Saturated",
        swatchGradient = listOf(Color(0xFF2E1C1A), Color(0xFF8C342A), Color(0xFFF2C894))
    ),
    LookDisplayItem(
        presetName = MonoLut.LUT_NAME,
        title = "TRI-X MONO",
        subtitle = "Achromatic Silver Halide",
        swatchGradient = listOf(Color(0xFF101012), Color(0xFF505054), Color(0xFFF0F0F0))
    ),
    LookDisplayItem(
        presetName = "CUSTOM_IMPORT",
        title = "+ CUSTOM LUT",
        subtitle = "Import .CUBE 3D LUT",
        swatchGradient = listOf(Color(0xFF2B2518), Color(0xFF7A6432), Color(0xFFD4AF37)),
        isCustomImporter = true
    )
)

data class ModeDisplayItem(
    val mode: CameraMode,
    val title: String,
    val icon: ImageVector
)

val CameraModesCatalogue = listOf(
    ModeDisplayItem(CameraMode.STANDARD, "STANDARD", Icons.Filled.Camera),
    ModeDisplayItem(CameraMode.TEMPORAL_ECHO, "ECHO", Icons.Filled.Timeline),
    ModeDisplayItem(CameraMode.MOTION_EXPOSURE, "MOTION", Icons.Filled.Speed),
    ModeDisplayItem(CameraMode.LIGHT_TRAILS, "LIGHT TRAILS", Icons.Filled.AutoAwesome),
    ModeDisplayItem(CameraMode.DOUBLE_EXPOSURE, "DOUBLE EXP", Icons.Filled.PhotoLibrary)
)

/**
 * AuroraTopMenu — Photographic Selection Overlay Sheet.
 *
 * Provides instant tactile selection for:
 * 1. Camera Computational Modes
 * 2. Signature 3D LUT Looks (Refined compact contact sheet presentation)
 * 3. Direct Custom .cube LUT file importer
 */
@Composable
fun AuroraTopMenu(
    isOpen: Boolean,
    onClose: () -> Unit,
    activeMode: CameraMode,
    onSelectMode: (CameraMode) -> Unit,
    activeLutName: String,
    onSelectLook: (String) -> Unit,
    onImportCustomLut: () -> Unit = {},
    customLutNames: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    if (isOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )
    }

    AnimatedVisibility(
        visible = isOpen,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(SmokedScrim)
                .border(
                    0.5.dp,
                    HairlineSubtle,
                    RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Header Rail
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "AURORACAM STUDIO",
                        color = WarmSilver,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "SELECT SHOOTING MODE & FILM EMULATION",
                        color = Ash,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

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
                        contentDescription = "Close Menu",
                        tint = WarmSilver,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Photographic Modes Segmented Row
            Text(
                text = "COMPUTATIONAL MODES",
                color = Ash,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(CameraModesCatalogue) { item ->
                    val isSelected = activeMode == item.mode
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AuroraBrass.copy(alpha = 0.18f) else DarkGraphite)
                            .border(
                                0.75.dp,
                                if (isSelected) AuroraBrass else HairlineBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelectMode(item.mode)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) AuroraBrass else Ash,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = item.title,
                            color = if (isSelected) WarmSilver else Ash,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Refined Compact Looks Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SIGNATURE FILM LOOKS",
                    color = Ash,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )

                // Quick Custom Import button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkGraphite)
                        .border(0.5.dp, HairlineBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onImportCustomLut()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add LUT",
                        tint = AuroraBrass,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "+ .CUBE",
                        color = AuroraBrass,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Combine catalogue looks + any cached custom imported LUTs
            val customDisplayItems = customLutNames.map { name ->
                LookDisplayItem(
                    presetName = name,
                    title = name.take(12).uppercase(),
                    subtitle = "Custom .cube Profile",
                    swatchGradient = listOf(Color(0xFF2E241E), Color(0xFF8C643A), Color(0xFFE8D4B0))
                )
            }
            val allLooks = AuroraLooksCatalogue + customDisplayItems

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(allLooks) { look ->
                    if (look.isCustomImporter) {
                        // Custom LUT Importer Card
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGraphite.copy(alpha = 0.65f))
                                .border(
                                    0.75.dp,
                                    AuroraBrass.copy(alpha = 0.6f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onImportCustomLut()
                                }
                                .padding(horizontal = 7.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(AuroraBrass.copy(alpha = 0.15f))
                                    .border(0.5.dp, AuroraBrass, RoundedCornerShape(3.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Import",
                                    tint = AuroraBrass,
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = look.title,
                                    color = AuroraBrass,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = look.subtitle,
                                    color = Ash,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        // Regular Film Look Card
                        val isSelected = activeLutName.equals(look.presetName, ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AuroraBrass.copy(alpha = 0.15f) else DarkGraphite)
                                .border(
                                    0.75.dp,
                                    if (isSelected) AuroraBrass else HairlineBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onSelectLook(look.presetName)
                                }
                                .padding(horizontal = 7.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Swatch Gradient Block
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Brush.verticalGradient(look.swatchGradient))
                                    .border(0.5.dp, HairlineSubtle, RoundedCornerShape(3.dp))
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = look.title,
                                    color = if (isSelected) AuroraBrass else WarmSilver,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = look.subtitle,
                                    color = Ash,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

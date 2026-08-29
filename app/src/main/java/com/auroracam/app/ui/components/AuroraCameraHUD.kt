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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.camera.FlashMode
import com.auroracam.app.ui.CameraMode
import com.auroracam.app.ui.theme.Ash
import com.auroracam.app.ui.theme.AuroraBrass
import com.auroracam.app.ui.theme.DarkGraphite
import com.auroracam.app.ui.theme.GraphiteSurface
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.WarmSilver
import kotlinx.coroutines.delay
import java.util.Locale

data class ShutterSpeedEntry(
    val label: String,
    val nanos: Long, // 0 for AUTO
    val isAuto: Boolean = (nanos == 0L)
)

val ShutterSpeedOptions = listOf(
    ShutterSpeedEntry("AUTO", 0L, true),
    ShutterSpeedEntry("1/4000s", 250_000L),
    ShutterSpeedEntry("1/2000s", 500_000L),
    ShutterSpeedEntry("1/1000s", 1_000_000L),
    ShutterSpeedEntry("1/500s", 2_000_000L),
    ShutterSpeedEntry("1/250s", 4_000_000L),
    ShutterSpeedEntry("1/125s", 8_000_000L),
    ShutterSpeedEntry("1/60s", 16_666_666L),
    ShutterSpeedEntry("1/30s", 33_333_333L),
    ShutterSpeedEntry("1/15s", 66_666_666L),
    ShutterSpeedEntry("1/8s", 125_000_000L),
    ShutterSpeedEntry("1/4s", 250_000_000L),
    ShutterSpeedEntry("1/2s", 500_000_000L),
    ShutterSpeedEntry("1s", 1_000_000_000L),
    ShutterSpeedEntry("2s", 2_000_000_000L)
)

/**
 * AuroraCameraHUD — Minimal Transparent Photographic Status Line & Viewfinder Controls.
 *
 * Features:
 * - FPS | Interactive Shutter Speed Selector | Interactive EV Slider
 * - Main Viewfinder Flash Mode Switcher (OFF | AUTO | ON | TORCH)
 * - Studio Mode & Film Look trigger badge
 */
@Composable
fun AuroraCameraHUD(
    currentFps: Double,
    evBias: Float,
    onEvBiasChanged: (Float) -> Unit,
    activeMode: CameraMode,
    activeLutName: String,
    onOpenMenu: () -> Unit,
    flashMode: FlashMode = FlashMode.OFF,
    onCycleFlashMode: () -> Unit = {},
    currentShutterLabel: String = "AUTO",
    selectedShutterNanos: Long = 0L,
    onSelectShutterSpeed: (ShutterSpeedEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isEvSliderOpen by remember { mutableStateOf(false) }
    var isShutterTrayOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isEvSliderOpen) {
        if (isEvSliderOpen) {
            delay(4000)
            isEvSliderOpen = false
        }
    }

    LaunchedEffect(isShutterTrayOpen) {
        if (isShutterTrayOpen) {
            delay(6000)
            isShutterTrayOpen = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xB308090B),
                        Color(0x4008090B),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Main Transparent Telemetry & Viewfinder Control Line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Live Exposure & Telemetry Data
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // FPS Indicator
                Text(
                    text = String.format(Locale.US, "%.0f FPS", currentFps.coerceAtLeast(0.0)),
                    color = Ash,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                Text(text = "·", color = MutedText, fontSize = 11.sp)

                // Interactive Shutter Speed Selector
                val isShutterManual = selectedShutterNanos > 0L
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isShutterTrayOpen) GraphiteSurface else if (isShutterManual) DarkGraphite else Color.Transparent)
                        .border(
                            0.5.dp,
                            if (isShutterTrayOpen) AuroraBrass else if (isShutterManual) AuroraBrass.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isShutterTrayOpen = !isShutterTrayOpen
                            if (isShutterTrayOpen) isEvSliderOpen = false
                        }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isShutterManual) currentShutterLabel else "1/125s (AUTO)",
                        color = if (isShutterManual) AuroraBrass else WarmSilver,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(text = "·", color = MutedText, fontSize = 11.sp)

                // Interactive EV Readout
                Text(
                    text = String.format(Locale.US, "EV %+1.1f", evBias),
                    color = if (kotlin.math.abs(evBias) > 0.05f) AuroraBrass else WarmSilver,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isEvSliderOpen = !isEvSliderOpen
                            if (isEvSliderOpen) isShutterTrayOpen = false
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Right: Flash Toggle & Studio Menu Trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Viewfinder Flash Mode Switcher
                val (flashIcon, flashColor, flashText) = when (flashMode) {
                    FlashMode.OFF -> Triple(Icons.Filled.FlashOff, Ash, "OFF")
                    FlashMode.AUTO -> Triple(Icons.Filled.FlashAuto, WarmSilver, "AUTO")
                    FlashMode.ON -> Triple(Icons.Filled.FlashOn, AuroraBrass, "ON")
                    FlashMode.TORCH -> Triple(Icons.Filled.Highlight, AuroraBrass, "TORCH")
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkGraphite.copy(alpha = 0.85f))
                        .border(
                            0.75.dp,
                            if (flashMode != FlashMode.OFF) AuroraBrass else HairlineBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCycleFlashMode()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Flash: $flashText",
                        tint = flashColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = flashText,
                        color = flashColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Studio Top Menu Button (Mode & Look Badge)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkGraphite.copy(alpha = 0.85f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenMenu()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modeLabel = when (activeMode) {
                        CameraMode.STANDARD -> "STD"
                        CameraMode.TEMPORAL_ECHO -> "ECHO"
                        CameraMode.MOTION_EXPOSURE -> "MOT"
                        CameraMode.LIGHT_TRAILS -> "TRAIL"
                        CameraMode.DOUBLE_EXPOSURE -> "DX"
                    }
                    val lookLabel = activeLutName.replace(".cube", "").uppercase(Locale.US).take(7)

                    Text(
                        text = "$modeLabel · $lookLabel",
                        color = WarmSilver,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Open Studio Menu",
                        tint = AuroraBrass,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Shutter Speed Selector Tray
        AnimatedVisibility(
            visible = isShutterTrayOpen,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkGraphite.copy(alpha = 0.95f))
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "SHUTTER SPEED SELECTION",
                    color = Ash,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ShutterSpeedOptions.forEach { opt ->
                        val isSelected = (opt.isAuto && selectedShutterNanos == 0L) ||
                                (!opt.isAuto && selectedShutterNanos == opt.nanos)
                        Text(
                            text = opt.label,
                            color = if (isSelected) AuroraBrass else WarmSilver,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) GraphiteSurface else Color.Black.copy(alpha = 0.35f))
                                .border(
                                    0.5.dp,
                                    if (isSelected) AuroraBrass else HairlineBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onSelectShutterSpeed(opt)
                                    isShutterTrayOpen = false
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Temporary Floating EV Fine-Tuning Slider
        AnimatedVisibility(
            visible = isEvSliderOpen,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkGraphite.copy(alpha = 0.95f))
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "-2.0",
                    color = Ash,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Slider(
                    value = evBias,
                    onValueChange = onEvBiasChanged,
                    valueRange = -2.0f..2.0f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = AuroraBrass,
                        activeTrackColor = AuroraBrass,
                        inactiveTrackColor = HairlineBorder
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "+2.0",
                    color = Ash,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

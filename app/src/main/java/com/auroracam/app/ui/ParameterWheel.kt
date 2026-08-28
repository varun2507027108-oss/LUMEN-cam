package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class WheelParameter(
    val title: String,
    val unit: String,
    val defaultValue: Float,
    val icon: ImageVector
) {
    ECHO_DECAY("Echo Decay", "%", 0.75f, Icons.Default.History),
    MOTION_THRESHOLD("Motion Thresh", "", 0.08f, Icons.AutoMirrored.Filled.DirectionsRun),
    LIGHT_DECAY("Light Decay", "%", 0.94f, Icons.Default.Flare),
    CHROMATIC_ABERRATION("Aberration", "%", 0.35f, Icons.Default.BlurOn),
    LOOK_INTENSITY("Look Mix", "%", 1.0f, Icons.Default.ColorLens),
    HALATION_GLOW("Glow", "%", 0.20f, Icons.Default.WbSunny)
}

/**
 * Cinema-Grade Rotary Parameter Wheel UI.
 *
 * Provides a tactile, circular rotary dial to fine-tune GPU effect parameters
 * with normalized relative angular delta tracking (preventing 359° -> 1° wraparound jumps),
 * radial sensitivity scaling, double-tap reset, and haptic feedback.
 */
@Composable
fun ParameterWheel(
    currentParam: WheelParameter,
    paramValue: Float,
    onParamChanged: (WheelParameter, Float) -> Unit,
    onSelectParam: (WheelParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    val goldAccent = Color(0xFFFFD54F)
    val cyanAccent = Color(0xFF00E5FF)
    val density = LocalDensity.current
    val view = LocalView.current

    var isDragging by remember { mutableStateOf(false) }
    var lastAngleRad by remember { mutableDoubleStateOf(0.0) }
    var lastHapticStep by remember { mutableIntStateOf(0) }

    val activeColor = when (currentParam) {
        WheelParameter.ECHO_DECAY, WheelParameter.MOTION_THRESHOLD -> cyanAccent
        WheelParameter.LIGHT_DECAY, WheelParameter.HALATION_GLOW -> goldAccent
        WheelParameter.CHROMATIC_ABERRATION -> Color(0xFFFF4081)
        WheelParameter.LOOK_INTENSITY -> Color(0xFF69F0AE)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Quick Parameter Selector Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(WheelParameter.values()) { param ->
                val isSelected = param == currentParam
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSelectParam(param)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = param.icon,
                            contentDescription = param.title,
                            modifier = Modifier.size(12.dp),
                            tint = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)
                        )
                    },
                    label = {
                        Text(
                            text = param.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = activeColor,
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0x33222222),
                        labelColor = Color.White
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Circular Rotary Wheel
        val wheelSize = 126.dp
        val wheelRadiusPx = with(density) { (wheelSize / 2).toPx() }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(wheelSize)
                // Double tap to reset
                .pointerInput(currentParam) {
                    detectTapGestures(
                        onDoubleTap = {
                            onParamChanged(currentParam, currentParam.defaultValue)
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    )
                }
                // Continuous delta drag
                .pointerInput(currentParam, paramValue) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val center = Offset(wheelRadiusPx, wheelRadiusPx)
                            val touchX = offset.x - center.x
                            val touchY = offset.y - center.y
                            lastAngleRad = atan2(touchY.toDouble(), touchX.toDouble())
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        val center = Offset(wheelRadiusPx, wheelRadiusPx)
                        val touchX = change.position.x - center.x
                        val touchY = change.position.y - center.y

                        val curAngleRad = atan2(touchY.toDouble(), touchX.toDouble())

                        // Calculate shortest normalized angular delta
                        var deltaAngle = curAngleRad - lastAngleRad
                        if (deltaAngle > PI) deltaAngle -= 2 * PI
                        if (deltaAngle < -PI) deltaAngle += 2 * PI
                        lastAngleRad = curAngleRad

                        // Radial sensitivity scaling: finer control near center (0.4x), normal at outer rim (1.0x)
                        val dist = sqrt(touchX * touchX + touchY * touchY)
                        val radiusNorm = (dist / wheelRadiusPx).coerceIn(0.25f, 1.25f)
                        val sensitivity = (radiusNorm * 0.85f).coerceIn(0.35f, 1.0f)

                        // Convert current parameter value to 0..1 normalized domain
                        val currentNorm = when (currentParam) {
                            WheelParameter.MOTION_THRESHOLD -> ((paramValue - 0.01f) / 0.39f).coerceIn(0.0f, 1.0f)
                            WheelParameter.LIGHT_DECAY -> ((paramValue - 0.50f) / 0.49f).coerceIn(0.0f, 1.0f)
                            else -> paramValue.coerceIn(0.0f, 1.0f)
                        }

                        // Delta addition
                        val deltaNorm = (deltaAngle / (2 * PI)).toFloat() * sensitivity
                        val newNorm = (currentNorm + deltaNorm).coerceIn(0.0f, 1.0f)

                        // Convert back to parameter-specific range
                        val formattedVal = when (currentParam) {
                            WheelParameter.MOTION_THRESHOLD -> (newNorm * 0.39f + 0.01f).coerceIn(0.01f, 0.40f)
                            WheelParameter.LIGHT_DECAY -> (newNorm * 0.49f + 0.50f).coerceIn(0.50f, 0.99f)
                            else -> newNorm.coerceIn(0.0f, 1.0f)
                        }

                        // Haptic feedback tick on 5% increments
                        val step = (newNorm * 20f).toInt()
                        if (step != lastHapticStep) {
                            lastHapticStep = step
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }

                        onParamChanged(currentParam, formattedVal)
                    }
                }
        ) {
            Canvas(modifier = Modifier.size(wheelSize)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (size.minDimension / 2f) - 10.dp.toPx()
                val strokeW = 4.dp.toPx()

                // 1. Background circular track
                drawCircle(
                    color = Color(0x33FFFFFF),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeW)
                )

                // 2. Radial tick marks (24 ticks around the circle)
                for (i in 0 until 24) {
                    val tickAngle = (i * 360f / 24f) * (PI.toFloat() / 180f) - (PI.toFloat() / 2f)
                    val isMajor = i % 6 == 0
                    val innerR = radius - (if (isMajor) 7.dp.toPx() else 4.dp.toPx())
                    val outerR = radius - 1.dp.toPx()

                    val p1 = Offset(cx + innerR * cos(tickAngle), cy + innerR * sin(tickAngle))
                    val p2 = Offset(cx + outerR * cos(tickAngle), cy + outerR * sin(tickAngle))

                    drawLine(
                        color = if (isMajor) activeColor.copy(alpha = 0.8f) else Color(0x44FFFFFF),
                        start = p1,
                        end = p2,
                        strokeWidth = if (isMajor) 1.75.dp.toPx() else 1.0.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 3. Active arc representation
                val progress = when (currentParam) {
                    WheelParameter.MOTION_THRESHOLD -> ((paramValue - 0.01f) / 0.39f).coerceIn(0.0f, 1.0f)
                    WheelParameter.LIGHT_DECAY -> ((paramValue - 0.50f) / 0.49f).coerceIn(0.0f, 1.0f)
                    else -> paramValue.coerceIn(0.0f, 1.0f)
                }

                val sweepAngle = progress * 360f

                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            activeColor.copy(alpha = 0.25f),
                            activeColor,
                            activeColor
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeW + 1.dp.toPx(), cap = StrokeCap.Round)
                )

                // 4. Dial Thumb Knob at the tip of the sweep
                val thumbAngleRad = (-90f + sweepAngle) * (PI.toFloat() / 180f)
                val thumbCenter = Offset(
                    cx + radius * cos(thumbAngleRad),
                    cy + radius * sin(thumbAngleRad)
                )

                drawCircle(
                    color = Color.Black,
                    radius = 6.dp.toPx(),
                    center = thumbCenter
                )
                drawCircle(
                    color = activeColor,
                    radius = 4.5.dp.toPx(),
                    center = thumbCenter
                )
            }

            // Center Display Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xEE111111))
                    .size(68.dp)
                    .clickable {
                        onParamChanged(currentParam, currentParam.defaultValue)
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    .padding(4.dp)
            ) {
                val displayStr = when (currentParam) {
                    WheelParameter.MOTION_THRESHOLD -> "%.2f".format(paramValue)
                    WheelParameter.ECHO_DECAY, WheelParameter.LIGHT_DECAY,
                    WheelParameter.LOOK_INTENSITY, WheelParameter.CHROMATIC_ABERRATION,
                    WheelParameter.HALATION_GLOW -> "${(paramValue * 100).roundToInt()}%"
                }

                Text(
                    text = displayStr,
                    color = activeColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = currentParam.title.take(8),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

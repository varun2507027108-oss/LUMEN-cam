package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.ElevatedSurface
import com.auroracam.app.ui.theme.SlateBorder
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class WheelParameter(
    val title: String,
    val unit: String,
    val defaultValue: Float,
    val icon: ImageVector,
    val accentColor: Color = com.auroracam.app.ui.theme.WarmAmber
) {
    ECHO_DECAY("Echo Decay", "%", 0.75f, Icons.Default.History),
    MOTION_THRESHOLD("Motion Thresh", "", 0.08f, Icons.AutoMirrored.Filled.DirectionsRun),
    LIGHT_DECAY("Light Decay", "%", 0.94f, Icons.Default.Flare),
    CHROMATIC_ABERRATION("Aberration", "%", 0.35f, Icons.Default.BlurOn),
    LOOK_INTENSITY("Look Mix", "%", 1.0f, Icons.Default.ColorLens),
    HALATION_GLOW("Glow", "%", 0.20f, Icons.Default.WbSunny),
    GRAIN("Grain", "%", 0.04f, Icons.Default.Grain),
    VIGNETTE("Vignette", "%", 0.12f, Icons.Default.CameraAlt)
}

/**
 * Minimalist Cinema Rotary Parameter Wheel UI.
 *
 * Provides a tactile circular dial to fine-tune GPU effect parameters
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
    val density = LocalDensity.current
    val view = LocalView.current

    var isDragging by remember { mutableStateOf(false) }
    var lastAngleRad by remember { mutableDoubleStateOf(0.0) }
    var lastHapticStep by remember { mutableIntStateOf(0) }

    // Normalized internal value [0.0..1.0] for the dial sweep
    var activeNorm by remember(currentParam) {
        mutableFloatStateOf(
            when (currentParam) {
                WheelParameter.MOTION_THRESHOLD -> ((paramValue - 0.01f) / 0.39f).coerceIn(0.0f, 1.0f)
                WheelParameter.LIGHT_DECAY -> ((paramValue - 0.50f) / 0.49f).coerceIn(0.0f, 1.0f)
                WheelParameter.GRAIN -> (paramValue / 0.20f).coerceIn(0.0f, 1.0f)
                WheelParameter.VIGNETTE -> (paramValue / 0.50f).coerceIn(0.0f, 1.0f)
                WheelParameter.HALATION_GLOW -> (paramValue / 0.60f).coerceIn(0.0f, 1.0f)
                WheelParameter.CHROMATIC_ABERRATION -> (paramValue / 0.10f).coerceIn(0.0f, 1.0f)
                else -> paramValue.coerceIn(0.0f, 1.0f)
            }
        )
    }

    val animatedSweep by animateFloatAsState(
        targetValue = activeNorm,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 250),
        label = "wheelSweep"
    )

    val activeColor = currentParam.accentColor

    // Capture latest lambdas to avoid stale closures in gesture pointerInput
    val latestOnParamChanged by rememberUpdatedState(onParamChanged)
    val latestParamValue by rememberUpdatedState(paramValue)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Parameter Selection Horizontal Carousel
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(WheelParameter.values()) { param ->
                val isSelected = currentParam == param
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onSelectParam(param)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = param.icon,
                            contentDescription = param.title,
                            tint = if (isSelected) DarkBackground else param.accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = param.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = param.accentColor,
                        selectedLabelColor = DarkBackground,
                        containerColor = ElevatedSurface,
                        labelColor = White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = SlateBorder,
                        selectedBorderColor = param.accentColor
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Circular Rotary Wheel
        val wheelSize = 130.dp
        val wheelRadiusPx = with(density) { (wheelSize / 2).toPx() }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(wheelSize)
                .pointerInput(currentParam) {
                    detectTapGestures(
                        onDoubleTap = {
                            latestOnParamChanged(currentParam, currentParam.defaultValue)
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    )
                }
                .pointerInput(currentParam) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val center = Offset(wheelRadiusPx, wheelRadiusPx)
                            val touchX = offset.x - center.x
                            val touchY = offset.y - center.y
                            lastAngleRad = atan2(touchY.toDouble(), touchX.toDouble())
                            activeNorm = when (currentParam) {
                                WheelParameter.MOTION_THRESHOLD -> ((latestParamValue - 0.01f) / 0.39f).coerceIn(0.0f, 1.0f)
                                WheelParameter.LIGHT_DECAY -> ((latestParamValue - 0.50f) / 0.49f).coerceIn(0.0f, 1.0f)
                                WheelParameter.GRAIN -> (latestParamValue / 0.20f).coerceIn(0.0f, 1.0f)
                                WheelParameter.VIGNETTE -> (latestParamValue / 0.50f).coerceIn(0.0f, 1.0f)
                                WheelParameter.HALATION_GLOW -> (latestParamValue / 0.60f).coerceIn(0.0f, 1.0f)
                                WheelParameter.CHROMATIC_ABERRATION -> (latestParamValue / 0.10f).coerceIn(0.0f, 1.0f)
                                else -> latestParamValue.coerceIn(0.0f, 1.0f)
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        val center = Offset(wheelRadiusPx, wheelRadiusPx)
                        val touchX = change.position.x - center.x
                        val touchY = change.position.y - center.y
                        val curAngleRad = atan2(touchY.toDouble(), touchX.toDouble())

                        var deltaAngle = curAngleRad - lastAngleRad
                        if (deltaAngle > PI) deltaAngle -= 2 * PI
                        if (deltaAngle < -PI) deltaAngle += 2 * PI
                        lastAngleRad = curAngleRad

                        val deltaNorm = (deltaAngle / (2 * PI)).toFloat() * 1.15f
                        activeNorm = (activeNorm + deltaNorm).coerceIn(0.0f, 1.0f)

                        val formattedVal = when (currentParam) {
                            WheelParameter.MOTION_THRESHOLD -> (activeNorm * 0.39f + 0.01f).coerceIn(0.01f, 0.40f)
                            WheelParameter.LIGHT_DECAY -> (activeNorm * 0.49f + 0.50f).coerceIn(0.50f, 0.99f)
                            WheelParameter.GRAIN -> (activeNorm * 0.20f).coerceIn(0.0f, 0.20f)
                            WheelParameter.VIGNETTE -> (activeNorm * 0.50f).coerceIn(0.0f, 0.50f)
                            WheelParameter.HALATION_GLOW -> (activeNorm * 0.60f).coerceIn(0.0f, 0.60f)
                            WheelParameter.CHROMATIC_ABERRATION -> (activeNorm * 0.10f).coerceIn(0.0f, 0.10f)
                            else -> activeNorm.coerceIn(0.0f, 1.0f)
                        }

                        val step = (activeNorm * 20f).toInt()
                        if (step != lastHapticStep) {
                            lastHapticStep = step
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }

                        latestOnParamChanged(currentParam, formattedVal)
                    }
                }
        ) {
            Canvas(modifier = Modifier.size(wheelSize)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (size.minDimension / 2f) - 10.dp.toPx()
                val strokeW = 4.dp.toPx()

                drawCircle(Color(0x22FFFFFF), radius, Offset(cx, cy), style = Stroke(width = strokeW))

                for (i in 0 until 24) {
                    val tickAngle = (i * 360f / 24f) * (PI.toFloat() / 180f) - (PI.toFloat() / 2f)
                    val isMajor = i % 6 == 0
                    val innerR = radius - (if (isMajor) 7.dp.toPx() else 4.dp.toPx())
                    val outerR = radius - 1.dp.toPx()
                    val p1 = Offset(cx + innerR * cos(tickAngle), cy + innerR * sin(tickAngle))
                    val p2 = Offset(cx + outerR * cos(tickAngle), cy + outerR * sin(tickAngle))
                    drawLine(
                        color = if (isMajor) activeColor.copy(alpha = 0.8f) else Color(0x33FFFFFF),
                        start = p1,
                        end = p2,
                        strokeWidth = if (isMajor) 1.75.dp.toPx() else 1.0.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                val progress = when (currentParam) {
                    WheelParameter.MOTION_THRESHOLD -> ((paramValue - 0.01f) / 0.39f).coerceIn(0.0f, 1.0f)
                    WheelParameter.LIGHT_DECAY -> ((paramValue - 0.50f) / 0.49f).coerceIn(0.0f, 1.0f)
                    WheelParameter.GRAIN -> (paramValue / 0.20f).coerceIn(0.0f, 1.0f)
                    WheelParameter.VIGNETTE -> (paramValue / 0.50f).coerceIn(0.0f, 1.0f)
                    WheelParameter.HALATION_GLOW -> (paramValue / 0.60f).coerceIn(0.0f, 1.0f)
                    WheelParameter.CHROMATIC_ABERRATION -> (paramValue / 0.10f).coerceIn(0.0f, 1.0f)
                    else -> paramValue.coerceIn(0.0f, 1.0f)
                }

                val sweepAngle = progress * 360f
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(activeColor.copy(alpha = 0.25f), activeColor, activeColor),
                        center = Offset(cx, cy)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeW + 1.dp.toPx(), cap = StrokeCap.Round)
                )

                val thumbAngleRad = (-90f + sweepAngle) * (PI.toFloat() / 180f)
                val thumbCenter = Offset(cx + radius * cos(thumbAngleRad), cy + radius * sin(thumbAngleRad))
                drawCircle(Color.Black, 6.dp.toPx(), thumbCenter)
                drawCircle(activeColor, 4.5.dp.toPx(), thumbCenter)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ElevatedSurface)
                    .border(1.dp, SlateBorder, CircleShape)
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
                    WheelParameter.HALATION_GLOW, WheelParameter.GRAIN,
                    WheelParameter.VIGNETTE -> "${(paramValue * 100).roundToInt()}%"
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
                    color = TextSecondary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

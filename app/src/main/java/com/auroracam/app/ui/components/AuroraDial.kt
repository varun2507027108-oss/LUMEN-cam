package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.Ash
import com.auroracam.app.ui.theme.AuroraBrass
import com.auroracam.app.ui.theme.AuroraBrassDim
import com.auroracam.app.ui.theme.DarkGraphite
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.HairlineSubtle
import com.auroracam.app.ui.theme.WarmSilver
import kotlin.math.cos
import kotlin.math.sin

/**
 * AuroraDial — The Primary Photographic Circular Control.
 *
 * A physical camera dial translated into precision glass:
 * - Thin circular track with a single restrained brass needle indicator.
 * - Bold, tabular monospaced numeric value in the center.
 * - Parameter name label beneath.
 * - Drag vertically/circularly to adjust with tactile haptic stepping.
 * - Double-tap to reset to default.
 */
@Composable
fun AuroraDial(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    valueDisplay: String,
    modifier: Modifier = Modifier,
    defaultValue: Float = 0f,
    step: Float = 0.01f,
    diameter: Dp = 68.dp,
    isActive: Boolean = true,
    hapticEveryStep: Boolean = false
) {
    val view = LocalView.current
    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val normalizedFraction = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)

    // Sensitivity accumulator for smooth vertical drag
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(DarkGraphite)
                .border(1.dp, if (isActive) AuroraBrass.copy(alpha = 0.35f) else HairlineBorder, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onValueChange(defaultValue)
                        },
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    )
                }
                .pointerInput(valueRange, step) {
                    detectDragGestures(
                        onDragStart = {
                            accumulatedDrag = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Drag up to increase (+), down to decrease (-)
                            accumulatedDrag -= dragAmount.y
                            val rangeSpan = maxVal - minVal
                            val delta = (accumulatedDrag / 160f) * rangeSpan
                            if (kotlin.math.abs(delta) >= step) {
                                val stepsCount = (delta / step).toInt()
                                val stepDelta = stepsCount * step
                                val newValue = (value + stepDelta).coerceIn(minVal, maxVal)
                                if (newValue != value) {
                                    onValueChange(newValue)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    accumulatedDrag -= (stepsCount * step / rangeSpan) * 160f
                                }
                            }
                        }
                    )
                }
        ) {
            // Arc Track & Active Brass Needle
            Canvas(modifier = Modifier.size(diameter - 8.dp)) {
                val strokeWidth = 1.5.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // 240-degree dial arc, open at bottom (from 150 deg to 390 deg)
                val startAngle = 150f
                val sweepAngle = 240f

                // Inactive Track
                drawArc(
                    color = HairlineSubtle,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active Arc up to current value
                val activeSweep = sweepAngle * normalizedFraction
                if (activeSweep > 0f && isActive) {
                    drawArc(
                        color = AuroraBrassDim,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth * 1.5f, cap = StrokeCap.Round)
                    )
                }

                // Needle Indicator Tick at current angle
                val currentAngleDeg = startAngle + (sweepAngle * normalizedFraction)
                val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())
                val needleStart = Offset(
                    center.x + (radius - 3.dp.toPx()) * cos(currentAngleRad).toFloat(),
                    center.y + (radius - 3.dp.toPx()) * sin(currentAngleRad).toFloat()
                )
                val needleEnd = Offset(
                    center.x + (radius + 2.dp.toPx()) * cos(currentAngleRad).toFloat(),
                    center.y + (radius + 2.dp.toPx()) * sin(currentAngleRad).toFloat()
                )

                drawLine(
                    color = if (isActive) AuroraBrass else WarmSilver,
                    start = needleStart,
                    end = needleEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Central Monospaced Value
            Text(
                text = valueDisplay,
                color = if (isActive) WarmSilver else Ash,
                fontSize = if (diameter >= 64.dp) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.2.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Parameter Label (Clean Humanist Sans)
        Text(
            text = label.uppercase(),
            color = if (isActive) AuroraBrass else Ash,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Default,
            letterSpacing = 0.8.sp
        )
    }
}

package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.auroracam.app.ui.theme.DarkGraphite
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.WarmSilver
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * AuroraCircularDial — Minimalist Circular Photographic Adjuster Dial.
 *
 * Displays a clean circular gauge with:
 * - A calibrated circular track and glowing active progress arc
 * - Centered monospaced numeric value
 * - Parameter name label beneath the dial
 * - Smooth vertical/rotational drag with tactile haptic ticks
 * - Double tap to reset to default
 */
@Composable
fun AuroraCircularDial(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    defaultValue: Float = 0.5f,
    valueRange: ClosedFloatingPointRange<Float> = 0.0f..1.0f,
    displayFormatter: (Float) -> String = { String.format(Locale.US, "%.0f%%", it * 100f) },
    dialSize: Dp = 68.dp,
    accentColor: Color = WarmSilver,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }

    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0.0f, 1.0f)

    val animatedProgress by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 120),
        label = "DialProgress"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(dialSize)
                .pointerInput(valueRange) {
                    detectTapGestures(
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onValueChange(defaultValue)
                        }
                    )
                }
                .pointerInput(valueRange) {
                    var accumulatedDrag = 0f
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            accumulatedDrag = 0f
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag -= dragAmount.y
                            // Drag sensitivity: 220dp of vertical drag = full range
                            val deltaRange = valueRange.endInclusive - valueRange.start
                            val delta = (accumulatedDrag / 220f) * deltaRange
                            val newValue = (value + delta).coerceIn(valueRange.start, valueRange.endInclusive)
                            if (newValue != value) {
                                accumulatedDrag = 0f
                                if (kotlin.math.abs(newValue - value) > deltaRange * 0.04f) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                onValueChange(newValue)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(dialSize)) {
                val strokeWidth = 2.dp.toPx()
                val radius = (size.minDimension - strokeWidth * 2) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Dial arc spans 270 degrees: from 135 deg to 405 deg (90 deg gap at bottom)
                val startAngle = 135f
                val sweepAngleTotal = 270f

                // 1. Background Inactive Track
                drawArc(
                    color = HairlineBorder,
                    startAngle = startAngle,
                    sweepAngle = sweepAngleTotal,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 2. Subtle Inner Track Background
                drawCircle(
                    color = DarkGraphite.copy(alpha = 0.55f),
                    radius = radius - strokeWidth,
                    center = center
                )

                // 3. Active Progress Arc
                val currentSweep = sweepAngleTotal * animatedProgress
                if (currentSweep > 0.5f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.6f),
                                accentColor
                            ),
                            center = center
                        ),
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth + 0.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 4. Indicator Needle / Dot at Current Position
                val angleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                val dotX = center.x + radius * cos(angleRad).toFloat()
                val dotY = center.y + radius * sin(angleRad).toFloat()

                drawCircle(
                    color = accentColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }

            // Numeric Readout in Dial Center
            Text(
                text = displayFormatter(value),
                color = if (isDragging) AuroraBrass else WarmSilver,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Parameter Name Below Dial
        Text(
            text = label.uppercase(Locale.US),
            color = if (isDragging) WarmSilver else Ash,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.AuroraInstrumentTokens
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.SurfaceRecess
import com.auroracam.app.ui.theme.WarmSlate
import kotlin.math.roundToInt

/**
 * Parameter-Specific Instrument Scales.
 *
 * 1. Film Density Scale (for Look Grain):
 *    GRAIN DENSITY │ 0.00 ···· 0.04 ···· 0.12 ···· 0.20
 *
 * 2. Radial Falloff Scale (for Look Vignette):
 *    VIGNETTE FALLOFF │ 0% ···· 25% ···· 50% ···· 100%
 *
 * 3. Optical Halation Scale (for Film Halation & Threshold):
 *    HALATION GLOW │ 0.0 ···· 0.35 ···· 0.80
 *
 * 4. Profile Mix Scale (for 3D LUT Mix Intensity):
 *    LOOK MIX │ 0% ········· 100%
 */

@Composable
fun InstrumentLinearScale(
    label: String,
    value: Float,
    onValueChanged: (Float) -> Unit,
    minVal: Float = 0f,
    maxVal: Float = 1f,
    unitSuffix: String = "",
    displayMultiplier: Float = 100f,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val onValueState by rememberUpdatedState(onValueChanged)
    var dragVal by remember { mutableFloatStateOf(value) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = WarmSlate,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            )
            val displayNum = "${"%.0f".format(value * displayMultiplier)}$unitSuffix"
            Text(
                text = displayNum,
                color = if (value > minVal) BurntBrass else ParchmentWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Horizontal Measurement Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(minVal, maxVal) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragVal = value },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val fraction = dragAmount / size.width.toFloat()
                            val next = (dragVal + fraction * (maxVal - minVal)).coerceIn(minVal, maxVal)
                            if ((next * 100f).roundToInt() != (value * 100f).roundToInt()) {
                                view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                                onValueState(next)
                            }
                            dragVal = next
                        }
                    )
                }
                .padding(vertical = 4.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                // Recessed baseline
                drawLine(
                    color = HairlineBorder,
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.dp.toPx()
                )

                // Calibration ticks at 0%, 25%, 50%, 75%, 100%
                for (i in 0..4) {
                    val x = (i / 4f) * w
                    val tickLen = if (i == 0 || i == 4) 6.dp.toPx() else 4.dp.toPx()
                    drawLine(
                        color = if (i == 0 || i == 4) WarmSlate else HairlineBorder,
                        start = Offset(x, midY - tickLen / 2f),
                        end = Offset(x, midY + tickLen / 2f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Active Burnt Brass Index Needle (▲)
                val norm = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val needleX = norm * w

                drawLine(
                    color = BurntBrass,
                    start = Offset(needleX, midY - 6.dp.toPx()),
                    end = Offset(needleX, midY + 6.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

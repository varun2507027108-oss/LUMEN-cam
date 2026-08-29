package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.WarmSlate
import kotlin.math.roundToInt

/**
 * EV Vernier Exposure Ladder with Active Touch Tracking.
 *
 * Precision optical measurement ladder with calibrated stop divisions:
 *   +2.0  ─
 *   +1.0  ─
 *   +0.7  ◄ (Painted Brass Index Needle)
 *    0.0  ━━━ (Zero Datum Line)
 *   -1.0  ─
 *   -2.0  ─
 */
@Composable
fun AuroraExposureVernier(
    evBias: Float,
    onEvBiasChanged: (Float) -> Unit,
    minEv: Float = -2.0f,
    maxEv: Float = 2.0f,
    onDraggingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val onEvChangedState by rememberUpdatedState(onEvBiasChanged)
    val onDraggingChangedState by rememberUpdatedState(onDraggingChanged)
    var dragAccumulator by remember { mutableFloatStateOf(evBias) }

    Row(
        modifier = modifier
            .width(60.dp)
            .height(180.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumulator = evBias
                        onDraggingChangedState(true)
                    },
                    onDragEnd = {
                        onDraggingChangedState(false)
                    },
                    onDragCancel = {
                        onDraggingChangedState(false)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Dragging upwards increases EV (+), dragging downwards decreases (-)
                        val delta = -dragAmount / 75f
                        val newEv = (dragAccumulator + delta).coerceIn(minEv, maxEv)
                        val steppedEv = (newEv * 3f).roundToInt() / 3f // 1/3 EV steps
                        if (steppedEv != evBias) {
                            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                            onEvChangedState(steppedEv)
                        }
                        dragAccumulator = newEv
                    }
                )
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // Vertical Vernier Scale
        Canvas(modifier = Modifier.fillMaxHeight().width(24.dp)) {
            val h = size.height
            val w = size.width
            val totalSteps = 12 // -2.0 to +2.0 in 1/3 steps = 12 intervals
            val stepH = h / totalSteps.toFloat()

            // 1. Center Zero Datum line
            val zeroY = h / 2f
            drawLine(
                color = WarmSlate,
                start = Offset(w - 14.dp.toPx(), zeroY),
                end = Offset(w, zeroY),
                strokeWidth = 1.5.dp.toPx()
            )

            // 2. Stop Divisions and intermediate tick marks
            for (i in 0..totalSteps) {
                val y = i * stepH
                val isMajor = (i == 0 || i == 3 || i == 6 || i == 9 || i == 12)
                val tickLen = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                val tickColor = if (isMajor) WarmSlate else HairlineBorder
                drawLine(
                    color = tickColor,
                    start = Offset(w - tickLen, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 3. Active Burnt Brass Index Needle (◄)
            val normalizedEv = ((maxEv - evBias) / (maxEv - minEv)).coerceIn(0f, 1f)
            val needleY = normalizedEv * h

            // Draw pointer
            drawLine(
                color = BurntBrass,
                start = Offset(w - 12.dp.toPx(), needleY),
                end = Offset(w, needleY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Active EV Value Label
        Column(
            modifier = Modifier.padding(start = 4.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            val text = if (evBias >= 0) "+${"%.1f".format(evBias)}" else "%.1f".format(evBias)
            Text(
                text = text,
                color = if (evBias != 0f) BurntBrass else ParchmentWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "EV",
                color = MutedText,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

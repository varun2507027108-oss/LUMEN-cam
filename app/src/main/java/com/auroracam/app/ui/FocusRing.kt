package com.auroracam.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Ultra-sleek, modern Camera Focus Ring & Precision Exposure Slider.
 *
 * Designed with minimalist, high-end cinema camera ergonomics:
 * - Ultra-fine golden corner targeting reticles with center micro-dot.
 * - Glassmorphic vertical Exposure slider with glowing sun indicator and EV readout.
 * - Silky smooth vertical drag gestures for real-time EV bias adjustments (-2.0 EV to +2.0 EV).
 */
@Composable
fun FocusRing(
    focusPoint: Offset?,
    evBias: Float,
    onEvBiasChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    val density = LocalDensity.current
    var isVisible by remember(focusPoint) { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }

    // Auto-dismiss countdown (3.2 seconds), pauses whenever the user is actively dragging EV
    LaunchedEffect(focusPoint, isDragging, evBias) {
        if (!isDragging) {
            delay(3200)
            isVisible = false
            delay(280)
            onDismiss()
        }
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.88f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "FocusScale"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 180),
        label = "FocusAlpha"
    )

    val sunPulse by rememberInfiniteTransition(label = "SunPulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SunScale"
    )

    val goldAccent = Color(0xFFFFD54F) // Classic Leica/Arri Gold Amber
    val boxSize = 64.dp
    val halfBoxPx = with(density) { (boxSize / 2).toPx() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Position the focus box centered on tap point, clamped inside screen edges
        val clampedCenterX = focusPoint.x.coerceIn(halfBoxPx + 16f, screenWidthPx - halfBoxPx - 16f)
        val clampedCenterY = focusPoint.y.coerceIn(halfBoxPx + 60f, screenHeightPx - halfBoxPx - 100f)

        // Decide if slider goes to the right or left of the box based on screen room
        val sliderOnRight = clampedCenterX < screenWidthPx - with(density) { 90.dp.toPx() }
        val sliderOffsetXPx = if (sliderOnRight) with(density) { 42.dp.toPx() } else with(density) { -76.dp.toPx() }

        val boxTopLeft = IntOffset(
            x = (clampedCenterX - halfBoxPx).roundToInt(),
            y = (clampedCenterY - halfBoxPx).roundToInt()
        )

        // 1. Sleek Reticle / Focus Square
        Box(
            modifier = Modifier
                .offset { boxTopLeft }
                .size(boxSize)
                .scale(scaleAnim)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val corner = 12.dp.toPx()
                val strokeW = 1.75.dp.toPx()
                val color = goldAccent.copy(alpha = alphaAnim * 0.95f)

                // Top-Left corner
                drawLine(color, Offset(0f, 0f), Offset(corner, 0f), strokeW, StrokeCap.Round)
                drawLine(color, Offset(0f, 0f), Offset(0f, corner), strokeW, StrokeCap.Round)

                // Top-Right corner
                drawLine(color, Offset(w, 0f), Offset(w - corner, 0f), strokeW, StrokeCap.Round)
                drawLine(color, Offset(w, 0f), Offset(w, corner), strokeW, StrokeCap.Round)

                // Bottom-Left corner
                drawLine(color, Offset(0f, h), Offset(corner, h), strokeW, StrokeCap.Round)
                drawLine(color, Offset(0f, h), Offset(0f, h - corner), strokeW, StrokeCap.Round)

                // Bottom-Right corner
                drawLine(color, Offset(w, h), Offset(w - corner, h), strokeW, StrokeCap.Round)
                drawLine(color, Offset(w, h), Offset(w, h - corner), strokeW, StrokeCap.Round)

                // Center precision dot
                drawCircle(color, radius = 1.75.dp.toPx(), center = Offset(w / 2f, h / 2f))
            }
        }

        // 2. High-Precision Exposure Slider (with generous touch capture box)
        val sliderTopLeft = IntOffset(
            x = (clampedCenterX + sliderOffsetXPx).roundToInt(),
            y = (clampedCenterY - with(density) { 62.dp.toPx() }).roundToInt()
        )

        Box(
            modifier = Modifier
                .offset { sliderTopLeft }
                .width(44.dp)
                .height(124.dp)
                .scale(scaleAnim)
                .pointerInput(focusPoint) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { _, dragAmount ->
                        // Upwards drag decreases Y (increases EV), downwards drag increases Y (decreases EV)
                        val deltaEv = -dragAmount / 45.0f
                        val newEv = (evBias + deltaEv).coerceIn(-2.0f, 2.0f)
                        onEvBiasChanged(newEv)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xB30A0A0A))
                    .padding(horizontal = 7.dp, vertical = 8.dp)
            ) {
                // Glowing Sun Icon
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Exposure",
                    tint = goldAccent.copy(alpha = alphaAnim),
                    modifier = Modifier
                        .size(15.dp)
                        .scale(if (isDragging) sunPulse else 1.0f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Vertical exposure track
                val trackHeight = 60.dp
                val trackHeightPx = with(density) { trackHeight.toPx() }

                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    goldAccent.copy(alpha = 0.7f),
                                    Color(0x44FFFFFF),
                                    Color(0x33000000)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Calculate thumb offset based on evBias in range [-2.0, +2.0]
                    // Normalized: 0.0 (at -2.0 EV bottom) -> 1.0 (at +2.0 EV top)
                    val progress = ((evBias + 2.0f) / 4.0f).coerceIn(0.0f, 1.0f)
                    val rawY = (1.0f - progress) * trackHeightPx - (trackHeightPx / 2f)
                    val thumbOffsetY = with(density) { rawY.toDp() }

                    // Glowing Pill Thumb
                    Box(
                        modifier = Modifier
                            .offset(y = thumbOffsetY)
                            .width(9.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(goldAccent.copy(alpha = alphaAnim))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Numerical EV Readout
                Text(
                    text = "${if (evBias > 0.05f) "+" else ""}${"%.1f".format(evBias)}",
                    color = if (evBias != 0.0f) goldAccent.copy(alpha = alphaAnim) else Color(0x88FFFFFF),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

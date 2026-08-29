package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.FocusMint
import com.auroracam.app.ui.theme.FocusMintDim
import com.auroracam.app.ui.theme.GlassHighlight
import com.auroracam.app.ui.theme.HyperSilver
import com.auroracam.app.ui.theme.ObsidianBlack
import com.auroracam.app.ui.theme.OpticCyan
import com.auroracam.app.ui.theme.SlateBorder
import com.auroracam.app.ui.theme.SmokedGlass
import com.auroracam.app.ui.theme.SolarGold
import com.auroracam.app.ui.theme.SolarOrange
import com.auroracam.app.ui.theme.TextMuted
import com.auroracam.app.ui.theme.TextPrimary
import com.auroracam.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * World-Class Cinema Optical Focus Reticle, Solar Vernier Exposure Ladder & AE/AF Lock.
 *
 * Inspired by Arri Alexa, Hasselblad Phocus, and Halide Mark II.
 */
@Composable
fun FocusRing(
    focusPoint: Offset?,
    evBias: Float,
    onEvBiasChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    val density = LocalDensity.current
    val view = LocalView.current
    var isVisible by remember(focusPoint) { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }

    val currentEvBiasState by rememberUpdatedState(evBias)
    val onEvBiasChangedState by rememberUpdatedState(onEvBiasChanged)
    var accumulatedEv by remember { mutableFloatStateOf(evBias) }

    // Auto-dismiss countdown (3.2s), paused during interaction or when AE/AF locked
    LaunchedEffect(focusPoint, isDragging, isLocked) {
        if (!isDragging && !isLocked) {
            delay(3200)
            isVisible = false
            delay(250)
            onDismiss()
        }
    }

    // Entrance and exit scale/alpha animations
    val scaleAnim by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.85f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "FocusScale"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 180),
        label = "FocusAlpha"
    )

    // Breathing pulse while hunting/tracking focus
    val infiniteTransition = rememberInfiniteTransition(label = "ReticleGlow")
    val huntingPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HuntingPulse"
    )

    // Solar corona ray rotation
    val sunRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SunRotation"
    )

    // Focus state chromatic accent
    val activeReticleColor = if (isLocked) FocusMint else OpticCyan
    val boxSize = 72.dp
    val halfBoxPx = with(density) { (boxSize / 2).toPx() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Position reticle centered on tap point, clamped within viewport bounds
        val clampedCenterX = focusPoint.x.coerceIn(halfBoxPx + 16f, screenWidthPx - halfBoxPx - 16f)
        val clampedCenterY = focusPoint.y.coerceIn(halfBoxPx + 80f, screenHeightPx - halfBoxPx - 140f)

        // Decide if slider goes to the right or left of the reticle
        val sliderOnRight = clampedCenterX < screenWidthPx - with(density) { 96.dp.toPx() }
        val sliderOffsetXPx = if (sliderOnRight) with(density) { 48.dp.toPx() } else with(density) { -86.dp.toPx() }

        val boxTopLeft = IntOffset(
            x = (clampedCenterX - halfBoxPx).roundToInt(),
            y = (clampedCenterY - halfBoxPx).roundToInt()
        )

        // =========================================================================
        // 0. AE / AF LOCK CAPSULE (Positioned directly atop the reticle)
        // =========================================================================
        val lockBadgeTopLeft = IntOffset(
            x = (clampedCenterX - with(density) { 56.dp.toPx() }).roundToInt(),
            y = (clampedCenterY - halfBoxPx - with(density) { 36.dp.toPx() }).roundToInt()
        )

        Box(
            modifier = Modifier
                .offset { lockBadgeTopLeft }
                .scale(scaleAnim)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isLocked) FocusMint else SmokedGlass)
                .border(
                    width = 1.dp,
                    color = if (isLocked) FocusMint else GlassHighlight,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onToggleLock()
                }
                .padding(horizontal = 10.dp, vertical = 4.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Micro Optic Indicator Dot
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isLocked) ObsidianBlack else (if (isVisible) OpticCyan else TextMuted))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "AE/AF Lock",
                    tint = if (isLocked) ObsidianBlack else TextPrimary,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.5.dp))
                Text(
                    text = if (isLocked) "AE/AF LOCKED" else "AE/AF LOCK",
                    color = if (isLocked) ObsidianBlack else TextPrimary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // =========================================================================
        // 1. OPTICAL RANGEFINDER RETICLE (4 Precision Chamfered Brackets + Reticle Center)
        // =========================================================================
        Box(
            modifier = Modifier
                .offset { boxTopLeft }
                .size(boxSize)
                .scale(if (!isLocked) huntingPulse * scaleAnim else scaleAnim)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onToggleLock()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val bracketLen = 14.dp.toPx()
                val strokeW = 1.75.dp.toPx()
                val crosshairLen = 5.dp.toPx()
                val reticleColor = activeReticleColor.copy(alpha = alphaAnim * 0.95f)

                // 4 Optical Corner L-Brackets
                // Top-Left
                drawLine(reticleColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW, StrokeCap.Round)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, bracketLen), strokeW, StrokeCap.Round)

                // Top-Right
                drawLine(reticleColor, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeW, StrokeCap.Round)
                drawLine(reticleColor, Offset(w, 0f), Offset(w, bracketLen), strokeW, StrokeCap.Round)

                // Bottom-Left
                drawLine(reticleColor, Offset(0f, h), Offset(bracketLen, h), strokeW, StrokeCap.Round)
                drawLine(reticleColor, Offset(0f, h), Offset(0f, h - bracketLen), strokeW, StrokeCap.Round)

                // Bottom-Right
                drawLine(reticleColor, Offset(w, h), Offset(w - bracketLen, h), strokeW, StrokeCap.Round)
                drawLine(reticleColor, Offset(w, h), Offset(w, h - bracketLen), strokeW, StrokeCap.Round)

                // Center Fine Crosshair [+]
                val cx = w / 2f
                val cy = h / 2f
                drawLine(reticleColor, Offset(cx - crosshairLen, cy), Offset(cx + crosshairLen, cy), 1.25.dp.toPx())
                drawLine(reticleColor, Offset(cx, cy - crosshairLen), Offset(cx, cy + crosshairLen), 1.25.dp.toPx())

                // Micro Center Optical Target Dot
                drawCircle(
                    color = reticleColor,
                    radius = if (isLocked) 2.5.dp.toPx() else 1.75.dp.toPx(),
                    center = Offset(cx, cy)
                )

                if (isLocked) {
                    // Outer Lock Ring Pulse
                    drawCircle(
                        color = FocusMintDim,
                        radius = 8.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Bottom Rangefinder Status Badge
            Text(
                text = if (isLocked) "AF [LOCK]" else "AF [ ● ]",
                color = activeReticleColor.copy(alpha = alphaAnim * 0.85f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 12.dp)
            )
        }

        // =========================================================================
        // 2. PRECISION SOLAR VERNIER EXPOSURE LADDER (Dynamic Sun & 1/3-Stop Ruler)
        // =========================================================================
        val sliderTopLeft = IntOffset(
            x = (clampedCenterX + sliderOffsetXPx).roundToInt(),
            y = (clampedCenterY - with(density) { 72.dp.toPx() }).roundToInt()
        )

        Box(
            modifier = Modifier
                .offset { sliderTopLeft }
                .width(52.dp)
                .height(148.dp)
                .scale(scaleAnim)
                .pointerInput(focusPoint) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            accumulatedEv = currentEvBiasState
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        onDragEnd = {
                            isDragging = false
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        // Smooth calibrated vertical drag: upwards increases EV, downwards decreases EV
                        val deltaEv = -dragAmount / 38.0f
                        val prevEv = accumulatedEv
                        val newEv = (accumulatedEv + deltaEv).coerceIn(-2.0f, 2.0f)
                        accumulatedEv = newEv
                        
                        // Haptic tick when crossing integer EV stops or 0.0 datum
                        if ((prevEv < 0f && newEv >= 0f) || (prevEv > 0f && newEv <= 0f) ||
                            (prevEv.roundToInt() != newEv.roundToInt())
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        
                        onEvBiasChangedState(newEv)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SmokedGlass)
                    .border(1.dp, GlassHighlight, RoundedCornerShape(20.dp))
                    .padding(horizontal = 7.dp, vertical = 8.dp)
            ) {
                // Dynamic Celestial Sun Engine (Custom Canvas with Solar Corona Rays)
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sunCenterX = size.width / 2f
                        val sunCenterY = size.height / 2f
                        val coreRadius = 4.2.dp.toPx()

                        // Color shifts dynamically based on exposure level
                        val solarColor = when {
                            evBias > 0.3f -> Brush.linearGradient(listOf(SolarGold, SolarOrange))
                            evBias < -0.3f -> Brush.linearGradient(listOf(HyperSilver, OpticCyan))
                            else -> Brush.linearGradient(listOf(SolarGold, HyperSilver))
                        }
                        val rayColor = if (evBias > 0.05f) SolarGold else (if (evBias < -0.05f) OpticCyan else HyperSilver)

                        // 8 Solar Corona Rays
                        val rayCount = 8
                        val rayInnerRadius = coreRadius + 2.0.dp.toPx()
                        val rayLength = (3.5.dp.toPx() + (evBias.coerceIn(-1.5f, 2.0f) + 1.5f) * 1.2f).coerceAtLeast(1.5f)

                        for (i in 0 until rayCount) {
                            val angleDeg = (i * 360f / rayCount) + (if (isDragging) sunRotation else 0f)
                            val angleRad = Math.toRadians(angleDeg.toDouble())
                            val startX = sunCenterX + (rayInnerRadius * cos(angleRad)).toFloat()
                            val startY = sunCenterY + (rayInnerRadius * sin(angleRad)).toFloat()
                            val endX = sunCenterX + ((rayInnerRadius + rayLength) * cos(angleRad)).toFloat()
                            val endY = sunCenterY + ((rayInnerRadius + rayLength) * sin(angleRad)).toFloat()

                            drawLine(
                                color = rayColor.copy(alpha = (0.6f + (evBias * 0.15f)).coerceIn(0.3f, 1.0f) * alphaAnim),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 1.25.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Central Solar Core
                        drawCircle(
                            brush = solarColor,
                            radius = coreRadius,
                            center = Offset(sunCenterX, sunCenterY)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Vernier Precision Scale & Ruler Track
                val trackHeight = 72.dp
                val trackHeightPx = with(density) { trackHeight.toPx() }

                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(trackHeight),
                    contentAlignment = Alignment.Center
                ) {
                    // Precision Ruler Notches & EV Ticks (1/3 stop increments)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rulerW = size.width
                        val rulerH = size.height

                        // Major and Minor tick lines (-2, -1, 0, +1, +2)
                        val totalStops = 12 // 4 EV range * 3 stops
                        for (i in 0..totalStops) {
                            val stopRatio = i.toFloat() / totalStops.toFloat()
                            val tickY = stopRatio * rulerH
                            val isMajor = (i % 3 == 0)
                            val isCenter = (i == 6) // 0.0 EV

                            val tickLen = if (isCenter) 14.dp.toPx() else (if (isMajor) 10.dp.toPx() else 5.dp.toPx())
                            val tickColor = when {
                                isCenter -> SolarGold.copy(alpha = 0.9f)
                                isMajor -> HyperSilver.copy(alpha = 0.65f)
                                else -> TextMuted.copy(alpha = 0.4f)
                            }
                            val startX = (rulerW - tickLen) / 2f

                            drawLine(
                                color = tickColor,
                                start = Offset(startX, tickY),
                                end = Offset(startX + tickLen, tickY),
                                strokeWidth = if (isMajor) 1.25.dp.toPx() else 0.75.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Dynamic Floating Vernier Thumb
                    val progress = ((evBias + 2.0f) / 4.0f).coerceIn(0.0f, 1.0f)
                    val rawY = (1.0f - progress) * trackHeightPx - (trackHeightPx / 2f)
                    val thumbOffsetY = with(density) { rawY.toDp() }

                    // Luminous Specular Thumb Pill
                    Box(
                        modifier = Modifier
                            .offset(y = thumbOffsetY)
                            .width(18.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(SolarGold, SolarOrange)
                                )
                            )
                            .border(0.5.dp, HyperSilver, RoundedCornerShape(3.dp))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Digital High-Contrast EV Readout Capsule
                val isPositive = evBias > 0.05f
                val isNegative = evBias < -0.05f
                val evDisplayColor = if (isPositive) SolarGold else (if (isNegative) OpticCyan else HyperSilver)

                Text(
                    text = "${if (isPositive) "+" else ""}${"%.1f".format(evBias)}",
                    color = evDisplayColor.copy(alpha = alphaAnim),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


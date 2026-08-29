package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.BlendMode
import com.auroracam.app.ui.DxStage
import com.auroracam.app.ui.theme.AuroraInstrumentTokens
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.BurntBrassDim
import com.auroracam.app.ui.theme.Graphite
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.OpticalGreen
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.SurfaceRecess
import com.auroracam.app.ui.theme.TextDisabled
import com.auroracam.app.ui.theme.WarmSlate

/**
 * 1. Temporal Echo Instrument Surface:
 *    Timeline visualizer: NOW ●────●────● F3 (History buffer feedback)
 */
@Composable
fun TemporalEchoSurface(
    decay: Float,
    onDecayChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Frame History Timeline
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceRecess, AuroraInstrumentTokens.CornerMicro)
                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerMicro)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FRAME HISTORY",
                color = WarmSlate,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "NOW", color = BurntBrass, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(text = "──●──", color = HairlineBorder, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text(text = "F1", color = ParchmentWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(text = "──●──", color = HairlineBorder, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text(text = "F2", color = WarmSlate, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(text = "──●──", color = HairlineBorder, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text(text = "F3", color = MutedText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Echo Decay Scale
        InstrumentLinearScale(
            label = "ECHO DECAY PERSISTENCE",
            value = decay,
            onValueChanged = onDecayChanged,
            minVal = 0.30f,
            maxVal = 0.95f,
            unitSuffix = "%",
            displayMultiplier = 100f
        )
    }
}

/**
 * 2. Motion Exposure Instrument Surface:
 *    Differential field scale: STATIC ─────── MOTION ▲
 */
@Composable
fun MotionExposureSurface(
    threshold: Float,
    onThresholdChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Differential Balance Readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceRecess, AuroraInstrumentTokens.CornerMicro)
                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerMicro)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DIFFERENTIAL FILTER",
                color = WarmSlate,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "STATIC [NULL]", color = MutedText, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Text(text = "│", color = HairlineBorder, fontSize = 9.sp)
                Text(text = "MOTION [CAPTURE]", color = OpticalGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // Motion Sensitivity Threshold
        InstrumentLinearScale(
            label = "MOTION DELTA SENSITIVITY",
            value = threshold,
            onValueChanged = onThresholdChanged,
            minVal = 0.02f,
            maxVal = 0.30f,
            unitSuffix = "%",
            displayMultiplier = 100f
        )
    }
}

/**
 * 3. Double Exposure Instrument Surface:
 *    Two negative exposure planes: FRAME A ────── FRAME B ────── ↕ ALIGN
 */
@Composable
fun DoubleExposureSurface(
    dxStage: DxStage,
    dxBlendMode: BlendMode = BlendMode.SCREEN,
    onBlendModeSelected: (BlendMode) -> Unit = {},
    dxOpacity: Float = 1.0f,
    onOpacityChanged: (Float) -> Unit = {},
    onResetDx: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Two-Frame Negative Alignment Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceRecess, AuroraInstrumentTokens.CornerMicro)
                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerMicro)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NEGATIVE A",
                    color = if (dxStage == DxStage.STAGE_1_EMPTY) BurntBrass else ParchmentWhite,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (dxStage == DxStage.STAGE_1_EMPTY) "ARMED / WAITING SHUTTER" else "LOCKED IN BUFFER",
                    color = if (dxStage == DxStage.STAGE_1_EMPTY) WarmSlate else OpticalGreen,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(text = "↕ BLEND", color = BurntBrass, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "NEGATIVE B",
                    color = if (dxStage == DxStage.STAGE_2_LOCKED) BurntBrass else MutedText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (dxStage == DxStage.STAGE_2_LOCKED) "LIVE ALIGNMENT" else "PENDING FRAME A",
                    color = if (dxStage == DxStage.STAGE_2_LOCKED) OpticalGreen else TextDisabled,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Blend Mode Index
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BlendMode.entries.forEach { mode ->
                val isSelected = (dxBlendMode == mode)
                Box(
                    modifier = Modifier
                        .background(if (isSelected) BurntBrassDim else Graphite, AuroraInstrumentTokens.CornerMicro)
                        .border(
                            AuroraInstrumentTokens.StrokeHairline,
                            if (isSelected) BurntBrass else HairlineBorder,
                            AuroraInstrumentTokens.CornerMicro
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onBlendModeSelected(mode)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mode.label.uppercase(),
                        color = if (isSelected) BurntBrass else WarmSlate,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Opacity Scale
        InstrumentLinearScale(
            label = "BLEND OPACITY",
            value = dxOpacity,
            onValueChanged = onOpacityChanged,
            minVal = 0.1f,
            maxVal = 1.0f,
            unitSuffix = "%",
            displayMultiplier = 100f
        )

        if (dxStage == DxStage.STAGE_2_LOCKED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "[ RESET BUFFER ]",
                    color = BurntBrass,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onResetDx() }
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * 4. Light Trail Instrument Surface:
 *    Accumulation timeline + Decay scale + Clear buffer trigger
 */
@Composable
fun LightTrailSurface(
    decay: Float,
    onDecayChanged: (Float) -> Unit,
    blendMode: Int,
    onBlendModeChanged: (Int) -> Unit,
    onClearTrails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Blend Mode Index
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val modes = listOf("MAX SCREEN", "ADDITIVE", "ALPHA OVERLAY")
            modes.forEachIndexed { index, modeName ->
                val isSelected = (blendMode == index)
                Box(
                    modifier = Modifier
                        .background(if (isSelected) BurntBrassDim else Graphite, AuroraInstrumentTokens.CornerMicro)
                        .border(
                            AuroraInstrumentTokens.StrokeHairline,
                            if (isSelected) BurntBrass else HairlineBorder,
                            AuroraInstrumentTokens.CornerMicro
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onBlendModeChanged(index)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = modeName,
                        color = if (isSelected) BurntBrass else WarmSlate,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClearTrails()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "[ CLEAR BUFFER ]",
                    color = ParchmentWhite,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Decay Scale
        InstrumentLinearScale(
            label = "PHOTON ACCUMULATION DECAY",
            value = decay,
            onValueChanged = onDecayChanged,
            minVal = 0.80f,
            maxVal = 0.99f,
            unitSuffix = "%",
            displayMultiplier = 100f
        )
    }
}

package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.gl.GpuTelemetry
import com.auroracam.app.ui.theme.AuroraInstrumentTokens
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.BurntBrassDim
import com.auroracam.app.ui.theme.Graphite
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.Obsidian
import com.auroracam.app.ui.theme.OpticalGreen
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.SurfaceRaised
import com.auroracam.app.ui.theme.SurfaceRecess
import com.auroracam.app.ui.theme.WarmSlate

/**
 * Aurora LAB Engineering Console (Full-Screen Route).
 *
 * Dedicated full-screen engineering telemetry & diagnostic suite:
 * - Real-time GPU Pipeline Profiling (FPS, Frametime, FBO color space)
 * - OpenGL ES 3.0 Multi-Pass Shader Engine inspection
 * - Sensor Telemetry & 16-Bit Half-Float validation
 * - 7-Up Contact Sheet Generator for multi-look diagnostic evaluation
 */
@Composable
fun AuroraLabScreen(
    gpuTelemetry: GpuTelemetry?,
    currentFps: Double,
    isLookPrecision16f: Boolean,
    onTogglePrecision16f: () -> Unit,
    onGenerateContactSheet: () -> Unit,
    onExitLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState)
    ) {
        // 1. Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onExitLab()
                    }
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Camera",
                    tint = BurntBrass,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "← CAMERA",
                    color = BurntBrass,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = "ENGINEERING LAB CONSOLE",
                color = MutedText,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. GPU Pipeline Diagnostics Block
        LabSectionHeader(title = "01 // GPU PIPELINE METRICS")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Graphite, AuroraInstrumentTokens.CornerInstrument)
                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerInstrument)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LabMetricRow(label = "REAL-TIME FRAME RATE", value = "${"%.1f".format(currentFps)} FPS", highlight = true)
            LabMetricRow(label = "GPU RENDER LATENCY", value = "${"%.2f".format(gpuTelemetry?.frameTimeMs ?: 16.6f)} ms")
            LabMetricRow(label = "FRAMEBUFFER RESOLUTION", value = "${gpuTelemetry?.previewWidth ?: 1080} x ${gpuTelemetry?.previewHeight ?: 1920}")
            LabMetricRow(label = "FBO COLOR PRECISION", value = if (isLookPrecision16f) "GL_RGBA16F (16-BIT HALF)" else "GL_RGBA8 (8-BIT UNORM)")
            LabMetricRow(label = "FRAME HISTORY BUFFERS", value = "3 x FBO RING ALLOCATED")
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Precision Pipeline Toggle Block
        LabSectionHeader(title = "02 // COLOR PIPELINE PRECISION")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Graphite, AuroraInstrumentTokens.CornerInstrument)
                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerInstrument)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                    onTogglePrecision16f()
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "16-BIT HALF-FLOAT FBO",
                    color = ParchmentWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Eliminates banding in 3D LUT grading & highlight halation",
                    color = WarmSlate,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Box(
                modifier = Modifier
                    .background(if (isLookPrecision16f) BurntBrassDim else SurfaceRecess, AuroraInstrumentTokens.CornerMicro)
                    .border(
                        AuroraInstrumentTokens.StrokeHairline,
                        if (isLookPrecision16f) BurntBrass else HairlineBorder,
                        AuroraInstrumentTokens.CornerMicro
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isLookPrecision16f) "[ ENABLED ]" else "[ DISABLED ]",
                    color = if (isLookPrecision16f) BurntBrass else MutedText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Contact Sheet Diagnostic Generator
        LabSectionHeader(title = "03 // 7-UP CONTACT SHEET GENERATION")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Graphite, AuroraInstrumentTokens.CornerInstrument)
                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerInstrument)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Captures the current scene simultaneously through all 7 color profiles into a single composite darkroom contact sheet grid.",
                color = WarmSlate,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceRaised, AuroraInstrumentTokens.CornerMicro)
                    .border(AuroraInstrumentTokens.StrokeHairline, BurntBrass, AuroraInstrumentTokens.CornerMicro)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onGenerateContactSheet()
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GENERATE 7-UP CONTACT SHEET",
                    color = BurntBrass,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Shader Pass Stack
        LabSectionHeader(title = "04 // SHADER PASS STACK")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceRecess, AuroraInstrumentTokens.CornerMicro)
                .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerMicro)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "PASS 01: OesToFboPass (OES External Texture → RGB FBO)", color = WarmSlate, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(text = "PASS 02: Multi-Frame Computational Kernel (Temporal / Motion / Dx / Light)", color = WarmSlate, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(text = "PASS 03: FilmCurvePass (3D LUT + Film Curve + Grain + Vignette + Halation)", color = WarmSlate, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(text = "PASS 04: FocusPeakingPass (Sobel Edge Kernel + Color Matrix)", color = OpticalGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LabSectionHeader(title: String) {
    Text(
        text = title,
        color = WarmSlate,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun LabMetricRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MutedText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(
            text = value,
            color = if (highlight) OpticalGreen else ParchmentWhite,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

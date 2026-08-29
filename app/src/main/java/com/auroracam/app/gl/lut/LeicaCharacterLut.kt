package com.auroracam.app.gl.lut

import kotlin.math.abs

/**
 * Procedural LUT: "Leica Character" Look
 * Baked using perceptually uniform OKLch color space:
 * - High micro-contrast Hermite S-curve on perceptual lightness L
 * - Signature warm-amber midtone bell curve peaking at L = 0.45 ("Leica Glow")
 * - Punchy warm saturation (+14% in reds, ambers, and yellows) with crisp shadow separation
 * - Deep rich blacks without muddying
 */
object LeicaCharacterLut {
    const val LUT_NAME = "Leica"
    const val DEFAULT_SIZE = 33

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        return OklabColor.bakeOklchLut(size) { lch, _, _, _ ->
            var l = lch.l
            var c = lch.c
            var h = lch.h

            // 1. Cubic Hermite High-Microcontrast S-Curve
            l = OklabColor.smoothstep(-0.02f, 1.02f, l)

            // 2. Signature Leica Warm-Amber Midtone Glow (Peaking at L = 0.45)
            val midDiff = abs(l - 0.45f)
            val midBell = (1.0f - (midDiff / 0.45f).coerceIn(0f, 1f)).let { it * it * (3.0f - 2.0f * it) }
            l += 0.028f * midBell

            // 3. Selective Warm-Tone Punch (+14% chroma in warm sector)
            val isWarmSector = (h in 15f..90f) || (h >= 345f)
            if (isWarmSector) {
                c *= 1.14f
            } else {
                c *= 0.96f
            }

            // 4. Warm Midtone Hue Pull towards Leica Amber (52°)
            if (midBell > 0f && isWarmSector) {
                h += (52f - h) * 0.12f * midBell
            }

            // 5. Deep Shadow Sculpting (crisp black floor)
            if (l < 0.22f) {
                val shadowDrop = (1.0f - OklabColor.smoothstep(0.0f, 0.22f, l)) * 0.015f
                l -= shadowDrop
            }

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

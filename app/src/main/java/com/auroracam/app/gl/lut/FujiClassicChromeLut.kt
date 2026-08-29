package com.auroracam.app.gl.lut

import kotlin.math.pow

/**
 * Procedural LUT: "Fujifilm Classic Chrome"
 * Baked using perceptually uniform OKLch color space:
 * - Hard documentary contrast toe on lightness (L^1.18 with highlight retention)
 * - Derivative-continuous Color Chrome red density (deep velvety reds without seam kinks)
 * - Signature sky blue -> cyan/teal shift (hue angle rotation around 235°)
 * - Subdued, earthy olive foliage and muted global chroma (0.78x)
 */
object FujiClassicChromeLut {
    const val LUT_NAME = "Classic Chrome"
    const val DEFAULT_SIZE = 33

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        return OklabColor.bakeOklchLut(size) { lch, _, _, _ ->
            var l = lch.l
            var c = lch.c
            var h = lch.h

            // 1. Hard Documentary Contrast Toe
            val toeL = l.pow(1.18f)
            l = toeL * 0.65f + OklabColor.smoothstep(0f, 1f, l) * 0.35f

            // 2. Continuous-Derivative "Color Chrome" Red Density
            val redWeight = OklabColor.redDensityWeight(h, widthDeg = 55f)
            if (redWeight > 0f) {
                val chromaFactor = (c / 0.15f).coerceIn(0f, 1f)
                l -= 0.045f * redWeight * chromaFactor
                c *= (1.0f + 0.14f * redWeight)
            }

            // 3. Classic Chrome Sky Shift (Deep Blue -> Cyan/Teal)
            val skyWeight = OklabColor.hueBellWeight(h, centerHue = 235f, widthDeg = 40f)
            if (skyWeight > 0f) {
                h -= 22f * skyWeight
            }

            // 4. Earthy Olive Green & Desaturated Foliage
            val greenWeight = OklabColor.hueBellWeight(h, centerHue = 135f, widthDeg = 35f)
            if (greenWeight > 0f) {
                h -= 18f * greenWeight
                c *= (1.0f - 0.25f * greenWeight)
            }

            // 5. Subdued Global Documentary Saturation
            c *= 0.78f

            // 6. Subtle Warm Shadow Tint
            val shadowZone = 1.0f - OklabColor.smoothstep(0.0f, 0.35f, l)
            if (shadowZone > 0f) {
                l += 0.008f * shadowZone
            }

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

package com.auroracam.app.gl.lut

/**
 * Procedural LUT: "Kodak Portra 400"
 * Baked using perceptually uniform OKLch color space:
 * - Lifted matte black floor (+0.035) for authentic film negative response
 * - Flattering golden-peach skin tones with smooth melanin gradient
 * - Gentle cool-cyan shadow bias and warm ivory highlight roll-off
 * - Soft highlight compression preserving natural daylight rolloff
 */
object KodakPortra400Lut {
    const val LUT_NAME = "Portra 400"
    const val DEFAULT_SIZE = 33

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        return OklabColor.bakeOklchLut(size) { lch, _, _, _ ->
            var l = lch.l
            var c = lch.c
            var h = lch.h

            // 1. Film Negative Response Curve with Lifted Matte Black Floor
            l = 0.035f + (l * 0.72f + OklabColor.smoothstep(0f, 1f, l) * 0.28f) * 0.965f

            // 2. Golden-Peach Skin Tone Melanin Enhancer (Centered at 52° in OKLch)
            val skinWeight = OklabColor.hueBellWeight(h, centerHue = 52f, widthDeg = 30f)
            if (skinWeight > 0f) {
                h += (52f - h) * 0.20f * skinWeight
                c *= (1.0f + 0.10f * skinWeight)
            }

            // 3. Subtle Cool Cyan Shadow Bias (L < 0.35)
            val shadowZone = 1.0f - OklabColor.smoothstep(0.0f, 0.35f, l)
            if (shadowZone > 0f) {
                h += (195f - h) * 0.10f * shadowZone
            }

            // 4. Warm Ivory Highlight Shoulder (L > 0.75)
            if (l > 0.75f) {
                val highZone = (l - 0.75f) / 0.25f
                h += (60f - h) * 0.12f * highZone
            }

            // 5. Refined Organic Film Chroma
            c *= 0.92f

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

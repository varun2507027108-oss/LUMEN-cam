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
            l = 0.038f + (l * 0.70f + OklabColor.smoothstep(0f, 1f, l) * 0.30f) * 0.962f

            // 2. Golden-Peach Melanin & Warm Midtone Enhancer (+18% magnitude, wider 40° bell)
            val skinWeight = OklabColor.hueBellWeight(h, centerHue = 52f, widthDeg = 40f)
            if (skinWeight > 0f) {
                h += (52f - h) * 0.24f * skinWeight
                c *= (1.0f + 0.14f * skinWeight)
            }

            // 3. Subtle Cool Cyan Shadow Bias (L < 0.32)
            val shadowZone = 1.0f - OklabColor.smoothstep(0.0f, 0.32f, l)
            if (shadowZone > 0f) {
                h += (195f - h) * 0.08f * shadowZone
            }

            // 4. Warm Ivory Highlight Shoulder (Starting earlier at L > 0.68)
            if (l > 0.68f) {
                val highZone = (l - 0.68f) / 0.32f
                h += (58f - h) * 0.14f * highZone
            }

            // 5. Refined Organic Film Chroma
            c *= 0.94f

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

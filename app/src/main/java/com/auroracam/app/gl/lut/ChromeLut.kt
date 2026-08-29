package com.auroracam.app.gl.lut

/**
 * Procedural LUT: "Chrome"
 * Positive reversal film look baked in OKLch:
 * - High micro-contrast S-curve with deep toe
 * - Vibrant color purity boost (+18% chroma)
 * - Cool-toned shadow floor and crisp highlight pop
 */
object ChromeLut {
    const val LUT_NAME = "Chrome"
    const val DEFAULT_SIZE = 33

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        return OklabColor.bakeOklchLut(size) { lch, _, _, _ ->
            var l = lch.l
            var c = lch.c
            var h = lch.h

            // 1. High-contrast punchy S-curve
            l = OklabColor.smoothstep(-0.04f, 1.04f, l)

            // 2. Vibrant Reversal Film Chroma (+18%)
            c *= 1.18f

            // 3. Cool Shadow Tone (Shift shadows towards 230° in OKLch)
            val shadowZone = 1.0f - OklabColor.smoothstep(0.0f, 0.38f, l)
            if (shadowZone > 0f) {
                h += (230f - h) * 0.14f * shadowZone
            }

            // 4. Highlight crispness pop
            if (l > 0.70f) {
                val highZone = (l - 0.70f) / 0.30f
                l += 0.015f * highZone
            }

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

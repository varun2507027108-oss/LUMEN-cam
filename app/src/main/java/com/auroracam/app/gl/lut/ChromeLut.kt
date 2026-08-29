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

            // 1. High-contrast punchy S-curve (+12% steeper midtone gradient)
            l = OklabColor.smoothstep(-0.07f, 1.07f, l)

            // 2. Vibrant Reversal Film Chroma (+22%)
            c *= 1.22f

            // 3. Deep Shadow Sculpting (Crisp digital contrasty blacks in flat light)
            if (l < 0.35f) {
                val shadowCut = (1.0f - OklabColor.smoothstep(0.0f, 0.35f, l)) * 0.024f
                l -= shadowCut
            }

            // 4. Cool Shadow Tone (Shift shadows towards 232° in OKLch)
            val shadowZone = 1.0f - OklabColor.smoothstep(0.0f, 0.40f, l)
            if (shadowZone > 0f) {
                h += (232f - h) * 0.16f * shadowZone
            }

            // 5. Highlight crispness pop
            if (l > 0.68f) {
                val highZone = (l - 0.68f) / 0.32f
                l += 0.020f * highZone
            }

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

package com.auroracam.app.gl.lut

import kotlin.math.abs
import kotlin.math.pow

/**
 * Procedural LUT: "Aurora Warm"
 * Baked using perceptually uniform OKLch color space:
 * - Golden sunset warmth with amber midtones (hue pull towards 55° in OKLch)
 * - Gentle highlight compression and rational shoulder roll-off (L > 0.72)
 * - True-black anchored matte floor in lower-midtones (0.05..0.35)
 */
object AuroraWarmLut {
    const val LUT_NAME = "Warm"
    const val DEFAULT_SIZE = 33

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        return OklabColor.bakeOklchLut(size) { lch, _, _, _ ->
            var l = lch.l
            var c = lch.c
            var h = lch.h

            // 1. Contrast curve with early highlight roll-off
            l = l * 0.60f + OklabColor.smoothstep(0f, 1f, l) * 0.40f

            if (l > 0.72f) {
                val shoulderT = (l - 0.72f) / 0.28f
                val comp = 1.0f - (1.0f - shoulderT).pow(1.4f)
                l -= comp * 0.05f
            }

            // 2. Golden Hour Midtone Warmth (peaking at L = 0.50)
            val mid = 1.0f - abs(2.0f * l - 1.0f).coerceIn(0f, 1f)
            val midBell = mid * mid * (3.0f - 2.0f * mid)
            h += (55f - h) * 0.22f * midBell
            c *= (1.0f + 0.12f * midBell)

            // 3. Matte floor with true-black anchor
            val matteLift = 0.045f * OklabColor.smoothstep(0.0f, 0.08f, l) * (1.0f - OklabColor.smoothstep(0.08f, 0.45f, l))
            l += matteLift

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

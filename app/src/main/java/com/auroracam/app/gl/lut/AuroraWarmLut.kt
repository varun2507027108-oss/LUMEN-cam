package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Procedural LUT: "Aurora Warm" v2
 * Bolder filmic palette with distinct golden midtones, olive yellow-greens, cerulean blues,
 * true-black anchored matte floor, and gentle highlight compression.
 */
object AuroraWarmLut {
    const val LUT_NAME = "Warm"
    const val DEFAULT_SIZE = 33

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    /** smoothstep(0, 1, x) — cubic ease with zero slope at both ends. */
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp01((x - edge0) / (edge1 - edge0))
        return t * t * (3.0f - 2.0f * t)
    }

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        val totalEntries = size * size * size
        val buffer = ByteBuffer.allocateDirect(totalEntries * 4).order(ByteOrder.nativeOrder())
        val step = 1.0f / (size - 1).toFloat()

        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val r0 = r * step
                    val g0 = g * step
                    val b0 = b * step

                    // Input Rec.709 luminance
                    val l = 0.2126f * r0 + 0.7152f * g0 + 0.0722f * b0

                    // 1) Contrast S-curve per channel with early highlight rolloff (starting at 0.72)
                    var cr = r0 * 0.55f + smoothstep(0f, 1f, r0) * 0.45f
                    var cg = g0 * 0.55f + smoothstep(0f, 1f, g0) * 0.45f
                    var cb = b0 * 0.55f + smoothstep(0f, 1f, b0) * 0.45f

                    // Highlight shoulder compression starting at 0.72
                    if (l > 0.72f) {
                        val shoulderT = (l - 0.72f) / 0.28f
                        val comp = 1.0f - (1.0f - shoulderT).pow(1.4f)
                        cr = cr * (1.0f - comp * 0.08f)
                        cg = cg * (1.0f - comp * 0.06f)
                        cb = cb * (1.0f - comp * 0.05f)
                    }

                    // 2) Bolder Midtone Warmth (+8% R, -6% B, peaking at l = 0.5)
                    val mid = 1.0f - abs(2.0f * l - 1.0f).coerceIn(0f, 1f)
                    val midBell = mid * mid * (3.0f - 2.0f * mid) // Smooth bell
                    cr += 0.080f * midBell
                    cg += 0.025f * midBell
                    cb -= 0.060f * midBell

                    // 3) Greens -> Olive shift (15% hue shift in yellow-green band)
                    if (g0 > b0 && (r0 + g0) > 0.3f) {
                        val yellowGreenness = max(0.0f, g0 - b0) * smoothstep(0.1f, 0.7f, g0)
                        cg -= yellowGreenness * 0.15f
                        cr += yellowGreenness * 0.04f
                    }

                    // 4) Blues -> Cerulean (12% violet removal / shift red out of blue band)
                    if (b0 > g0) {
                        val blueness = (b0 - g0).coerceAtLeast(0f)
                        cr -= blueness * 0.12f
                        cg += blueness * 0.04f
                    }

                    // 5) Matte Floor with True-Black Anchor:
                    // True black (l=0) stays black; lower-midtones (l=0.05..0.35) get 0.06 lift
                    val matteLift = 0.060f * smoothstep(0.0f, 0.08f, l) * (1.0f - smoothstep(0.08f, 0.45f, l))
                    cr += matteLift * 1.05f
                    cg += matteLift * 0.95f
                    cb += matteLift * 1.10f

                    // 6) Clamp and store RGBA8888
                    buffer.put((clamp01(cr) * 255f + 0.5f).toInt().toByte())
                    buffer.put((clamp01(cg) * 255f + 0.5f).toInt().toByte())
                    buffer.put((clamp01(cb) * 255f + 0.5f).toInt().toByte())
                    buffer.put(255.toByte())
                }
            }
        }
        buffer.rewind()

        return ParsedCube(
            size = size,
            data = buffer,
            domainMin = floatArrayOf(0f, 0f, 0f),
            domainMax = floatArrayOf(1f, 1f, 1f)
        )
    }
}

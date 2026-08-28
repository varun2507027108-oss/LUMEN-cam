package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Procedural LUT: "Chrome"
 * High-vibrancy, punchy positive reversal film look.
 * Deep contrast S-curve with deep toe, +18% saturation boost, cool-toned shadows, and crisp highlights.
 */
object ChromeLut {
    const val LUT_NAME = "Chrome"
    const val DEFAULT_SIZE = 33

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

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

                    // Input luminance
                    val l = 0.2126f * r0 + 0.7152f * g0 + 0.0722f * b0

                    // 1) Punchy S-curve with deeper contrast toe
                    var cr = smoothstep(-0.05f, 1.05f, r0)
                    var cg = smoothstep(-0.05f, 1.05f, g0)
                    var cb = smoothstep(-0.05f, 1.05f, b0)

                    // 2) Saturation boost (+18%)
                    val newL = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                    cr = newL + (cr - newL) * 1.18f
                    cg = newL + (cg - newL) * 1.18f
                    cb = newL + (cb - newL) * 1.18f

                    // 3) Cool shadow tone (-5% warm / +5% blue lift in deep shadows)
                    val shadowZone = (1.0f - smoothstep(0.0f, 0.40f, l))
                    cb += 0.050f * shadowZone
                    cr -= 0.030f * shadowZone

                    // 4) Clean highlight pop
                    val highZone = smoothstep(0.70f, 1.0f, l)
                    cr += 0.020f * highZone
                    cg += 0.015f * highZone

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

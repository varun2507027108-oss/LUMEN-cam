package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Procedural LUT: "Fujifilm Classic Chrome"
 * Documentary film aesthetic, muted pastel colors, hard documentary contrast toe (x^1.22),
 * distinctive blue-to-cyan/teal sky shift, deep rich warm reds, and desaturated greens/yellows.
 *
 * Core Math:
 * - Hard shadow toe curve: x^1.22
 * - Sky blue shift to cyan/teal: g += 0.14 * blueDominance, r -= 0.06 * blueDominance
 * - Subdued global saturation (0.78x) with retained red luminance depth
 * - Earthy warm midtones
 */
object FujiClassicChromeLut {
    const val LUT_NAME = "Classic Chrome"
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

                    // Input Rec.709 Luminance
                    val l0 = 0.2126f * r0 + 0.7152f * g0 + 0.0722f * b0

                    // 1) Hard Documentary Contrast Toe (x^1.22 power curve on lower/mid levels)
                    var cr = r0.pow(1.22f)
                    var cg = g0.pow(1.22f)
                    var cb = b0.pow(1.22f)

                    // S-curve blend for highlight retention
                    cr = cr * 0.65f + smoothstep(0f, 1f, r0) * 0.35f
                    cg = cg * 0.65f + smoothstep(0f, 1f, g0) * 0.35f
                    cb = cb * 0.65f + smoothstep(0f, 1f, b0) * 0.35f

                    // 2) Distinctive Classic Chrome Sky Shift (Deep Blue -> Cyan/Teal)
                    if (b0 > g0 && b0 > 0.15f) {
                        val blueDominance = ((b0 - max(r0, g0)) / b0).coerceIn(0f, 1f) * smoothstep(0.15f, 0.9f, b0)
                        cg += 0.14f * blueDominance
                        cr -= 0.06f * blueDominance
                    }

                    // 3) Earthy Warm Reds & Desaturated Greens
                    if (r0 > g0 && r0 > b0) {
                        // Deep rich warm red
                        cr += 0.03f * smoothstep(0.2f, 0.8f, r0)
                    } else if (g0 > r0 && g0 > b0) {
                        // Olive/desaturated green
                        cr += 0.025f * (g0 - r0)
                        cg -= 0.030f * (g0 - r0)
                    }

                    // 4) Subdued Global Documentary Saturation (0.78x chroma)
                    val l1 = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                    cr = l1 + (cr - l1) * 0.78f
                    cg = l1 + (cg - l1) * 0.78f
                    cb = l1 + (cb - l1) * 0.78f

                    // 5) Subtle Warm Shadow Tint
                    val shadowZone = 1.0f - smoothstep(0.0f, 0.35f, l0)
                    cr += 0.012f * shadowZone
                    cb -= 0.010f * shadowZone

                    // Clamp & store RGBA8888
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

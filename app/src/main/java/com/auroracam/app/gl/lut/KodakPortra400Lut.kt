package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Procedural LUT: "Kodak Portra 400"
 * Renowned portrait negative film emulation with warm golden-peach skin tones,
 * lifted matte shadows with subtle cyan-olive coolness, and creamy ivory highlight roll-off.
 *
 * Core Math:
 * - Lifted matte black floor (+0.035)
 * - Golden-peach skin tone enhancer in midtones
 * - Cyan/olive shadow tinting for classic Kodak contrast separation
 * - Warm ivory highlight roll-off (L > 0.75)
 */
object KodakPortra400Lut {
    const val LUT_NAME = "Portra 400"
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

                    // 1) Gentle Negative Filmic S-Curve Tone Response
                    var cr = r0 * 0.60f + smoothstep(0f, 1f, r0) * 0.40f
                    var cg = g0 * 0.60f + smoothstep(0f, 1f, g0) * 0.40f
                    var cb = b0 * 0.60f + smoothstep(0f, 1f, b0) * 0.40f

                    // 2) Lifted Matte Black Floor (+0.035 lift with smooth transition to midtones)
                    val matteLift = 0.035f * (1.0f - smoothstep(0.0f, 0.40f, l0))
                    cr += matteLift * 0.95f
                    cg += matteLift * 1.05f
                    cb += matteLift * 1.15f // Subtle cyan/cool lift in shadows

                    // 3) Golden-Peach Skin Tone Enhancer (Peaking around L = 0.50)
                    if (r0 > g0 && g0 > b0) {
                        val skinDelta = ((r0 - g0) * (g0 - b0)).coerceAtLeast(0f) * 6.0f
                        val midBell = 1.0f - abs(2.0f * l0 - 1.0f).coerceIn(0f, 1f)
                        val skinFactor = skinDelta * midBell
                        cr += 0.040f * skinFactor
                        cg += 0.015f * skinFactor
                        cb -= 0.030f * skinFactor
                    }

                    // 4) Cyan/Olive Shadow Tinting (L < 0.35)
                    if (l0 < 0.35f) {
                        val shadowCool = (1.0f - smoothstep(0.0f, 0.35f, l0))
                        cb += 0.020f * shadowCool
                        cg += 0.012f * shadowCool
                        cr -= 0.015f * shadowCool
                    }

                    // 5) Warm Ivory Highlight Roll-Off (L > 0.75)
                    if (l0 > 0.75f) {
                        val highWarm = smoothstep(0.75f, 1.0f, l0)
                        cr += 0.020f * highWarm
                        cg += 0.015f * highWarm
                        cb -= 0.010f * highWarm
                    }

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

package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Procedural LUT: "Hasselblad Natural Color Solution" (HNCS)
 * Medium-format fidelity, true-to-life skin tones, extended highlight roll-off,
 * and clean, uncrushed neutral-cool shadows.
 *
 * Core Math:
 * - Linearized Rec.709 luminance response
 * - Rational asymptotic highlight shoulder (x > 0.55)
 * - Restrained global chroma (0.94x) preserving high-fidelity skin tones
 * - Subdued, clean neutral-cool shadow floor
 */
object HasselbladNaturalLut {
    const val LUT_NAME = "Hasselblad"
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

                    // 1) Natural Medium-Format Tone Curve (subtle contrast with smooth linear midtones)
                    var cr = r0 * 0.70f + smoothstep(0f, 1f, r0) * 0.30f
                    var cg = g0 * 0.70f + smoothstep(0f, 1f, g0) * 0.30f
                    var cb = b0 * 0.70f + smoothstep(0f, 1f, b0) * 0.30f

                    // 2) Rational Asymptotic Highlight Shoulder (x > 0.55) for smooth roll-off
                    if (l0 > 0.55f) {
                        val shoulderT = (l0 - 0.55f) / 0.45f
                        val shoulderComp = shoulderT / (1.0f + 0.35f * shoulderT)
                        val highFactor = 1.0f - shoulderComp * 0.06f
                        cr *= highFactor
                        cg *= highFactor
                        cb *= highFactor
                    }

                    // 3) Restrained Global Chroma (0.94x) — eliminates artificial neon saturation
                    val l1 = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                    cr = l1 + (cr - l1) * 0.94f
                    cg = l1 + (cg - l1) * 0.94f
                    cb = l1 + (cb - l1) * 0.94f

                    // 4) Natural Skin Tone Harmonization (Warm yellow-red melanin alignment)
                    if (r0 > g0 && g0 > b0) {
                        val skinFactor = ((r0 - g0) * (g0 - b0)).coerceAtLeast(0f) * 4.0f
                        cr += 0.015f * skinFactor
                        cg += 0.008f * skinFactor
                        cb -= 0.010f * skinFactor
                    }

                    // 5) Clean Neutral-Cool Shadow Floor (preserves deep shadow separation)
                    val shadowZone = 1.0f - smoothstep(0.0f, 0.35f, l0)
                    cb += 0.015f * shadowZone
                    cr -= 0.008f * shadowZone

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

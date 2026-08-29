package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Procedural LUT: "Leica Character" Look
 * High micro-contrast, signature warm-amber midtones ("Leica Glow"),
 * deep rich blacks, and punchy yet organic color separation.
 *
 * Core Math:
 * - Cubic Hermite S-curve tonal contrast
 * - Warm-amber midtone bell curve peaking at L = 0.45 (+0.055 R, +0.015 G, -0.045 B)
 * - Warm hue chroma boost (+12% in reds/yellows, restrained in cyans/blues)
 * - Controlled deep shadow compression with crisp micro-contrast
 */
object LeicaCharacterLut {
    const val LUT_NAME = "Leica"
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

                    // 1) Cubic Hermite High-Microcontrast S-Curve
                    var cr = smoothstep(-0.02f, 1.02f, r0)
                    var cg = smoothstep(-0.02f, 1.02f, g0)
                    var cb = smoothstep(-0.02f, 1.02f, b0)

                    // 2) Signature Leica Warm-Amber Midtone Glow (Peaking at L = 0.45)
                    val midDiff = abs(l0 - 0.45f)
                    val midBell = (1.0f - (midDiff / 0.45f).coerceIn(0f, 1f)).let { it * it * (3.0f - 2.0f * it) }
                    cr += 0.055f * midBell
                    cg += 0.015f * midBell
                    cb -= 0.045f * midBell

                    // 3) Selective Warm-Tone Saturation Boost & Cool Tone Restraint
                    val l1 = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                    if (cr > cb) {
                        // Warm region: boost saturation by +12%
                        val warmBoost = 1.12f
                        cr = l1 + (cr - l1) * warmBoost
                        cg = l1 + (cg - l1) * warmBoost
                        cb = l1 + (cb - l1) * warmBoost
                    } else {
                        // Cool/Blue region: slightly restrain saturation by -4%
                        val coolRestraint = 0.96f
                        cr = l1 + (cr - l1) * coolRestraint
                        cg = l1 + (cg - l1) * coolRestraint
                        cb = l1 + (cb - l1) * coolRestraint
                    }

                    // 4) Deep Shadow Sculpting (crisp black floor without muddying)
                    if (l0 < 0.25f) {
                        val shadowDarken = (1.0f - smoothstep(0.0f, 0.25f, l0)) * 0.02f
                        cr -= shadowDarken * 0.8f
                        cg -= shadowDarken * 0.8f
                        cb -= shadowDarken * 1.2f // Subtle warm shadow bias
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

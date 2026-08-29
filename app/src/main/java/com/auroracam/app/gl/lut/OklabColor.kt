package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Perceptually uniform OKLab / OKLch color space conversion engine and LUT baking pipeline.
 *
 * References:
 * - Björn Ottosson, "A perceptual color space for image processing" (2020)
 * - Hue linearity prevents chromatic drift (Abney & Bezold–Brücke effects)
 * - Lightness-Chroma decoupling enables orthogonal contrast and saturation grading
 */

data class Oklab(
    val l: Float, // Perceptual Lightness [0, 1]
    val a: Float, // Green (-a) to Red (+a)
    val b: Float  // Blue (-b) to Yellow (+b)
)

data class Oklch(
    val l: Float, // Perceptual Lightness [0, 1]
    val c: Float, // Chroma (Saturation / Color Purity) >= 0
    val h: Float  // Hue angle in degrees [0, 360)
)

object OklabColor {

    fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    /**
     * Non-linear sRGB [0, 1] to Linear sRGB (Inverse EOTF / Gamma Expansion).
     */
    fun srgbToLinear(c: Float): Float {
        val x = clamp01(c)
        return if (x <= 0.04045f) {
            x / 12.92f
        } else {
            ((x + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    /**
     * Linear sRGB to Non-linear sRGB [0, 1] (Forward EOTF / Gamma Compression).
     */
    fun linearToSrgb(c: Float): Float {
        val x = clamp01(c)
        return if (x <= 0.0031308f) {
            12.92f * x
        } else {
            1.055f * x.pow(1.0f / 2.4f) - 0.055f
        }
    }

    /**
     * Linear sRGB (0..1) to OKLab (L, a, b).
     * Strictly clamps inputs >= 0 before cube root to guarantee zero NaN corruption.
     */
    fun linearSrgbToOklab(r: Float, g: Float, b: Float): Oklab {
        val rClamped = max(0f, r)
        val gClamped = max(0f, g)
        val bClamped = max(0f, b)

        // sRGB Linear to LMS matrix
        val lLin = 0.4122214708f * rClamped + 0.5363325363f * gClamped + 0.0514459929f * bClamped
        val mLin = 0.2119034982f * rClamped + 0.6806995451f * gClamped + 0.1073969566f * bClamped
        val sLin = 0.0883024619f * rClamped + 0.2817188376f * gClamped + 0.6299787005f * bClamped

        // Non-linear cube root compression with defensive zero lower bound
        val l = max(0f, lLin).pow(1.0f / 3.0f)
        val m = max(0f, mLin).pow(1.0f / 3.0f)
        val s = max(0f, sLin).pow(1.0f / 3.0f)

        return Oklab(
            l = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            b = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        )
    }

    /**
     * OKLab (L, a, b) to Linear sRGB.
     */
    fun oklabToLinearSrgb(lab: Oklab): Triple<Float, Float, Float> {
        val l = lab.l + 0.3963377774f * lab.a + 0.2158037573f * lab.b
        val m = lab.l - 0.1055613458f * lab.a - 0.0638541728f * lab.b
        val s = lab.l - 0.0894841775f * lab.a - 1.2914855480f * lab.b

        val lCubed = l * l * l
        val mCubed = m * m * m
        val sCubed = s * s * s

        val rLin = +4.0767416621f * lCubed - 3.3077115913f * mCubed + 0.2309699292f * sCubed
        val gLin = -1.2684380046f * lCubed + 2.6097574011f * mCubed - 0.3413193965f * sCubed
        val bLin = -0.0041960863f * lCubed - 0.7034186147f * mCubed + 1.7076147010f * sCubed

        return Triple(rLin, gLin, bLin)
    }

    /**
     * OKLab (L, a, b) to Cylindrical OKLch (L, C, h).
     */
    fun oklabToOklch(lab: Oklab): Oklch {
        val c = sqrt(lab.a * lab.a + lab.b * lab.b)
        var h = Math.toDegrees(atan2(lab.b.toDouble(), lab.a.toDouble())).toFloat()
        if (h < 0f) h += 360f
        return Oklch(l = lab.l, c = c, h = h)
    }

    /**
     * Cylindrical OKLch (L, C, h) to OKLab (L, a, b).
     */
    fun oklchToOklab(lch: Oklch): Oklab {
        val hRad = Math.toRadians(lch.h.toDouble())
        return Oklab(
            l = lch.l,
            a = (lch.c * cos(hRad)).toFloat(),
            b = (lch.c * sin(hRad)).toFloat()
        )
    }

    /**
     * Direct conversion: sRGB [0..1] -> OKLch.
     */
    fun srgbToOklch(r: Float, g: Float, b: Float): Oklch {
        val rLin = srgbToLinear(r)
        val gLin = srgbToLinear(g)
        val bLin = srgbToLinear(b)
        val lab = linearSrgbToOklab(rLin, gLin, bLin)
        return oklabToOklch(lab)
    }

    /**
     * Direct conversion: OKLch -> sRGB [0..1].
     */
    fun oklchToSrgb(lch: Oklch): Triple<Float, Float, Float> {
        val lab = oklchToOklab(lch)
        val (rLin, gLin, bLin) = oklabToLinearSrgb(lab)
        return Triple(
            linearToSrgb(rLin),
            linearToSrgb(gLin),
            linearToSrgb(bLin)
        )
    }

    // =========================================================================
    // CONTINUOUS HUE INTERPOLATION & SELECTION HELPERS
    // =========================================================================

    /**
     * Calculates the shortest angular distance (in degrees [0..180]) between two hue angles.
     */
    fun angularHueDistance(h1: Float, h2: Float): Float {
        val d = abs(h1 - h2) % 360f
        return if (d > 180f) 360f - d else d
    }

    /**
     * Smooth Hermite bell curve weight around a target hue with a specified angular radius.
     * Guaranteed zero discontinuity and continuous first derivative.
     */
    fun hueBellWeight(hDeg: Float, centerHue: Float, widthDeg: Float): Float {
        val dist = angularHueDistance(hDeg, centerHue)
        if (dist >= widthDeg) return 0.0f
        val t = 1.0f - (dist / widthDeg)
        return t * t * (3.0f - 2.0f * t)
    }

    /**
     * Smooth continuous Red Density / Color Chrome weight across the 0°/360° boundary.
     * Prevents slope/derivative mismatch at the wrap-around seam.
     */
    fun redDensityWeight(hDeg: Float, widthDeg: Float = 55f): Float {
        val hueDist = min(abs(hDeg), 360f - abs(hDeg))
        val t = (1.0f - hueDist / widthDeg).coerceIn(0f, 1f)
        return t * t * (3.0f - 2.0f * t)
    }

    /**
     * Hermite smoothstep interpolation.
     */
    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp01((x - edge0) / (edge1 - edge0))
        return t * t * (3.0f - 2.0f * t)
    }

    // =========================================================================
    // HIGH-PERFORMANCE PROCEDURAL LUT BAKING PIPELINE
    // =========================================================================

    /**
     * Bakes a complete 33³ (or custom size) `.cube` data structure by running perceptual OKLch
     * transformations for each lattice point. Zero runtime GPU cost — all math executes at bake time.
     */
    inline fun bakeOklchLut(
        size: Int = 33,
        crossinline transform: (lch: Oklch, r0: Float, g0: Float, b0: Float) -> Oklch
    ): ParsedCube {
        val totalEntries = size * size * size
        val buffer = ByteBuffer.allocateDirect(totalEntries * 4).order(ByteOrder.nativeOrder())
        val step = 1.0f / (size - 1).toFloat()

        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val r0 = r * step
                    val g0 = g * step
                    val b0 = b * step

                    val inLch = srgbToOklch(r0, g0, b0)
                    val outLch = transform(inLch, r0, g0, b0)
                    val (outR, outG, outB) = oklchToSrgb(outLch)

                    buffer.put((clamp01(outR) * 255f + 0.5f).toInt().toByte())
                    buffer.put((clamp01(outG) * 255f + 0.5f).toInt().toByte())
                    buffer.put((clamp01(outB) * 255f + 0.5f).toInt().toByte())
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

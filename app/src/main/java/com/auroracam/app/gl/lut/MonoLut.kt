package com.auroracam.app.gl.lut

/**
 * Procedural LUT: "Mono"
 * Classic Black & White Panatomic / Tri-X film emulation baked in OKLch:
 * - Classic Red-filter spectral luma weighting in Linear sRGB
 * - Zero chroma in OKLch for pure perceptual neutrality
 * - Rich Tri-X silver-halide S-curve contrast on lightness L with anchored matte floor
 */
object MonoLut {
    const val LUT_NAME = "Mono"
    const val DEFAULT_SIZE = 33

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        return OklabColor.bakeOklchLut(size) { _, r0, g0, b0 ->
            // Red-filter spectral weighting in linear RGB
            val rLin = OklabColor.srgbToLinear(r0)
            val gLin = OklabColor.srgbToLinear(g0)
            val bLin = OklabColor.srgbToLinear(b0)
            val bwLinear = 0.60f * rLin + 0.30f * gLin + 0.10f * bLin
            val bwSrgb = OklabColor.linearToSrgb(bwLinear)

            val baseLch = OklabColor.srgbToOklch(bwSrgb, bwSrgb, bwSrgb)
            var l = baseLch.l

            // Dynamic Tri-X S-curve
            l = OklabColor.smoothstep(0.02f, 0.98f, l)

            // Matte shadow floor with true-black anchor
            val matte = 0.04f * OklabColor.smoothstep(0.0f, 0.10f, l) * (1.0f - OklabColor.smoothstep(0.10f, 0.50f, l))
            l += matte

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = 0.0f, // Pure monochrome
                h = 0.0f
            )
        }
    }
}

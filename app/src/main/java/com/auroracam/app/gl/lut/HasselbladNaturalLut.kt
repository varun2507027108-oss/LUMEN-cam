package com.auroracam.app.gl.lut

/**
 * Procedural LUT: "Hasselblad Natural Color Solution" (HNCS)
 * Baked using perceptually uniform OKLch color space:
 * - Medium-format tonal fidelity with rational asymptotic highlight shoulder (L > 0.55)
 * - True-to-life skin tone hue stability (centered at 48° in OKLch)
 * - Restrained global chroma (0.94x) preserving organic texture and depth
 * - Clean neutral-cool shadow floor preserving deep shadow separation
 */
object HasselbladNaturalLut {
    const val LUT_NAME = "Hasselblad"
    const val DEFAULT_SIZE = 33

    fun generate(size: Int = DEFAULT_SIZE): ParsedCube {
        return OklabColor.bakeOklchLut(size) { lch, _, _, _ ->
            var l = lch.l
            var c = lch.c
            var h = lch.h

            // 1. Natural Medium-Format Tone Curve (subtle contrast with linear midtones)
            l = l * 0.75f + OklabColor.smoothstep(0f, 1f, l) * 0.25f

            // 2. Rational Asymptotic Highlight Shoulder (L > 0.55) for smooth roll-off
            if (l > 0.55f) {
                val shoulderT = (l - 0.55f) / 0.45f
                val shoulderComp = shoulderT / (1.0f + 0.35f * shoulderT)
                l -= shoulderComp * 0.045f
            }

            // 3. Natural Skin Tone Harmonization (HNCS Melanin Alignment)
            val skinWeight = OklabColor.hueBellWeight(h, centerHue = 48f, widthDeg = 25f)
            if (skinWeight > 0f) {
                h += (48f - h) * 0.15f * skinWeight
                c *= (1.0f + 0.04f * skinWeight)
            }

            // 4. Restrained Global Chroma for Medium-Format Organic Purity
            c *= 0.94f

            // 5. Clean Neutral-Cool Shadow Floor
            val shadowZone = 1.0f - OklabColor.smoothstep(0.0f, 0.30f, l)
            if (shadowZone > 0f) {
                // Subtle cool tint in deep shadows (shift towards 240°)
                h += (240f - h) * 0.06f * shadowZone
            }

            Oklch(
                l = l.coerceIn(0f, 1f),
                c = c.coerceAtLeast(0f),
                h = (h % 360f + 360f) % 360f
            )
        }
    }
}

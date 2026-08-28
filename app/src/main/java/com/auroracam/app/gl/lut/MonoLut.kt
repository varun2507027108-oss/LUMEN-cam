package com.auroracam.app.gl.lut

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Procedural LUT: "Mono"
 * High-contrast Black & White film emulation with classic Red-filter spectral response.
 * Weighting: 0.60 Red + 0.30 Green + 0.10 Blue to darken skies and dramatically sculpt skin and architecture.
 */
object MonoLut {
    const val LUT_NAME = "Mono"
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

                    // Red-filter spectral weighting (0.60 R, 0.30 G, 0.10 B)
                    val bwLuma = 0.60f * r0 + 0.30f * g0 + 0.10f * b0

                    // Dynamic filmic S-curve contrast
                    var bw = smoothstep(0.02f, 0.98f, bwLuma)

                    // Matte shadow floor (lifted deep blacks 0.05 while preserving zero-anchor)
                    val matte = 0.05f * smoothstep(0.0f, 0.10f, bwLuma) * (1.0f - smoothstep(0.10f, 0.50f, bwLuma))
                    bw += matte

                    val v = clamp01(bw)
                    val byteVal = (v * 255f + 0.5f).toInt().toByte()

                    buffer.put(byteVal)
                    buffer.put(byteVal)
                    buffer.put(byteVal)
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

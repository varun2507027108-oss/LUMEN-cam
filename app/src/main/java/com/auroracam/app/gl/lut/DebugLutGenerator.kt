package com.auroracam.app.gl.lut

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.util.Locale

/**
 * Generates test .cube files in filesDir/Look/ for verification:
 * - identity.cube (no change)
 * - mono.cube (desaturates to Rec.709 luma, verifying parser order & 3D sampler)
 * - invert.cube (inverts RGB)
 */
object DebugLutGenerator {
    private const val TAG = "DebugLutGenerator"

    suspend fun generateDebugCubes(context: Context): List<File> = withContext(Dispatchers.IO) {
        val lookDir = File(context.filesDir, "Look").apply { if (!exists()) mkdirs() }
        val files = mutableListOf<File>()

        try {
            // 1. identity.cube
            val identityFile = File(lookDir, "identity.cube")
            writeCubeFile(identityFile, "Identity Debug LUT", 33) { r, g, b ->
                Triple(r, g, b)
            }
            files.add(identityFile)

            // 2. mono.cube
            val monoFile = File(lookDir, "mono.cube")
            writeCubeFile(monoFile, "Monochrome Debug LUT", 33) { r, g, b ->
                val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                Triple(l, l, l)
            }
            files.add(monoFile)

            // 3. invert.cube
            val invertFile = File(lookDir, "invert.cube")
            writeCubeFile(invertFile, "Invert Debug LUT", 33) { r, g, b ->
                Triple(1.0f - r, 1.0f - g, 1.0f - b)
            }
            files.add(invertFile)

            Log.i(TAG, "Generated 3 debug .cube files in ${lookDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate debug .cube files", e)
        }

        files
    }

    private fun writeCubeFile(
        file: File,
        title: String,
        size: Int,
        colorTransform: (Float, Float, Float) -> Triple<Float, Float, Float>
    ) {
        FileWriter(file).use { writer ->
            writer.write("# $title\n")
            writer.write("LUT_3D_SIZE $size\n")
            writer.write("DOMAIN_MIN 0.0 0.0 0.0\n")
            writer.write("DOMAIN_MAX 1.0 1.0 1.0\n")

            val step = 1.0f / (size - 1).toFloat()
            // Data order: Red fastest, Green next, Blue slowest
            for (b in 0 until size) {
                for (g in 0 until size) {
                    for (r in 0 until size) {
                        val r0 = r * step
                        val g0 = g * step
                        val b0 = b * step

                        val (tr, tg, tb) = colorTransform(r0, g0, b0)
                        writer.write(
                            String.format(
                                Locale.US,
                                "%.6f %.6f %.6f\n",
                                tr.coerceIn(0f, 1f),
                                tg.coerceIn(0f, 1f),
                                tb.coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
        }
    }
}

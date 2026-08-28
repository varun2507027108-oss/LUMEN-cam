package com.auroracam.app.gl.lut

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedCube(
    val size: Int,
    val data: ByteBuffer,
    val domainMin: FloatArray,
    val domainMax: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ParsedCube
        if (size != other.size) return false
        if (data != other.data) return false
        if (!domainMin.contentEquals(other.domainMin)) return false
        if (!domainMax.contentEquals(other.domainMax)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + data.hashCode()
        result = 31 * result + domainMin.contentHashCode()
        result = 31 * result + domainMax.contentHashCode()
        return result
    }
}

/**
 * Adobe/IRIDAS .cube 3D LUT Parser.
 *
 * .cube data rows are ordered: Red changes fastest, Green next, Blue slowest.
 * We load row-by-row into a direct RGBA8888 ByteBuffer for direct consumption by GLES30.glTexImage3D.
 */
object CubeParser {
    private const val TAG = "CubeParser"

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    fun parse(inputStream: InputStream): ParsedCube {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var size = 0
        val domainMin = floatArrayOf(0f, 0f, 0f)
        val domainMax = floatArrayOf(1f, 1f, 1f)

        // Read all entries
        val rawRgb = ArrayList<Float>(33 * 33 * 33 * 3)

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue

                val parts = line.split("\\s+".toRegex())
                val kw = parts[0].uppercase()

                when {
                    kw == "TITLE" || kw == "COMMENT" -> continue
                    kw == "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUTs are not supported; requires 3D LUT")
                    kw == "LUT_3D_SIZE" -> {
                        val n = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid LUT_3D_SIZE: ${parts[1]}")
                        if (n < 2 || n > 64) throw IllegalArgumentException("LUT_3D_SIZE $n out of bounds [2, 64]")
                        if (size != 0 && size != n) throw IllegalArgumentException("Conflicting LUT_3D_SIZE declarations")
                        size = n
                    }
                    kw == "DOMAIN_MIN" -> {
                        if (parts.size >= 4) {
                            domainMin[0] = parts[1].toFloat()
                            domainMin[1] = parts[2].toFloat()
                            domainMin[2] = parts[3].toFloat()
                        }
                    }
                    kw == "DOMAIN_MAX" -> {
                        if (parts.size >= 4) {
                            domainMax[0] = parts[1].toFloat()
                            domainMax[1] = parts[2].toFloat()
                            domainMax[2] = parts[3].toFloat()
                        }
                    }
                    else -> {
                        // Data row: 3 color channels (R, G, B)
                        if (parts.size >= 3) {
                            val r = parts[0].toFloatOrNull()
                            val g = parts[1].toFloatOrNull()
                            val b = parts[2].toFloatOrNull()
                            if (r != null && g != null && b != null) {
                                rawRgb.add(r)
                                rawRgb.add(g)
                                rawRgb.add(b)
                            }
                        }
                    }
                }
            }
        }

        if (size == 0) {
            // Infer from count if missing (e.g. 35937 triples = 33^3)
            val count = rawRgb.size / 3
            val root = Math.cbrt(count.toDouble()).toInt()
            if (root * root * root == count) {
                size = root
                Log.w(TAG, "Missing LUT_3D_SIZE; inferred size $size from row count $count")
            } else {
                throw IllegalArgumentException("Missing LUT_3D_SIZE keyword and non-cubic entry count")
            }
        }

        val expectedEntries = size * size * size
        val actualEntries = rawRgb.size / 3
        if (actualEntries != expectedEntries) {
            throw IllegalArgumentException("LUT row count mismatch: expected $expectedEntries, found $actualEntries")
        }

        val byteBuffer = ByteBuffer.allocateDirect(expectedEntries * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until expectedEntries) {
            val r = rawRgb[i * 3]
            val g = rawRgb[i * 3 + 1]
            val b = rawRgb[i * 3 + 2]

            byteBuffer.put((clamp01(r) * 255f + 0.5f).toInt().toByte())
            byteBuffer.put((clamp01(g) * 255f + 0.5f).toInt().toByte())
            byteBuffer.put((clamp01(b) * 255f + 0.5f).toInt().toByte())
            byteBuffer.put(255.toByte())
        }
        byteBuffer.rewind()

        Log.i(TAG, "Parsed .cube LUT: size=$size, domainMin=[${domainMin.joinToString()}], domainMax=[${domainMax.joinToString()}]")
        return ParsedCube(size, byteBuffer, domainMin, domainMax)
    }
}

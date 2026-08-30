package com.auroracam.app.camera.burst

import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

object BurstAligner {
    private const val TAG = "BurstAligner"

    // Working resolution for the alignment pyramid. Raised from 480x360 to
    // 960x720 to shrink the scale-up multiplier applied when mapping the
    // measured offset back to full capture resolution — at 480w that
    // multiplier was ~6-7x for a ~3200px-wide capture, amplifying small
    // sub-pixel alignment residuals into multi-pixel ghosting after merge.
    // Doubling working resolution roughly halves that multiplier.
    const val WORKING_W = 960
    const val WORKING_H = 720

    // Pyramid level dimensions, derived so they stay correct if
    // WORKING_W/WORKING_H change again.
    const val LEVEL1_W = WORKING_W / 2   // 480
    const val LEVEL1_H = WORKING_H / 2   // 360
    const val LEVEL0_W = WORKING_W / 4   // 240
    const val LEVEL0_H = WORKING_H / 4   // 180

    const val TILE_SIZE = 16
    const val TILES_X = WORKING_W / TILE_SIZE // 60
    const val TILES_Y = WORKING_H / TILE_SIZE // 45
    const val TOTAL_TILES = TILES_X * TILES_Y // 2700

    data class AlignedFrame(
        val rawFrame: RawYuvFrame,
        val dxFull: Float,
        val dyFull: Float,
        val acceptedFraction: Float,
        val maskBuffer: ByteBuffer // R8 mask texture buffer (TILES_X x TILES_Y)
    )

    data class AlignmentResult(
        val refIndex: Int,
        val alignedFrames: List<AlignedFrame>,
        val droppedCount: Int,
        val elapsedMs: Long
    )

    /**
     * Executes full CPU multi-resolution alignment and robustness tile mask generation.
     */
    fun alignBurst(frames: List<RawYuvFrame>): AlignmentResult {
        val startMs = SystemClock.elapsedRealtime()
        val n = frames.size
        require(n >= 1) { "Cannot align empty burst" }

        if (n == 1) {
            val mask = createFullAcceptMask()
            val aligned = AlignedFrame(frames[0], 0f, 0f, 1.0f, mask)
            return AlignmentResult(0, listOf(aligned), 0, SystemClock.elapsedRealtime() - startMs)
        }

        val origW = frames[0].width
        val origH = frames[0].height
        val scaleX = origW.toFloat() / WORKING_W.toFloat()
        val scaleY = origH.toFloat() / WORKING_H.toFloat()

        // 3.1: Downscale each Y plane to working luma resolution
        val downscaled = Array(n) { i ->
            downscaleYToWorkingResolution(frames[i].yBuffer, origW, origH)
        }

        // 3.2: Reference selection via highest gradient energy (Sum |gradX| + |gradY|)
        var bestRefIdx = 0
        var maxEnergy = -1L
        for (i in 0 until n) {
            val energy = computeGradientEnergy(downscaled[i], WORKING_W, WORKING_H)
            if (energy > maxEnergy) {
                maxEnergy = energy
                bestRefIdx = i
            }
        }
        Log.i(TAG, "Selected reference frame: index=$bestRefIdx (gradientEnergy=$maxEnergy)")

        val refLuma480 = downscaled[bestRefIdx]
        val refLuma240 = downscaleHalf(refLuma480, WORKING_W, WORKING_H)
        val refLuma120 = downscaleHalf(refLuma240, LEVEL1_W, LEVEL1_H)

        val alignedList = mutableListOf<AlignedFrame>()
        val offsetsLogged = mutableListOf<String>()
        val accLogged = mutableListOf<String>()
        var droppedCount = 0

        for (i in 0 until n) {
            if (i == bestRefIdx) {
                val refMask = createFullAcceptMask()
                alignedList.add(AlignedFrame(frames[i], 0f, 0f, 1.0f, refMask))
                continue
            }

            val targetLuma480 = downscaled[i]
            val targetLuma240 = downscaleHalf(targetLuma480, WORKING_W, WORKING_H)
            val targetLuma120 = downscaleHalf(targetLuma240, LEVEL1_W, LEVEL1_H)

            // 3.3: 3-level pyramidal translation search
            // Level 0 (Coarsest): Search +/- 16px
            val (dx120, dy120) = searchBestIntegerSAD(
                ref = refLuma120,
                target = targetLuma120,
                w = LEVEL0_W,
                h = LEVEL0_H,
                centerDx = 0,
                centerDy = 0,
                radius = 16
            )

            // Level 1 (Mid): Refine +/- 2px around 2 * dx120
            val (dx240, dy240) = searchBestIntegerSAD(
                ref = refLuma240,
                target = targetLuma240,
                w = LEVEL1_W,
                h = LEVEL1_H,
                centerDx = dx120 * 2,
                centerDy = dy120 * 2,
                radius = 2
            )

            // Level 2 (Fine, full working res): Refine +/- 2px around 2 * dx240 with subpixel fit
            val (subDx480, subDy480) = searchSubpixelSAD(
                ref = refLuma480,
                target = targetLuma480,
                w = WORKING_W,
                h = WORKING_H,
                centerDx = dx240 * 2,
                centerDy = dy240 * 2,
                radius = 2
            )

            // Scale to full resolution
            val dxFull = subDx480 * scaleX
            val dyFull = subDy480 * scaleY

            // 3.4: Compute 16px tile robustness masks
            val (maskBuffer, acceptFrac) = computeTileRobustnessMask(
                ref = refLuma480,
                target = targetLuma480,
                dx = subDx480.roundToInt(),
                dy = subDy480.roundToInt()
            )

            offsetsLogged.add("(%.2f,%.2f)".format(dxFull, dyFull))
            accLogged.add("${(acceptFrac * 100).toInt()}%")

            // 3.5: Drop frame if accepted tile fraction < 25%
            if (acceptFrac < 0.25f) {
                Log.w(TAG, "Dropping frame $i: accepted tile fraction ${(acceptFrac * 100).toInt()}% is below 25% threshold (motion/occlusion)")
                droppedCount++
            } else {
                alignedList.add(AlignedFrame(frames[i], dxFull, dyFull, acceptFrac, maskBuffer))
            }
        }

        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        Log.i(TAG, "CAPTURE burst-align: off=[${offsetsLogged.joinToString()}] acc=[${accLogged.joinToString()}] ms=$elapsedMs")

        return AlignmentResult(
            refIndex = bestRefIdx,
            alignedFrames = alignedList,
            droppedCount = droppedCount,
            elapsedMs = elapsedMs
        )
    }

    private fun downscaleYToWorkingResolution(yBuf: ByteBuffer, origW: Int, origH: Int): ByteArray {
        val out = ByteArray(WORKING_W * WORKING_H)
        val stepX = origW.toFloat() / WORKING_W.toFloat()
        val stepY = origH.toFloat() / WORKING_H.toFloat()

        val srcXTable = IntArray(WORKING_W) { (it * stepX).toInt().coerceIn(0, origW - 1) }

        for (y in 0 until WORKING_H) {
            val srcY = (y * stepY).toInt().coerceIn(0, origH - 1)
            val srcRowOffset = srcY * origW
            val outRowOffset = y * WORKING_W
            for (x in 0 until WORKING_W) {
                out[outRowOffset + x] = yBuf.get(srcRowOffset + srcXTable[x])
            }
        }
        return out
    }

    private fun downscaleHalf(src: ByteArray, w: Int, h: Int): ByteArray {
        val outW = w / 2
        val outH = h / 2
        val out = ByteArray(outW * outH)
        for (y in 0 until outH) {
            val srcY = y * 2
            val srcRow0 = srcY * w
            val srcRow1 = (srcY + 1) * w
            val outRow = y * outW
            for (x in 0 until outW) {
                val srcX = x * 2
                val p00 = src[srcRow0 + srcX].toInt() and 0xFF
                val p01 = src[srcRow0 + srcX + 1].toInt() and 0xFF
                val p10 = src[srcRow1 + srcX].toInt() and 0xFF
                val p11 = src[srcRow1 + srcX + 1].toInt() and 0xFF
                out[outRow + x] = ((p00 + p01 + p10 + p11) shr 2).toByte()
            }
        }
        return out
    }

    private fun computeGradientEnergy(luma: ByteArray, w: Int, h: Int): Long {
        var energy = 0L
        for (y in 0 until h - 1 step 2) {
            val row = y * w
            val nextRow = (y + 1) * w
            for (x in 0 until w - 1 step 2) {
                val p = luma[row + x].toInt() and 0xFF
                val px = luma[row + x + 1].toInt() and 0xFF
                val py = luma[nextRow + x].toInt() and 0xFF
                energy += (abs(px - p) + abs(py - p))
            }
        }
        return energy
    }

    private fun searchBestIntegerSAD(
        ref: ByteArray,
        target: ByteArray,
        w: Int,
        h: Int,
        centerDx: Int,
        centerDy: Int,
        radius: Int
    ): Pair<Int, Int> {
        var bestDx = centerDx
        var bestDy = centerDy
        var minSad = Long.MAX_VALUE

        val margin = 8
        for (dy in (centerDy - radius)..(centerDy + radius)) {
            for (dx in (centerDx - radius)..(centerDx + radius)) {
                var sad = 0L
                val startY = maxOf(margin, -dy)
                val endY = minOf(h - margin, h - dy)
                val startX = maxOf(margin, -dx)
                val endX = minOf(w - margin, w - dx)

                for (y in startY until endY step 2) {
                    val refRow = y * w
                    val targetRow = (y + dy) * w
                    for (x in startX until endX step 2) {
                        val rVal = ref[refRow + x].toInt() and 0xFF
                        val tVal = target[targetRow + x + dx].toInt() and 0xFF
                        sad += abs(rVal - tVal)
                    }
                }
                if (sad < minSad) {
                    minSad = sad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }
        return Pair(bestDx, bestDy)
    }

    private fun searchSubpixelSAD(
        ref: ByteArray,
        target: ByteArray,
        w: Int,
        h: Int,
        centerDx: Int,
        centerDy: Int,
        radius: Int
    ): Pair<Float, Float> {
        var bestDx = centerDx
        var bestDy = centerDy
        var minSad = Long.MAX_VALUE

        val margin = 16
        val sadGrid = Array(2 * radius + 1) { LongArray(2 * radius + 1) }

        for (rY in -radius..radius) {
            val dy = centerDy + rY
            val startY = maxOf(margin, -dy)
            val endY = minOf(h - margin, h - dy)

            for (rX in -radius..radius) {
                val dx = centerDx + rX
                val startX = maxOf(margin, -dx)
                val endX = minOf(w - margin, w - dx)

                var sad = 0L
                for (y in startY until endY step 2) {
                    val refRow = y * w
                    val targetRow = (y + dy) * w
                    for (x in startX until endX step 2) {
                        val rVal = ref[refRow + x].toInt() and 0xFF
                        val tVal = target[targetRow + x + dx].toInt() and 0xFF
                        sad += abs(rVal - tVal)
                    }
                }
                sadGrid[rY + radius][rX + radius] = sad
                if (sad < minSad) {
                    minSad = sad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        // Subpixel fitting using 1D parabola around best integer coordinates
        val rXBest = (bestDx - centerDx + radius).coerceIn(1, 2 * radius - 1)
        val rYBest = (bestDy - centerDy + radius).coerceIn(1, 2 * radius - 1)

        val sXm1 = sadGrid[rYBest][rXBest - 1].toDouble()
        val sX0 = sadGrid[rYBest][rXBest].toDouble()
        val sXp1 = sadGrid[rYBest][rXBest + 1].toDouble()
        val subShiftX = if (sXm1 + sXp1 - 2 * sX0 > 1e-4) {
            ((sXm1 - sXp1) / (2 * (sXm1 + sXp1 - 2 * sX0))).toFloat().coerceIn(-0.5f, 0.5f)
        } else 0.0f

        val sYm1 = sadGrid[rYBest - 1][rXBest].toDouble()
        val sY0 = sadGrid[rYBest][rXBest].toDouble()
        val sYp1 = sadGrid[rYBest + 1][rXBest].toDouble()
        val subShiftY = if (sYm1 + sYp1 - 2 * sY0 > 1e-4) {
            ((sYm1 - sYp1) / (2 * (sYm1 + sYp1 - 2 * sY0))).toFloat().coerceIn(-0.5f, 0.5f)
        } else 0.0f

        return Pair(bestDx.toFloat() + subShiftX, bestDy.toFloat() + subShiftY)
    }

    private fun computeTileRobustnessMask(
        ref: ByteArray,
        target: ByteArray,
        dx: Int,
        dy: Int
    ): Pair<ByteBuffer, Float> {
        val tileSads = FloatArray(TOTAL_TILES)
        var tileIdx = 0

        for (ty in 0 until TILES_Y) {
            val startY = ty * TILE_SIZE
            for (tx in 0 until TILES_X) {
                val startX = tx * TILE_SIZE
                var tileSad = 0L
                var validPx = 0

                for (y in startY until (startY + TILE_SIZE)) {
                    val targetY = y + dy
                    if (targetY < 0 || targetY >= WORKING_H) continue
                    val refRow = y * WORKING_W
                    val targetRow = targetY * WORKING_W
                    for (x in startX until (startX + TILE_SIZE)) {
                        val targetX = x + dx
                        if (targetX < 0 || targetX >= WORKING_W) continue
                        val rVal = ref[refRow + x].toInt() and 0xFF
                        val tVal = target[targetRow + targetX].toInt() and 0xFF
                        tileSad += abs(rVal - tVal)
                        validPx++
                    }
                }
                tileSads[tileIdx++] = if (validPx > 0) tileSad.toFloat() / validPx else 999.0f
            }
        }

        // Calculate median tile SAD
        val sortedSads = tileSads.clone().apply { sort() }
        val medianSad = sortedSads[TOTAL_TILES / 2]
        val threshold = (medianSad * 1.5f + 4.0f)

        val maskBuffer = ByteBuffer.allocateDirect(TILES_X * TILES_Y).order(ByteOrder.nativeOrder())
        var acceptedCount = 0

        for (i in 0 until TOTAL_TILES) {
            val isAccepted = tileSads[i] <= threshold
            if (isAccepted) acceptedCount++
            maskBuffer.put(if (isAccepted) 0xFF.toByte() else 0x00.toByte())
        }
        maskBuffer.rewind()

        val acceptFrac = acceptedCount.toFloat() / TOTAL_TILES.toFloat()
        return Pair(maskBuffer, acceptFrac)
    }

    fun createFullAcceptMask(): ByteBuffer {
        val maskBuffer = ByteBuffer.allocateDirect(TILES_X * TILES_Y).order(ByteOrder.nativeOrder())
        for (i in 0 until TOTAL_TILES) {
            maskBuffer.put(0xFF.toByte())
        }
        maskBuffer.rewind()
        return maskBuffer
    }
}

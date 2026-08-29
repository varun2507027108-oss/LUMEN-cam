package com.auroracam.app.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.auroracam.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CaptureSaver {
    private const val TAG = "CaptureSaver"
    private const val ALBUM_NAME = "AuroraCam"

    data class Telemetry(
        val iso: Int? = null,
        val expTimeFormatted: String = "N/A"
    )

    /**
     * Builds the standardized provenance tag:
     * AURORA_<PATH>_<ENC>_<LOOK>_<I###>
     *
     * PATH: STD | DX
     * ENC:  LEG | YUV | STK
     * LOOK: OFF | WRM | CHR | MON | CUB
     * I###: 3-digit intensity percentage (e.g. I027, I100), I000 when OFF
     */
    fun buildProvenanceTag(
        path: String,
        isLegacy: Boolean,
        lookName: String,
        isLookEnabled: Boolean,
        intensity: Float,
        encOverride: String? = null
    ): String {
        val pathTag = path.uppercase(Locale.US)
        val encTag = encOverride ?: if (isLegacy) "LEG" else "YUV"
        val lookTag: String
        val intensityTag: String

        if (!isLookEnabled || intensity <= 0.0f) {
            lookTag = "OFF"
            intensityTag = "I000"
        } else {
            lookTag = when {
                lookName.contains("Amber", ignoreCase = true) -> "AMB"
                lookName.contains("Steel", ignoreCase = true) -> "MST"
                lookName.contains("Teal", ignoreCase = true) -> "CYN"
                lookName.contains("Bleach", ignoreCase = true) -> "BLU"
                lookName.contains("Gold", ignoreCase = true) -> "GLD"
                lookName.contains("Warm", ignoreCase = true) -> "WRM"
                lookName.contains("Chrome", ignoreCase = true) -> "CHR"
                lookName.contains("Mono", ignoreCase = true) -> "MON"
                lookName.contains("Vint", ignoreCase = true) -> "VNT"
                else -> "CUB"
            }
            val intPct = (intensity * 100f).toInt().coerceIn(0, 100)
            intensityTag = String.format(Locale.US, "I%03d", intPct)
        }

        return "AURORA_${pathTag}_${encTag}_${lookTag}_${intensityTag}"
    }

    /**
     * Generates a unique timestamped base name with full provenance:
     * AURORA_<PATH>_<ENC>_<LOOK>_<I###>_<timestamp>
     */
    fun generateProvenanceBaseName(
        path: String,
        isLegacy: Boolean,
        lookName: String,
        isLookEnabled: Boolean,
        intensity: Float,
        encOverride: String? = null
    ): String {
        val tag = buildProvenanceTag(path, isLegacy, lookName, isLookEnabled, intensity, encOverride)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "${tag}_${timeStamp}"
    }

    fun generateCaptureFileName(
        path: String,
        isLegacy: Boolean,
        lookName: String,
        isLookEnabled: Boolean,
        intensity: Float,
        suffix: String = "",
        encOverride: String? = null
    ): String {
        val base = generateProvenanceBaseName(path, isLegacy, lookName, isLookEnabled, intensity, encOverride)
        return "${base}${suffix}.jpg"
    }

    fun logSizeGuard(width: Int, height: Int, expectedWidth: Int = 3200, expectedHeight: Int = 2400) {
        val isOk = (width == expectedWidth && height == expectedHeight) || (width == expectedHeight && height == expectedWidth)
        Log.i(TAG, "CAPTURE size-guard: readback=${width}x${height} expected=${expectedWidth}x${expectedHeight} ${if (isOk) "OK" else "FAIL"}")
    }

    fun generateBaseFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "AURORA_${timeStamp}"
    }

    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        quality: Int = 97,
        telemetry: Telemetry? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
            put(MediaStore.Images.Media.ORIENTATION, 0)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for $fileName")
            return@withContext null
        }

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.flush()
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            val isoStr = telemetry?.iso?.toString() ?: "N/A"
            val expStr = telemetry?.expTimeFormatted ?: "N/A"
            val sizeStr = "${bitmap.width}x${bitmap.height}"
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "CAPTURE $fileName  ISO=$isoStr ExpTime=$expStr size=$sizeStr")
                Log.i(TAG, "Saved image: $uri (path: Pictures/$ALBUM_NAME/$fileName)")
            }
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error writing image $fileName to MediaStore", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    suspend fun saveJpegBytes(
        context: Context,
        jpegBytes: ByteArray,
        fileName: String,
        rotationDegrees: Int = 0,
        width: Int = 0,
        height: Int = 0,
        telemetry: Telemetry? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
            put(MediaStore.Images.Media.ORIENTATION, rotationDegrees)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for $fileName")
            return@withContext null
        }

        try {
            resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                outputStream.write(jpegBytes)
                outputStream.flush()
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            val isoStr = telemetry?.iso?.toString() ?: "N/A"
            val expStr = telemetry?.expTimeFormatted ?: "N/A"
            val sizeStr = if (width > 0 && height > 0) "${width}x${height}" else "N/A"
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "CAPTURE $fileName  ISO=$isoStr ExpTime=$expStr size=$sizeStr bytes=${jpegBytes.size}")
                Log.i(TAG, "Saved image: $uri (path: Pictures/$ALBUM_NAME/$fileName, size: ${jpegBytes.size} bytes / ${"%.1f".format(jpegBytes.size / 1024.0)} KB)")
            }
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error writing image $fileName to MediaStore", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    suspend fun saveDng(
        context: Context,
        rawImage: Image,
        captureResult: TotalCaptureResult,
        characteristics: CameraCharacteristics,
        fileName: String,
        telemetry: Telemetry? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for DNG $fileName")
            return@withContext null
        }

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                DngCreator(characteristics, captureResult).use { dngCreator ->
                    dngCreator.writeImage(outputStream, rawImage)
                }
                outputStream.flush()
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            val isoStr = telemetry?.iso?.toString() ?: "N/A"
            val expStr = telemetry?.expTimeFormatted ?: "N/A"
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "DNG CAPTURE $fileName  ISO=$isoStr ExpTime=$expStr")
                Log.i(TAG, "Saved DNG: $uri (path: Pictures/$ALBUM_NAME/$fileName)")
            }
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error writing DNG $fileName to MediaStore", e)
            resolver.delete(uri, null, null)
            null
        } finally {
            try {
                rawImage.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing raw Image", e)
            }
        }
    }

    suspend fun loadThumbnail(
        context: Context,
        uri: Uri,
        targetSize: Size = Size(128, 128)
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, targetSize, null)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load thumbnail for $uri", e)
            null
        }
    }

    suspend fun loadFullBitmap(
        context: Context,
        uri: Uri
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load full bitmap for $uri", e)
            null
        }
    }

    suspend fun getLatestCaptureUri(
        context: Context
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$ALBUM_NAME%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idCol)
                    return@withContext Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query latest capture", e)
            null
        }
    }

    suspend fun deleteCapture(
        context: Context,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete capture: $uri", e)
            false
        }
    }
}
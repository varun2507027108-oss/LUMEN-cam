package com.auroracam.app.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageProxy
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

    fun generateBaseFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "AURORA_${timeStamp}"
    }

    suspend fun saveImageProxy(
        context: Context,
        imageProxy: ImageProxy
    ): Uri? = withContext(Dispatchers.IO) {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        imageProxy.close()
        val fileName = "${generateBaseFileName()}.jpg"
        saveJpegBytes(context, bytes, fileName, rotationDegrees)
    }

    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        quality: Int = 95
    ): Uri? = withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        saveJpegBytes(context, bytes, fileName, 0)
    }

    suspend fun saveJpegBytes(
        context: Context,
        jpegBytes: ByteArray,
        fileName: String,
        rotationDegrees: Int = 0
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

            Log.i(TAG, "Saved image: $uri (path: Pictures/$ALBUM_NAME/$fileName)")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error writing image $fileName to MediaStore", e)
            resolver.delete(uri, null, null)
            null
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
}

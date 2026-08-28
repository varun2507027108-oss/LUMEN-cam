package com.auroracam.app.capture

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class DxMetadata(
    val firstFileName: String,
    val secondFileName: String,
    val compositeFileName: String,
    val blendMode: Int,
    val opacity: Float,
    val flipFirst: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("firstFileName", firstFileName)
            put("secondFileName", secondFileName)
            put("compositeFileName", compositeFileName)
            put("mode", blendMode)
            put("opacity", opacity.toDouble())
            put("flipped", flipFirst)
            put("timestamp", timestamp)
        }.toString(2)
    }

    companion object {
        private const val TAG = "DxMetadata"
        private const val DIR_NAME = "dx_metadata"

        suspend fun save(context: Context, metadata: DxMetadata) = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, DIR_NAME)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, "META_${metadata.timestamp}.json")
                file.writeText(metadata.toJson())
                Log.i(TAG, "Saved DX metadata: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save DX metadata", e)
            }
        }
    }
}

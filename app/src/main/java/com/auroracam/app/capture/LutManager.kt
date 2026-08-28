package com.auroracam.app.capture

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.auroracam.app.gl.lut.AuroraWarmLut
import com.auroracam.app.gl.lut.ChromeLut
import com.auroracam.app.gl.lut.CubeParser
import com.auroracam.app.gl.lut.MonoLut
import com.auroracam.app.gl.lut.ParsedCube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class LutManager(private val context: Context) {
    companion object {
        private const val TAG = "LutManager"
        private const val PREFS_NAME = "aurora_lut_prefs"
        private const val KEY_LAST_LUT_NAME = "last_selected_lut_name"
        private const val KEY_LAST_LUT_FILE = "last_selected_lut_file"
        
        val BUILTIN_PRESETS = listOf(AuroraWarmLut.LUT_NAME, ChromeLut.LUT_NAME, MonoLut.LUT_NAME)
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lookDir: File = File(context.filesDir, "Look").apply { if (!exists()) mkdirs() }

    var activeLutName: String = AuroraWarmLut.LUT_NAME
        private set

    /**
     * Loads the initial LUT on app startup (persisted selection or default Warm).
     */
    suspend fun loadInitialLut(): Pair<String, ParsedCube> = withContext(Dispatchers.IO) {
        val lastFileName = prefs.getString(KEY_LAST_LUT_FILE, null)
        val lastName = prefs.getString(KEY_LAST_LUT_NAME, AuroraWarmLut.LUT_NAME)

        if (!lastFileName.isNullOrEmpty()) {
            val file = File(lookDir, lastFileName)
            if (file.exists()) {
                try {
                    val parsed = file.inputStream().use { CubeParser.parse(it) }
                    activeLutName = lastName ?: file.nameWithoutExtension
                    Log.i(TAG, "Restored last active LUT: $activeLutName from ${file.name}")
                    return@withContext Pair(activeLutName, parsed)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore custom LUT, trying preset", e)
                }
            }
        }

        return@withContext when (lastName) {
            ChromeLut.LUT_NAME -> selectPreset(ChromeLut.LUT_NAME)
            MonoLut.LUT_NAME -> selectPreset(MonoLut.LUT_NAME)
            else -> selectPreset(AuroraWarmLut.LUT_NAME)
        }
    }

    /**
     * Selects one of the built-in procedural preset LUTs (Warm, Chrome, Mono).
     */
    fun selectPreset(presetName: String): Pair<String, ParsedCube> {
        activeLutName = presetName
        prefs.edit()
            .remove(KEY_LAST_LUT_FILE)
            .putString(KEY_LAST_LUT_NAME, activeLutName)
            .apply()

        val cube = when (presetName) {
            ChromeLut.LUT_NAME -> ChromeLut.generate()
            MonoLut.LUT_NAME -> MonoLut.generate()
            else -> AuroraWarmLut.generate()
        }
        Log.i(TAG, "Activated preset LUT: $presetName")
        return Pair(activeLutName, cube)
    }

    /**
     * Imports a .cube file via SAF Uri, caches it into filesDir/Look/, and parses it off-thread.
     */
    suspend fun importAndSelectCubeUri(uri: Uri): Pair<String, ParsedCube> = withContext(Dispatchers.IO) {
        val displayName = getFileNameFromUri(uri) ?: "custom_${System.currentTimeMillis()}.cube"
        val targetFile = File(lookDir, displayName)

        context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        val parsed = targetFile.inputStream().use { CubeParser.parse(it) }
        val lutName = displayName.removeSuffix(".cube")
        activeLutName = lutName

        prefs.edit()
            .putString(KEY_LAST_LUT_FILE, targetFile.name)
            .putString(KEY_LAST_LUT_NAME, activeLutName)
            .apply()

        Log.i(TAG, "Imported and activated LUT: $activeLutName (${targetFile.length()} bytes)")
        Pair(activeLutName, parsed)
    }

    /**
     * Selects a locally cached LUT file.
     */
    suspend fun selectCachedLut(file: File): Pair<String, ParsedCube> = withContext(Dispatchers.IO) {
        val parsed = file.inputStream().use { CubeParser.parse(it) }
        val lutName = file.nameWithoutExtension
        activeLutName = lutName

        prefs.edit()
            .putString(KEY_LAST_LUT_FILE, file.name)
            .putString(KEY_LAST_LUT_NAME, activeLutName)
            .apply()

        Log.i(TAG, "Selected cached LUT: $activeLutName")
        Pair(activeLutName, parsed)
    }

    fun resetToDefault(): Pair<String, ParsedCube> {
        return selectPreset(AuroraWarmLut.LUT_NAME)
    }

    fun listCachedLuts(): List<File> {
        return lookDir.listFiles { file -> file.extension.equals("cube", ignoreCase = true) }?.toList() ?: emptyList()
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
        return name
    }
}

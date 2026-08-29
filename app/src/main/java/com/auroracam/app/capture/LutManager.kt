package com.auroracam.app.capture

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.auroracam.app.gl.lut.AuroraWarmLut
import com.auroracam.app.gl.lut.ChromeLut
import com.auroracam.app.gl.lut.CubeParser
import com.auroracam.app.gl.lut.FujiClassicChromeLut
import com.auroracam.app.gl.lut.HasselbladNaturalLut
import com.auroracam.app.gl.lut.KodakPortra400Lut
import com.auroracam.app.gl.lut.LeicaCharacterLut
import com.auroracam.app.gl.lut.MonoLut
import com.auroracam.app.gl.lut.ParsedCube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class LookUniforms(
    val intensity: Float = 1.0f,
    val halation: Float = 0.20f,
    val grain: Float = 0.04f,
    val vignette: Float = 0.12f,
    val chromaticAberration: Float = 0.0f,
    val halationThreshold: Float = 0.75f
)

data class LookActivationResult(
    val name: String,
    val cube: ParsedCube,
    val uniforms: LookUniforms
)

class LutManager(private val context: Context) {
    companion object {
        private const val TAG = "LutManager"
        private const val PREFS_NAME = "aurora_lut_prefs"
        private const val KEY_LAST_LUT_NAME = "last_selected_lut_name"
        private const val KEY_LAST_LUT_FILE = "last_selected_lut_file"
        
        val BUILTIN_PRESETS = listOf(
            AuroraWarmLut.LUT_NAME,
            HasselbladNaturalLut.LUT_NAME,
            LeicaCharacterLut.LUT_NAME,
            FujiClassicChromeLut.LUT_NAME,
            KodakPortra400Lut.LUT_NAME,
            ChromeLut.LUT_NAME,
            MonoLut.LUT_NAME
        )

        val DEFAULT_LOOK_UNIFORMS: Map<String, LookUniforms> = mapOf(
            HasselbladNaturalLut.LUT_NAME to LookUniforms(intensity = 1.0f, halation = 0.00f, grain = 0.010f, vignette = 0.08f, chromaticAberration = 0.00f, halationThreshold = 0.75f),
            LeicaCharacterLut.LUT_NAME to LookUniforms(intensity = 1.0f, halation = 0.28f, grain = 0.030f, vignette = 0.18f, chromaticAberration = 0.04f, halationThreshold = 0.58f),
            FujiClassicChromeLut.LUT_NAME to LookUniforms(intensity = 1.0f, halation = 0.12f, grain = 0.035f, vignette = 0.14f, chromaticAberration = 0.00f, halationThreshold = 0.72f),
            KodakPortra400Lut.LUT_NAME to LookUniforms(intensity = 1.0f, halation = 0.22f, grain = 0.040f, vignette = 0.12f, chromaticAberration = 0.02f, halationThreshold = 0.70f),
            AuroraWarmLut.LUT_NAME to LookUniforms(intensity = 1.0f, halation = 0.25f, grain = 0.030f, vignette = 0.12f, chromaticAberration = 0.00f, halationThreshold = 0.70f),
            ChromeLut.LUT_NAME to LookUniforms(intensity = 1.0f, halation = 0.10f, grain = 0.025f, vignette = 0.22f, chromaticAberration = 0.03f, halationThreshold = 0.75f),
            MonoLut.LUT_NAME to LookUniforms(intensity = 1.0f, halation = 0.16f, grain = 0.050f, vignette = 0.24f, chromaticAberration = 0.00f, halationThreshold = 0.75f)
        )

        val DEFAULT_CUSTOM_UNIFORMS = LookUniforms(intensity = 1.0f, halation = 0.15f, grain = 0.030f, vignette = 0.10f, chromaticAberration = 0.00f, halationThreshold = 0.75f)
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lookDir: File = File(context.filesDir, "Look").apply { if (!exists()) mkdirs() }

    var activeLutName: String = AuroraWarmLut.LUT_NAME
        private set

    fun getUniformsForPreset(presetName: String): LookUniforms {
        val default = DEFAULT_LOOK_UNIFORMS[presetName] ?: DEFAULT_CUSTOM_UNIFORMS
        val hasOverride = prefs.getBoolean("override_active_$presetName", false)
        if (!hasOverride) return default

        return LookUniforms(
            intensity = prefs.getFloat("override_${presetName}_intensity", default.intensity),
            halation = prefs.getFloat("override_${presetName}_halation", default.halation),
            grain = prefs.getFloat("override_${presetName}_grain", default.grain),
            vignette = prefs.getFloat("override_${presetName}_vignette", default.vignette),
            chromaticAberration = prefs.getFloat("override_${presetName}_ca", default.chromaticAberration),
            halationThreshold = prefs.getFloat("override_${presetName}_halation_threshold", default.halationThreshold)
        )
    }

    fun saveUserOverride(presetName: String, uniforms: LookUniforms) {
        prefs.edit()
            .putBoolean("override_active_$presetName", true)
            .putFloat("override_${presetName}_intensity", uniforms.intensity)
            .putFloat("override_${presetName}_halation", uniforms.halation)
            .putFloat("override_${presetName}_grain", uniforms.grain)
            .putFloat("override_${presetName}_vignette", uniforms.vignette)
            .putFloat("override_${presetName}_ca", uniforms.chromaticAberration)
            .putFloat("override_${presetName}_halation_threshold", uniforms.halationThreshold)
            .apply()
        Log.d(TAG, "Saved user override for look '$presetName': $uniforms")
    }

    fun resetUserOverrides(presetName: String): LookUniforms {
        val default = DEFAULT_LOOK_UNIFORMS[presetName] ?: DEFAULT_CUSTOM_UNIFORMS
        prefs.edit()
            .remove("override_active_$presetName")
            .remove("override_${presetName}_intensity")
            .remove("override_${presetName}_halation")
            .remove("override_${presetName}_grain")
            .remove("override_${presetName}_vignette")
            .remove("override_${presetName}_ca")
            .remove("override_${presetName}_halation_threshold")
            .apply()
        Log.i(TAG, "Reset look '$presetName' back to factory default uniforms: $default")
        return default
    }

    /**
     * Loads the initial LUT on app startup (persisted selection or default Warm).
     */
    suspend fun loadInitialLut(): LookActivationResult = withContext(Dispatchers.IO) {
        val lastFileName = prefs.getString(KEY_LAST_LUT_FILE, null)
        val lastName = prefs.getString(KEY_LAST_LUT_NAME, AuroraWarmLut.LUT_NAME)

        if (!lastFileName.isNullOrEmpty()) {
            val file = File(lookDir, lastFileName)
            if (file.exists()) {
                try {
                    val parsed = file.inputStream().use { CubeParser.parse(it) }
                    activeLutName = lastName ?: file.nameWithoutExtension
                    val uniforms = getUniformsForPreset(activeLutName)
                    Log.i(TAG, "Restored last active LUT: $activeLutName from ${file.name}")
                    return@withContext LookActivationResult(activeLutName, parsed, uniforms)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore custom LUT, trying preset", e)
                }
            }
        }

        return@withContext when (lastName) {
            HasselbladNaturalLut.LUT_NAME -> selectPreset(HasselbladNaturalLut.LUT_NAME)
            LeicaCharacterLut.LUT_NAME -> selectPreset(LeicaCharacterLut.LUT_NAME)
            FujiClassicChromeLut.LUT_NAME -> selectPreset(FujiClassicChromeLut.LUT_NAME)
            KodakPortra400Lut.LUT_NAME -> selectPreset(KodakPortra400Lut.LUT_NAME)
            ChromeLut.LUT_NAME -> selectPreset(ChromeLut.LUT_NAME)
            MonoLut.LUT_NAME -> selectPreset(MonoLut.LUT_NAME)
            else -> selectPreset(AuroraWarmLut.LUT_NAME)
        }
    }

    /**
     * Selects one of the built-in procedural preset LUTs (Warm, Hasselblad, Leica, Classic Chrome, Portra 400, Chrome, Mono).
     */
    fun selectPreset(presetName: String): LookActivationResult {
        activeLutName = presetName
        prefs.edit()
            .remove(KEY_LAST_LUT_FILE)
            .putString(KEY_LAST_LUT_NAME, activeLutName)
            .apply()

        val cube = when (presetName) {
            HasselbladNaturalLut.LUT_NAME -> HasselbladNaturalLut.generate()
            LeicaCharacterLut.LUT_NAME -> LeicaCharacterLut.generate()
            FujiClassicChromeLut.LUT_NAME -> FujiClassicChromeLut.generate()
            KodakPortra400Lut.LUT_NAME -> KodakPortra400Lut.generate()
            ChromeLut.LUT_NAME -> ChromeLut.generate()
            MonoLut.LUT_NAME -> MonoLut.generate()
            else -> AuroraWarmLut.generate()
        }
        val uniforms = getUniformsForPreset(activeLutName)
        Log.i(TAG, "Activated preset LUT: $presetName with uniforms: $uniforms")
        return LookActivationResult(activeLutName, cube, uniforms)
    }

    /**
     * Imports a .cube file via SAF Uri, caches it into filesDir/Look/, and parses it off-thread.
     */
    suspend fun importAndSelectCubeUri(uri: Uri): LookActivationResult = withContext(Dispatchers.IO) {
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

        val uniforms = getUniformsForPreset(activeLutName)
        Log.i(TAG, "Imported and activated LUT: $activeLutName (${targetFile.length()} bytes)")
        LookActivationResult(activeLutName, parsed, uniforms)
    }

    /**
     * Selects a locally cached LUT file.
     */
    suspend fun selectCachedLut(file: File): LookActivationResult = withContext(Dispatchers.IO) {
        val parsed = file.inputStream().use { CubeParser.parse(it) }
        val lutName = file.nameWithoutExtension
        activeLutName = lutName

        prefs.edit()
            .putString(KEY_LAST_LUT_FILE, file.name)
            .putString(KEY_LAST_LUT_NAME, activeLutName)
            .apply()

        val uniforms = getUniformsForPreset(activeLutName)
        Log.i(TAG, "Selected cached LUT: $activeLutName")
        LookActivationResult(activeLutName, parsed, uniforms)
    }

    fun resetToDefault(): LookActivationResult {
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

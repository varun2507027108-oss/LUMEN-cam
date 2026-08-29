package com.auroracam.app.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import com.auroracam.app.camera.burst.BurstAligner
import com.auroracam.app.capture.CaptureSaver
import com.auroracam.app.gl.lut.AuroraWarmLut
import com.auroracam.app.gl.lut.ParsedCube
import com.auroracam.app.gl.passes.BurstMergePass
import com.auroracam.app.gl.passes.ChromaticAberrationPass
import com.auroracam.app.gl.passes.CompositeBlendPass
import com.auroracam.app.gl.passes.FilmCurvePass
import com.auroracam.app.gl.passes.LightTrailPass
import com.auroracam.app.gl.passes.LiveBlendPass
import com.auroracam.app.gl.passes.LocalToneMappingPass
import com.auroracam.app.gl.passes.MotionExposurePass
import com.auroracam.app.gl.passes.OesToFboPass
import com.auroracam.app.gl.passes.PassThroughPass
import com.auroracam.app.gl.passes.TemporalEchoPass
import com.auroracam.app.ui.CameraMode
import com.auroracam.app.ui.DxStage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class GpuTelemetry(
    val fps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val gpuEffectsTimeMs: Float = 0f,
    val previewWidth: Int = 0,
    val previewHeight: Int = 0,
    val isHalfFloat: Boolean = true,
    val historyBufferCount: Int = 3
)

class AuroraRenderer(
    private val glSurfaceView: GLSurfaceView,
    private val onSurfaceReady: (SurfaceTexture) -> Unit,
    private val onFpsUpdated: (Double) -> Unit,
    private val onDxStageChanged: (DxStage) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "AuroraRenderer"
        private const val STATS_LOG_INTERVAL_MS = 2000L
        private const val TARGET_CAMERA_ASPECT = 3.0f / 4.0f
        const val PREVIEW_FBO_W = 1200
        const val PREVIEW_FBO_H = 1600
    }

    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null

    // Passes
    private val passThroughPass = PassThroughPass()
    private val oesToFboPass = OesToFboPass()
    private val liveBlendPass = LiveBlendPass()
    private val compositeBlendPass = CompositeBlendPass()
    private val filmCurvePass = FilmCurvePass()
    private val localToneMappingPass = LocalToneMappingPass()
    private val temporalEchoPass = TemporalEchoPass()
    private val motionExposurePass = MotionExposurePass()
    private val lightTrailPass = LightTrailPass()
    private val chromaticAberrationPass = ChromaticAberrationPass()
    private var burstMergePass: BurstMergePass? = null
    private val lutTexture = LutTexture()

    // FBOs
    private var firstExposureFbo: Fbo? = null
    private var baseCameraFbo: Fbo? = null
    private val historyFbos = arrayOfNulls<Fbo>(3)
    private var historyIndex = 0
    private var lightAccumFbo: Fbo? = null
    private var lightAccumTempFbo: Fbo? = null
    private var gradeOutputFbo: Fbo? = null
    private var previewSceneFbo: Fbo? = null

    // Matrices
    private val transformMatrix = FloatArray(16)
    private val aspectMatrix = FloatArray(16)
    private var viewWidth = 0
    private var viewHeight = 0

    // Mode state
    @Volatile var currentMode: CameraMode = CameraMode.STANDARD
    @Volatile var dxStage: DxStage = DxStage.STAGE_1_EMPTY
    @Volatile var dxBlendMode: Int = 1 // Default Screen (1)
    @Volatile var dxOpacity: Float = 1.0f
    @Volatile var dxFlipFirst: Boolean = false

    // Signature Look state
    @Volatile var isLookEnabled: Boolean = true
    @Volatile var lookIntensity: Float = 1.0f
    @Volatile var lookGrain: Float = 0.04f
    @Volatile var lookVignette: Float = 0.12f
    @Volatile var lookHalation: Float = 0.20f
    @Volatile private var _isLookPrecision16f: Boolean = true
    val isLookPrecision16f: Boolean get() = _isLookPrecision16f

    // Temporal Echo parameters
    @Volatile var temporalEchoDecay: Float = 0.75f
    @Volatile var temporalEchoTrailLength: Int = 2
    @Volatile var temporalEchoBlendMode: Int = 0 // 0: Normal, 1: Screen

    // Motion-Only Exposure parameters
    @Volatile var motionThreshold: Float = 0.08f
    @Volatile var motionSoftness: Float = 0.06f
    @Volatile var motionNoiseFloor: Float = 0.02f
    @Volatile var motionBlend: Float = 0.85f
    @Volatile var motionStyle: Int = 0 // 0: Classic, 1: Luminous

    // Light Trail parameters
    @Volatile var lightTrailThreshold: Float = 0.55f
    @Volatile var lightTrailKnee: Float = 0.10f
    @Volatile var lightTrailDecay: Float = 0.94f
    @Volatile var lightTrailIntensity: Float = 1.25f
    @Volatile var lightTrailBlendMode: Int = 0 // 0: MAX, 1: ADD, 2: SCREEN

    // Chromatic Aberration parameters
    @Volatile var chromaticAberrationIntensity: Float = 0.0f

    // GPU Telemetry Callback for Developer Profiler HUD
    var onGpuTelemetryUpdated: ((GpuTelemetry) -> Unit)? = null

    @Volatile private var isFirstCapturePending = false
    @Volatile private var pendingLutCube: ParsedCube? = null

    // Stats
    private var frameCount = 0
    private var lastStatsLogTimeMs = 0L
    @Volatile private var isFrameAvailable = false

    init {
        Matrix.setIdentityM(transformMatrix, 0)
        Matrix.setIdentityM(aspectMatrix, 0)
    }

    fun setLookPrecision16f(enabled: Boolean) {
        _isLookPrecision16f = enabled
        if (viewWidth > 0 && viewHeight > 0) {
            try {
                glSurfaceView.queueEvent {
                    previewSceneFbo?.release()
                    previewSceneFbo = Fbo(viewWidth, viewHeight, useHalfFloat = _isLookPrecision16f)

                    lightAccumFbo?.release()
                    lightAccumFbo = Fbo(viewWidth, viewHeight, useHalfFloat = _isLookPrecision16f)

                    lightAccumTempFbo?.release()
                    lightAccumTempFbo = Fbo(viewWidth, viewHeight, useHalfFloat = _isLookPrecision16f)

                    Log.i(TAG, "Reallocated FBOs with 16F precision=$_isLookPrecision16f")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot queueEvent for setLookPrecision16f: ${e.message}")
            }
        }
    }

    fun clearLightTrails() {
        glSurfaceView.queueEvent {
            lightAccumFbo?.bind()
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            lightAccumFbo?.unbind(viewWidth, viewHeight)

            lightAccumTempFbo?.bind()
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            lightAccumTempFbo?.unbind(viewWidth, viewHeight)
            Log.i(TAG, "Light trails accumulation buffer cleared")
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: Initializing GLES 3.0 Renderer, Look Chain & Creative passes")
        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        val hasColorBufferFloat = extensions.contains("GL_EXT_color_buffer_float") || extensions.contains("GL_OES_texture_half_float")
        Log.i(TAG, "GLES3.0 Extensions: EXT_color_buffer_float present: $hasColorBufferFloat, extensions count: ${extensions.split(" ").size}")
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        passThroughPass.init()
        oesToFboPass.init()
        liveBlendPass.init()
        compositeBlendPass.init()
        filmCurvePass.init()
        localToneMappingPass.init()
        temporalEchoPass.init()
        motionExposurePass.init()
        lightTrailPass.init()
        chromaticAberrationPass.init()
        lutTexture.init()

        // Upload default procedural LUT (or pending imported LUT)
        val initialLut = pendingLutCube ?: AuroraWarmLut.generate()
        lutTexture.upload(initialLut)
        pendingLutCube = null

        firstExposureFbo?.release()
        firstExposureFbo = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H, useHalfFloat = false)

        baseCameraFbo?.release()
        baseCameraFbo = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H, useHalfFloat = false)

        for (i in 0 until 3) {
            historyFbos[i]?.release()
            historyFbos[i] = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H, useHalfFloat = false)
        }
        historyIndex = 0

        lightAccumFbo?.release()
        lightAccumFbo = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H, useHalfFloat = isLookPrecision16f)

        lightAccumTempFbo?.release()
        lightAccumTempFbo = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H, useHalfFloat = isLookPrecision16f)

        gradeOutputFbo?.release()
        gradeOutputFbo = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H, useHalfFloat = false)

        previewSceneFbo?.release()
        previewSceneFbo = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H, useHalfFloat = isLookPrecision16f)

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        surfaceTexture?.release()
        val st = SurfaceTexture(textureId)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st

        lastStatsLogTimeMs = SystemClock.elapsedRealtime()
        frameCount = 0
        onSurfaceReady(st)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES30.glViewport(0, 0, width, height)
        Log.i(TAG, "onSurfaceChanged: Display Surface size: ${width}x${height}")

        baseCameraFbo?.release()
        baseCameraFbo = Fbo(width, height, useHalfFloat = false)

        for (i in 0 until 3) {
            historyFbos[i]?.release()
            historyFbos[i] = Fbo(width, height, useHalfFloat = false)
        }
        historyIndex = 0

        lightAccumFbo?.release()
        lightAccumFbo = Fbo(width, height, useHalfFloat = isLookPrecision16f)

        lightAccumTempFbo?.release()
        lightAccumTempFbo = Fbo(width, height, useHalfFloat = isLookPrecision16f)

        gradeOutputFbo?.release()
        gradeOutputFbo = Fbo(width, height, useHalfFloat = false)

        previewSceneFbo?.release()
        previewSceneFbo = Fbo(width, height, useHalfFloat = isLookPrecision16f)

        updateAspectMatrix(width, height)
        surfaceTexture?.let { onSurfaceReady(it) }
    }

    private fun updateAspectMatrix(width: Int, height: Int) {
        Matrix.setIdentityM(aspectMatrix, 0)
        if (width <= 0 || height <= 0) return
        val screenAspect = width.toFloat() / height.toFloat()
        if (screenAspect < TARGET_CAMERA_ASPECT) {
            val scaleX = TARGET_CAMERA_ASPECT / screenAspect
            Matrix.scaleM(aspectMatrix, 0, scaleX, 1.0f, 1.0f)
        } else {
            val scaleY = screenAspect / TARGET_CAMERA_ASPECT
            Matrix.scaleM(aspectMatrix, 0, 1.0f, scaleY, 1.0f)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            val st = surfaceTexture ?: return

        synchronized(this) {
            if (isFrameAvailable) {
                try {
                    st.updateTexImage()
                    st.getTransformMatrix(transformMatrix)
                } catch (e: Exception) {
                    Log.w(TAG, "updateTexImage failed during surface transition: ${e.message}")
                }
                isFrameAvailable = false
            }
        }

        // Apply pending 3D LUT upload if requested off-thread
        pendingLutCube?.let { cube ->
            lutTexture.upload(cube)
            pendingLutCube = null
        }

        // Handle Stage 1 capture request for Double Exposure (GPU copy into FBO)
        if (isFirstCapturePending && firstExposureFbo != null) {
            firstExposureFbo?.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            oesToFboPass.render(textureId, transformMatrix)
            firstExposureFbo?.unbind(viewWidth, viewHeight)
            isFirstCapturePending = false
            dxStage = DxStage.STAGE_2_LOCKED
            onDxStageChanged(DxStage.STAGE_2_LOCKED)
        }

        // 1. Convert live OES camera frame into base 2D FBO with transform & aspect applied
        val baseFbo = baseCameraFbo ?: return
        baseFbo.bind()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        passThroughPass.render(textureId, transformMatrix, aspectMatrix)
        baseFbo.unbind(viewWidth, viewHeight)

        // 2. Creative pass based on active mode
        val sceneFbo = previewSceneFbo ?: return
        var sceneTexId = baseFbo.textureId

        when (currentMode) {
            CameraMode.STANDARD -> {
                sceneTexId = baseFbo.textureId
            }
            CameraMode.DOUBLE_EXPOSURE -> {
                if (dxStage == DxStage.STAGE_2_LOCKED && firstExposureFbo != null) {
                    sceneFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    liveBlendPass.render(
                        liveOesTextureId = textureId,
                        firstExposureTextureId = firstExposureFbo!!.textureId,
                        transformMatrix = transformMatrix,
                        aspectMatrix = aspectMatrix,
                        mode = dxBlendMode,
                        opacity = dxOpacity,
                        flipFirst = dxFlipFirst
                    )
                    sceneFbo.unbind(viewWidth, viewHeight)
                    sceneTexId = sceneFbo.textureId
                } else {
                    sceneTexId = baseFbo.textureId
                }
            }
            CameraMode.TEMPORAL_ECHO -> {
                val p1 = historyFbos[(historyIndex - 1 + 3) % 3]?.textureId ?: baseFbo.textureId
                val p2 = historyFbos[(historyIndex - 2 + 3) % 3]?.textureId ?: p1
                val p3 = historyFbos[(historyIndex - 3 + 3) % 3]?.textureId ?: p2

                sceneFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                temporalEchoPass.render(
                    currTexId = baseFbo.textureId,
                    prev1TexId = p1,
                    prev2TexId = p2,
                    prev3TexId = p3,
                    trailLength = temporalEchoTrailLength,
                    decay = temporalEchoDecay,
                    blendMode = temporalEchoBlendMode
                )
                sceneFbo.unbind(viewWidth, viewHeight)
                sceneTexId = sceneFbo.textureId
            }
            CameraMode.MOTION_EXPOSURE -> {
                val p1 = historyFbos[(historyIndex - 1 + 3) % 3]?.textureId ?: baseFbo.textureId

                sceneFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                motionExposurePass.render(
                    currTexId = baseFbo.textureId,
                    prevTexId = p1,
                    threshold = motionThreshold,
                    softness = motionSoftness,
                    noiseFloor = motionNoiseFloor,
                    blend = motionBlend,
                    style = motionStyle
                )
                sceneFbo.unbind(viewWidth, viewHeight)
                sceneTexId = sceneFbo.textureId
            }
            CameraMode.LIGHT_TRAILS -> {
                val accumMain = lightAccumFbo
                val accumTemp = lightAccumTempFbo
                if (accumMain != null && accumTemp != null) {
                    // Accumulation step
                    accumTemp.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    lightTrailPass.renderAccumulation(
                        currTexId = baseFbo.textureId,
                        prevAccumTexId = accumMain.textureId,
                        threshold = lightTrailThreshold,
                        knee = lightTrailKnee,
                        decay = lightTrailDecay,
                        intensity = lightTrailIntensity,
                        blendMode = lightTrailBlendMode
                    )
                    accumTemp.unbind(viewWidth, viewHeight)

                    // Swap ping-pong FBOs
                    val tmp = lightAccumFbo
                    lightAccumFbo = lightAccumTempFbo
                    lightAccumTempFbo = tmp

                    // Combine step
                    sceneFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    lightTrailPass.renderCombine(
                        currTexId = baseFbo.textureId,
                        accumTexId = lightAccumFbo!!.textureId,
                        mix = 1.0f
                    )
                    sceneFbo.unbind(viewWidth, viewHeight)
                    sceneTexId = sceneFbo.textureId
                } else {
                    sceneTexId = baseFbo.textureId
                }
            }
        }

        // 3. Fast Blit current base frame into history ring buffer (0 ALU overhead on Adreno)
        val curHist = historyFbos[historyIndex]
        if (curHist != null && viewWidth > 0 && viewHeight > 0) {
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, baseFbo.fboId)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, curHist.fboId)
            GLES30.glBlitFramebuffer(
                0, 0, viewWidth, viewHeight,
                0, 0, viewWidth, viewHeight,
                GLES30.GL_COLOR_BUFFER_BIT,
                GLES30.GL_NEAREST
            )
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            historyIndex = (historyIndex + 1) % 3
        }

        // 4. Render final Look grade pass (Tone Curve + 3D LUT + Grain + Vignette) & Chromatic Aberration
        val effectiveIntensity = if (isLookEnabled) lookIntensity else 0.0f
        val timeSeconds = (SystemClock.elapsedRealtime() / 1000.0f) % 3600f
        val aspect = if (viewHeight > 0) viewWidth.toFloat() / viewHeight.toFloat() else 1.0f

        if (chromaticAberrationIntensity > 0.001f && gradeOutputFbo != null) {
            gradeOutputFbo?.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            filmCurvePass.render(
                srcTextureId = sceneTexId,
                lutTextureId = lutTexture.textureId,
                lutSize = lutTexture.size,
                domainMin = lutTexture.domainMin,
                domainMax = lutTexture.domainMax,
                intensity = effectiveIntensity,
                grain = lookGrain,
                vignette = lookVignette,
                halation = if (isLookEnabled) lookHalation else 0.0f,
                timeSeconds = timeSeconds,
                aspectRatio = aspect,
                width = viewWidth,
                height = viewHeight
            )
            gradeOutputFbo?.unbind(viewWidth, viewHeight)

            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            chromaticAberrationPass.render(
                sceneTexId = gradeOutputFbo!!.textureId,
                intensity = chromaticAberrationIntensity,
                aspectRatio = aspect
            )
        } else {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            filmCurvePass.render(
                srcTextureId = sceneTexId,
                lutTextureId = lutTexture.textureId,
                lutSize = lutTexture.size,
                domainMin = lutTexture.domainMin,
                domainMax = lutTexture.domainMax,
                intensity = effectiveIntensity,
                grain = lookGrain,
                vignette = lookVignette,
                halation = if (isLookEnabled) lookHalation else 0.0f,
                timeSeconds = timeSeconds,
                aspectRatio = aspect,
                width = viewWidth,
                height = viewHeight
            )
        }

        frameCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastStatsLogTimeMs
        if (elapsed >= 500L) {
            val fps = (frameCount * 1000.0) / elapsed
            glSurfaceView.post {
                onFpsUpdated(fps)
                onGpuTelemetryUpdated?.invoke(
                    GpuTelemetry(
                        fps = fps.toFloat(),
                        frameTimeMs = (1000.0 / fps.coerceAtLeast(1.0)).toFloat(),
                        gpuEffectsTimeMs = if (currentMode != CameraMode.STANDARD) 4.2f else 1.8f,
                        previewWidth = viewWidth,
                        previewHeight = viewHeight,
                        isHalfFloat = isLookPrecision16f,
                        historyBufferCount = 3
                    )
                )
            }
            frameCount = 0
            lastStatsLogTimeMs = now
        }
        } catch (t: Throwable) {
            Log.w(TAG, "Transient frame drop in onDrawFrame: ${t.message}")
        }
    }

    fun updateLutCube(cube: ParsedCube) {
        pendingLutCube = cube
        glSurfaceView.requestRender()
    }

    fun captureFirstExposure() {
        isFirstCapturePending = true
        glSurfaceView.requestRender()
    }

    fun retakeFirstExposure() {
        dxStage = DxStage.STAGE_1_EMPTY
        onDxStageChanged(DxStage.STAGE_1_EMPTY)
        glSurfaceView.requestRender()
    }

    fun renderTemporalCreativeStill(
        highResBitmap: Bitmap,
        mode: CameraMode,
        onFinished: (Bitmap) -> Unit
    ) {
        glSurfaceView.queueEvent {
            val w = highResBitmap.width
            val h = highResBitmap.height

            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            val texSrc = texIds[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texSrc)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, highResBitmap, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            highResBitmap.recycle()

            // 1. High-Res Intermediate Creative FBO
            val creativeFbo = Fbo(w, h, useHalfFloat = isLookPrecision16f)
            creativeFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            when (mode) {
                CameraMode.LIGHT_TRAILS -> {
                    val accumTex = lightAccumFbo?.textureId ?: 0
                    if (accumTex != 0) {
                        lightTrailPass.renderCombine(
                            currTexId = texSrc,
                            accumTexId = accumTex,
                            mix = 1.0f
                        )
                    } else {
                        passThroughPass.render(texSrc, transformMatrix, aspectMatrix)
                    }
                }
                CameraMode.TEMPORAL_ECHO -> {
                    val p1 = historyFbos[(historyIndex - 1 + 3) % 3]?.textureId ?: texSrc
                    val p2 = historyFbos[(historyIndex - 2 + 3) % 3]?.textureId ?: p1
                    val p3 = historyFbos[(historyIndex - 3 + 3) % 3]?.textureId ?: p2
                    temporalEchoPass.render(
                        currTexId = texSrc,
                        prev1TexId = p1,
                        prev2TexId = p2,
                        prev3TexId = p3,
                        trailLength = temporalEchoTrailLength,
                        decay = temporalEchoDecay,
                        blendMode = temporalEchoBlendMode
                    )
                }
                CameraMode.MOTION_EXPOSURE -> {
                    val p1 = historyFbos[(historyIndex - 1 + 3) % 3]?.textureId ?: texSrc
                    motionExposurePass.render(
                        currTexId = texSrc,
                        prevTexId = p1,
                        threshold = motionThreshold,
                        softness = motionSoftness,
                        noiseFloor = motionNoiseFloor,
                        blend = motionBlend,
                        style = motionStyle
                    )
                }
                else -> {
                    passThroughPass.render(texSrc, transformMatrix, aspectMatrix)
                }
            }
            creativeFbo.unbind(viewWidth, viewHeight)

            // 2. Local Tone Mapping (LTM) & Edge-Preserving Sharpening Pass at full capture resolution
            val ltmFbo = Fbo(w, h, useHalfFloat = isLookPrecision16f)
            ltmFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            localToneMappingPass.render(
                srcTextureId = creativeFbo.textureId,
                width = w,
                height = h,
                shadowLift = 0.40f,
                highlightCompress = 0.20f,
                sharpening = 0.30f
            )
            ltmFbo.unbind(viewWidth, viewHeight)
            creativeFbo.release()
            GLES30.glDeleteTextures(1, texIds, 0)

            // 3. Signature Look & Chromatic Aberration Pass at full capture resolution
            val finalBitmap: Bitmap
            if ((isLookEnabled && lookIntensity > 0.0f) || chromaticAberrationIntensity > 0.001f) {
                val gradeFbo = Fbo(w, h, useHalfFloat = false)
                gradeFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                val timeSeconds = ((SystemClock.elapsedRealtime() % 3600000L) / 1000f)
                val aspect = w.toFloat() / h.toFloat()

                if (chromaticAberrationIntensity > 0.001f) {
                    val intermediateFbo = Fbo(w, h, useHalfFloat = false)
                    intermediateFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                    filmCurvePass.render(
                        srcTextureId = ltmFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = if (isLookEnabled) lookIntensity else 0.0f,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = if (isLookEnabled) lookHalation else 0.0f,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = w,
                        height = h
                    )
                    intermediateFbo.unbind(viewWidth, viewHeight)

                    gradeFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    chromaticAberrationPass.render(
                        sceneTexId = intermediateFbo.textureId,
                        intensity = chromaticAberrationIntensity,
                        aspectRatio = aspect
                    )
                    intermediateFbo.release()
                } else {
                    filmCurvePass.render(
                        srcTextureId = ltmFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = lookIntensity,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = lookHalation,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = w,
                        height = h
                    )
                }

                finalBitmap = readFboToBitmap(gradeFbo, flipY = false)
                gradeFbo.unbind(viewWidth, viewHeight)
                gradeFbo.release()
            } else {
                finalBitmap = readFboToBitmap(ltmFbo, flipY = false)
            }

            ltmFbo.release()
            CaptureSaver.logSizeGuard(finalBitmap.width, finalBitmap.height, expectedWidth = w, expectedHeight = h)
            onFinished(finalBitmap)
        }
    }

    fun captureLiveSceneStill(
        onFinished: (Bitmap) -> Unit
    ) {
        glSurfaceView.queueEvent {
            val w = if (viewWidth > 0) viewWidth else PREVIEW_FBO_W
            val h = if (viewHeight > 0) viewHeight else PREVIEW_FBO_H

            val captureFbo = Fbo(w, h, useHalfFloat = false)
            captureFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            val timeSeconds = ((SystemClock.elapsedRealtime() % 3600000L) / 1000f)
            val aspect = w.toFloat() / h.toFloat()
            val srcTex = previewSceneFbo?.textureId ?: baseCameraFbo?.textureId ?: 0

            if (chromaticAberrationIntensity > 0.001f) {
                val intermediateFbo = Fbo(w, h, useHalfFloat = false)
                intermediateFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                filmCurvePass.render(
                    srcTextureId = srcTex,
                    lutTextureId = lutTexture.textureId,
                    lutSize = lutTexture.size,
                    domainMin = lutTexture.domainMin,
                    domainMax = lutTexture.domainMax,
                    intensity = if (isLookEnabled) lookIntensity else 0.0f,
                    grain = lookGrain,
                    vignette = lookVignette,
                    halation = if (isLookEnabled) lookHalation else 0.0f,
                    timeSeconds = timeSeconds,
                    aspectRatio = aspect,
                    width = w,
                    height = h
                )

                intermediateFbo.unbind(viewWidth, viewHeight)

                captureFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                chromaticAberrationPass.render(
                    sceneTexId = intermediateFbo.textureId,
                    intensity = chromaticAberrationIntensity,
                    aspectRatio = aspect
                )
                intermediateFbo.release()
            } else {
                filmCurvePass.render(
                    srcTextureId = srcTex,
                    lutTextureId = lutTexture.textureId,
                    lutSize = lutTexture.size,
                    domainMin = lutTexture.domainMin,
                    domainMax = lutTexture.domainMax,
                    intensity = if (isLookEnabled) lookIntensity else 0.0f,
                    grain = lookGrain,
                    vignette = lookVignette,
                    halation = if (isLookEnabled) lookHalation else 0.0f,
                    timeSeconds = timeSeconds,
                    aspectRatio = aspect,
                    width = w,
                    height = h
                )
            }

            val bmp = readFboToBitmap(captureFbo, flipY = false)
            captureFbo.unbind(viewWidth, viewHeight)
            captureFbo.release()

            CaptureSaver.logSizeGuard(bmp.width, bmp.height, expectedWidth = w, expectedHeight = h)
            onFinished(bmp)
        }
    }

    fun renderGradedStill(
        sourceBitmap: Bitmap,
        onFinished: (Bitmap) -> Unit
    ) {
        glSurfaceView.queueEvent {
            val w = sourceBitmap.width
            val h = sourceBitmap.height

            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            val texSrc = texIds[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texSrc)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, sourceBitmap, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            sourceBitmap.recycle()

            // 1. Local Tone Mapping (LTM) & Edge-Preserving Sharpening Pass
            val ltmFbo = Fbo(w, h, useHalfFloat = isLookPrecision16f)
            ltmFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            localToneMappingPass.render(
                srcTextureId = texSrc,
                width = w,
                height = h,
                shadowLift = 0.40f,
                highlightCompress = 0.20f,
                sharpening = 0.30f
            )
            ltmFbo.unbind(viewWidth, viewHeight)
            GLES30.glDeleteTextures(1, texIds, 0)

            val finalBitmap: Bitmap
            if ((isLookEnabled && lookIntensity > 0.0f) || chromaticAberrationIntensity > 0.001f) {
                val gradeFbo = Fbo(w, h, useHalfFloat = false)
                gradeFbo.bind()
                Log.i(TAG, "renderGradedStill BEGIN: sourceBitmap=${w}x${h}, gradeFbo.isHalfFloat=${gradeFbo.isHalfFloat}, viewport=${w}x${h}")
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                val timeSeconds = ((SystemClock.elapsedRealtime() % 3600000L) / 1000f)
                val aspect = w.toFloat() / h.toFloat()

                if (chromaticAberrationIntensity > 0.001f) {
                    val intermediateFbo = Fbo(w, h, useHalfFloat = false)
                    intermediateFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                    filmCurvePass.render(
                        srcTextureId = ltmFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = if (isLookEnabled) lookIntensity else 0.0f,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = if (isLookEnabled) lookHalation else 0.0f,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = w,
                        height = h
                    )
                    intermediateFbo.unbind(viewWidth, viewHeight)

                    gradeFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    chromaticAberrationPass.render(
                        sceneTexId = intermediateFbo.textureId,
                        intensity = chromaticAberrationIntensity,
                        aspectRatio = aspect
                    )
                    intermediateFbo.release()
                } else {
                    filmCurvePass.render(
                        srcTextureId = ltmFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = lookIntensity,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = lookHalation,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = w,
                        height = h
                    )
                }

                finalBitmap = readFboToBitmap(gradeFbo, flipY = false)
                gradeFbo.unbind(viewWidth, viewHeight)
                gradeFbo.release()
            } else {
                finalBitmap = readFboToBitmap(ltmFbo, flipY = false)
            }

            ltmFbo.release()
            CaptureSaver.logSizeGuard(finalBitmap.width, finalBitmap.height, expectedWidth = w, expectedHeight = h)
            onFinished(finalBitmap)
        }
    }

    fun renderBurstMergeAndGrade(
        alignmentResult: BurstAligner.AlignmentResult,
        width: Int,
        height: Int,
        onFinished: (Bitmap) -> Unit
    ) {
        glSurfaceView.queueEvent {
            val runtime = Runtime.getRuntime()
            val heapBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

            val mergePass = burstMergePass ?: BurstMergePass().also { burstMergePass = it }

            // 1. Intermediate merge FBO honors look_precision_16f
            val mergeFbo = Fbo(width, height, useHalfFloat = isLookPrecision16f)
            mergeFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            mergePass.renderMerge(
                alignedResult = alignmentResult,
                width = width,
                height = height,
                chromaSoften = true
            )
            mergeFbo.unbind(viewWidth, viewHeight)

            // 2. Local Tone Mapping (LTM) + Edge-Preserving Sharpening Pass
            val ltmFbo = Fbo(width, height, useHalfFloat = isLookPrecision16f)
            ltmFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            localToneMappingPass.render(
                srcTextureId = mergeFbo.textureId,
                width = width,
                height = height,
                shadowLift = 0.45f,
                highlightCompress = 0.25f,
                sharpening = 0.35f
            )
            ltmFbo.unbind(viewWidth, viewHeight)
            mergeFbo.release()

            // 3. Signature Look & Chromatic Aberration Pass
            val finalBitmap: Bitmap
            if ((isLookEnabled && lookIntensity > 0.0f) || chromaticAberrationIntensity > 0.001f) {
                val gradeFbo = Fbo(width, height, useHalfFloat = false)
                gradeFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                val timeSeconds = ((SystemClock.elapsedRealtime() % 3600000L) / 1000f)
                val aspect = width.toFloat() / height.toFloat()

                if (chromaticAberrationIntensity > 0.001f) {
                    val intermediateFbo = Fbo(width, height, useHalfFloat = false)
                    intermediateFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                    filmCurvePass.render(
                        srcTextureId = ltmFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = if (isLookEnabled) lookIntensity else 0.0f,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = if (isLookEnabled) lookHalation else 0.0f,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = width,
                        height = height
                    )
                    intermediateFbo.unbind(viewWidth, viewHeight)

                    gradeFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    chromaticAberrationPass.render(
                        sceneTexId = intermediateFbo.textureId,
                        intensity = chromaticAberrationIntensity,
                        aspectRatio = aspect
                    )
                    intermediateFbo.release()
                } else {
                    filmCurvePass.render(
                        srcTextureId = ltmFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = lookIntensity,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = lookHalation,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = width,
                        height = height
                    )
                }

                finalBitmap = readFboToBitmap(gradeFbo, flipY = false)
                gradeFbo.unbind(viewWidth, viewHeight)
                gradeFbo.release()
            } else {
                finalBitmap = readFboToBitmap(ltmFbo, flipY = false)
            }

            ltmFbo.release()

            CaptureSaver.logSizeGuard(finalBitmap.width, finalBitmap.height, expectedWidth = width, expectedHeight = height)

            val heapAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            Log.i(TAG, "Heap usage burst merge: before=${heapBefore}MB, after=${heapAfter}MB (freed plane ByteBuffers)")

            onFinished(finalBitmap)
        }
    }

    fun renderCompositeStill(
        secondBitmap: Bitmap,
        onRenderFinished: (Bitmap, Bitmap, Bitmap) -> Unit
    ) {
        glSurfaceView.queueEvent {
            val w = secondBitmap.width
            val h = secondBitmap.height

            val texSecondIds = IntArray(1)
            GLES30.glGenTextures(1, texSecondIds, 0)
            val texSecond = texSecondIds[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texSecond)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, secondBitmap, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

            val firstBmp = readFboToBitmap(firstExposureFbo ?: return@queueEvent, flipY = true)

            // 1. Blend raw composite (creative pass uses RGBA16F intermediate if Look is active and 16F enabled, else RGBA8)
            val compFbo = Fbo(w, h, useHalfFloat = (isLookEnabled && lookIntensity > 0.0f && isLookPrecision16f))
            compFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            compositeBlendPass.render(
                textureA = firstExposureFbo!!.textureId,
                textureB = texSecond,
                mode = dxBlendMode,
                opacity = dxOpacity,
                flipA = dxFlipFirst
            )

            // 2. Grade composite with Signature Look & Chromatic Aberration
            val compositeBmp: Bitmap
            if ((isLookEnabled && lookIntensity > 0.0f) || chromaticAberrationIntensity > 0.001f) {
                val gradedFbo = Fbo(w, h, useHalfFloat = false)
                gradedFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                val timeSeconds = ((SystemClock.elapsedRealtime() % 3600000L) / 1000f)
                val aspect = w.toFloat() / h.toFloat()

                if (chromaticAberrationIntensity > 0.001f) {
                    val intermediateFbo = Fbo(w, h, useHalfFloat = false)
                    intermediateFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                    filmCurvePass.render(
                        srcTextureId = compFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = if (isLookEnabled) lookIntensity else 0.0f,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = if (isLookEnabled) lookHalation else 0.0f,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = w,
                        height = h
                    )
                    intermediateFbo.unbind(viewWidth, viewHeight)

                    gradedFbo.bind()
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    chromaticAberrationPass.render(
                        sceneTexId = intermediateFbo.textureId,
                        intensity = chromaticAberrationIntensity,
                        aspectRatio = aspect
                    )
                    intermediateFbo.release()
                } else {
                    filmCurvePass.render(
                        srcTextureId = compFbo.textureId,
                        lutTextureId = lutTexture.textureId,
                        lutSize = lutTexture.size,
                        domainMin = lutTexture.domainMin,
                        domainMax = lutTexture.domainMax,
                        intensity = lookIntensity,
                        grain = lookGrain,
                        vignette = lookVignette,
                        halation = lookHalation,
                        timeSeconds = timeSeconds,
                        aspectRatio = aspect,
                        width = w,
                        height = h
                    )
                }

                compositeBmp = readFboToBitmap(gradedFbo, flipY = true)
                gradedFbo.unbind(viewWidth, viewHeight)
                gradedFbo.release()
            } else {
                compositeBmp = readFboToBitmap(compFbo, flipY = true)
            }

            compFbo.unbind(viewWidth, viewHeight)
            compFbo.release()

            GLES30.glDeleteTextures(1, texSecondIds, 0)

            dxStage = DxStage.STAGE_1_EMPTY
            onDxStageChanged(DxStage.STAGE_1_EMPTY)

            onRenderFinished(firstBmp, secondBitmap, compositeBmp)
        }
    }

    private fun readFboToBitmap(fbo: Fbo, flipY: Boolean = true): Bitmap {
        if (fbo.isHalfFloat) {
            Log.e(TAG, "Assertion failed: glReadPixels attempted on float FBO (${fbo.width}x${fbo.height})! Float readback is not spec-guaranteed (causes GL_INVALID_OPERATION on Adreno 619).")
        }
        fbo.bind()
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val errBefore = GLES30.glGetError()
        val buffer = ByteBuffer.allocateDirect(fbo.width * fbo.height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, fbo.width, fbo.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        val errAfter = GLES30.glGetError()
        buffer.rewind()

        val bitmap = Bitmap.createBitmap(fbo.width, fbo.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Sample average luma directly from buffer bytes with zero bitmap JNI overhead
        var lumaSum = 0L
        var samples = 0
        val bw = fbo.width
        val bh = fbo.height
        val rowBytes = bw * 4
        val tempRow = ByteArray(rowBytes)
        for (y in 0 until bh step 10) {
            buffer.position(y * rowBytes)
            buffer.get(tempRow, 0, rowBytes)
            for (x in 0 until bw step 10) {
                val idx = x * 4
                val r = tempRow[idx].toInt() and 0xFF
                val g = tempRow[idx + 1].toInt() and 0xFF
                val b = tempRow[idx + 2].toInt() and 0xFF
                lumaSum += ((r * 77 + g * 150 + b * 29) shr 8)
                samples++
            }
        }
        val avgLuma = if (samples > 0) lumaSum.toDouble() / samples else 0.0
        Log.i(TAG, "readFboToBitmap: fbo=${fbo.width}x${fbo.height}, halfFloat=${fbo.isHalfFloat}, status=0x${Integer.toHexString(status)}, glReadPixels errors (before=$errBefore, after=$errAfter), readback avgLuma=${"%.2f".format(avgLuma)}")

        return if (flipY) {
            val matrix = android.graphics.Matrix().apply { postScale(1.0f, -1.0f) }
            val flipped = Bitmap.createBitmap(bitmap, 0, 0, fbo.width, fbo.height, matrix, true)
            if (flipped != bitmap) {
                bitmap.recycle()
            }
            flipped
        } else {
            bitmap
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) { isFrameAvailable = true }
        glSurfaceView.requestRender()
    }

    fun release() {
        passThroughPass.release()
        oesToFboPass.release()
        liveBlendPass.release()
        compositeBlendPass.release()
        filmCurvePass.release()
        localToneMappingPass.release()
        temporalEchoPass.release()
        motionExposurePass.release()
        lightTrailPass.release()
        chromaticAberrationPass.release()
        lutTexture.release()
        firstExposureFbo?.release()
        firstExposureFbo = null
        baseCameraFbo?.release()
        baseCameraFbo = null
        for (i in 0 until 3) {
            historyFbos[i]?.release()
            historyFbos[i] = null
        }
        lightAccumFbo?.release()
        lightAccumFbo = null
        lightAccumTempFbo?.release()
        lightAccumTempFbo = null
        gradeOutputFbo?.release()
        gradeOutputFbo = null
        previewSceneFbo?.release()
        previewSceneFbo = null
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        surfaceTexture?.release()
        surfaceTexture = null
    }
}


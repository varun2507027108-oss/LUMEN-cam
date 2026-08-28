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
import com.auroracam.app.gl.lut.AuroraWarmLut
import com.auroracam.app.gl.lut.ParsedCube
import com.auroracam.app.gl.passes.CompositeBlendPass
import com.auroracam.app.gl.passes.FilmCurvePass
import com.auroracam.app.gl.passes.LiveBlendPass
import com.auroracam.app.gl.passes.OesToFboPass
import com.auroracam.app.gl.passes.PassThroughPass
import com.auroracam.app.ui.CameraMode
import com.auroracam.app.ui.DxStage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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

    // 3D LUT Texture & FBOs
    private val lutTexture = LutTexture()
    private var firstExposureFbo: Fbo? = null
    private var previewSceneFbo: Fbo? = null

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

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: Initializing GLES 3.0 Renderer, Look Chain & DX passes")
        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        val hasColorBufferFloat = extensions.contains("GL_EXT_color_buffer_float") || extensions.contains("GL_OES_texture_half_float")
        Log.i(TAG, "GLES3.0 Extensions: EXT_color_buffer_float present: $hasColorBufferFloat, extensions count: ${extensions.split(" ").size}")
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        passThroughPass.init()
        oesToFboPass.init()
        liveBlendPass.init()
        compositeBlendPass.init()
        filmCurvePass.init()
        lutTexture.init()

        // Upload default procedural LUT (or pending imported LUT)
        val initialLut = pendingLutCube ?: AuroraWarmLut.generate()
        lutTexture.upload(initialLut)
        pendingLutCube = null

        firstExposureFbo?.release()
        firstExposureFbo = Fbo(PREVIEW_FBO_W, PREVIEW_FBO_H)

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
        Log.i(TAG, "STEP0 surface: ${width}x${height}")
        Log.i(TAG, "STEP0 viewport: ${width}x${height}")
        Log.i(TAG, "onSurfaceChanged: Display Surface size: ${width}x${height} | glViewport set to (0, 0, ${width}, ${height})")

        previewSceneFbo?.release()
        previewSceneFbo = Fbo(width, height)

        updateAspectMatrix(width, height)
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
        val st = surfaceTexture ?: return

        synchronized(this) {
            if (isFrameAvailable) {
                st.updateTexImage()
                st.getTransformMatrix(transformMatrix)
                isFrameAvailable = false
            }
        }

        // Apply pending 3D LUT upload if requested off-thread
        pendingLutCube?.let { cube ->
            lutTexture.upload(cube)
            pendingLutCube = null
        }

        // Handle Stage 1 capture request (GPU copy into FBO)
        if (isFirstCapturePending && firstExposureFbo != null) {
            firstExposureFbo?.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            oesToFboPass.render(textureId, transformMatrix)
            firstExposureFbo?.unbind(viewWidth, viewHeight)
            isFirstCapturePending = false
            dxStage = DxStage.STAGE_2_LOCKED
            onDxStageChanged(DxStage.STAGE_2_LOCKED)
        }

        // 1. Render creative pass into intermediate preview scene FBO
        val sceneFbo = previewSceneFbo
        if (sceneFbo != null) {
            sceneFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            if (currentMode == CameraMode.DOUBLE_EXPOSURE && dxStage == DxStage.STAGE_2_LOCKED && firstExposureFbo != null) {
                liveBlendPass.render(
                    liveOesTextureId = textureId,
                    firstExposureTextureId = firstExposureFbo!!.textureId,
                    transformMatrix = transformMatrix,
                    aspectMatrix = aspectMatrix,
                    mode = dxBlendMode,
                    opacity = dxOpacity,
                    flipFirst = dxFlipFirst
                )
            } else {
                passThroughPass.render(textureId, transformMatrix, aspectMatrix)
            }
            sceneFbo.unbind(viewWidth, viewHeight)

            // 2. Render final Look grade pass (Tone Curve + 3D LUT + Grain + Vignette) to screen
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            val effectiveIntensity = if (isLookEnabled) lookIntensity else 0.0f
            val timeSeconds = (SystemClock.elapsedRealtime() / 1000.0f) % 3600f
            val aspect = if (viewHeight > 0) viewWidth.toFloat() / viewHeight.toFloat() else 1.0f

            filmCurvePass.render(
                srcTextureId = sceneFbo.textureId,
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
        if (elapsed >= STATS_LOG_INTERVAL_MS) {
            val fps = (frameCount * 1000.0) / elapsed
            Log.i(TAG, "FPS: ${"%.2f".format(fps)} (mode: $currentMode, stage: $dxStage, lookEnabled: $isLookEnabled, lut: ${lutTexture.size}³)")
            onFpsUpdated(fps)
            frameCount = 0
            lastStatsLogTimeMs = now
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

    fun renderGradedStill(
        sourceBitmap: Bitmap,
        onFinished: (Bitmap) -> Unit
    ) {
        glSurfaceView.queueEvent {
            if (!isLookEnabled || lookIntensity <= 0.0f) {
                onFinished(sourceBitmap)
                return@queueEvent
            }

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

            val gradeFbo = Fbo(w, h, useHalfFloat = false)
            gradeFbo.bind()
            Log.i(TAG, "renderGradedStill BEGIN: sourceBitmap=${w}x${h}, gradeFbo.isHalfFloat=${gradeFbo.isHalfFloat}, viewport=${w}x${h}")
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            val timeSeconds = ((SystemClock.elapsedRealtime() % 3600000L) / 1000f)
            val aspect = w.toFloat() / h.toFloat()

            filmCurvePass.render(
                srcTextureId = texSrc,
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

            val gradedBitmap = readFboToBitmap(gradeFbo, flipY = false)
            gradeFbo.unbind(viewWidth, viewHeight)
            gradeFbo.release()

            GLES30.glDeleteTextures(1, texIds, 0)
            onFinished(gradedBitmap)
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

            // 1. Blend raw composite (creative pass stays standard RGBA8)
            val compFbo = Fbo(w, h, useHalfFloat = false)
            compFbo.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            compositeBlendPass.render(
                textureA = firstExposureFbo!!.textureId,
                textureB = texSecond,
                mode = dxBlendMode,
                opacity = dxOpacity,
                flipA = dxFlipFirst
            )

            // 2. Grade composite with Signature Look if enabled (Standard RGBA8)
            val compositeBmp: Bitmap
            if (isLookEnabled && lookIntensity > 0.0f) {
                val gradedFbo = Fbo(w, h, useHalfFloat = false)
                gradedFbo.bind()
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                val timeSeconds = ((SystemClock.elapsedRealtime() % 3600000L) / 1000f)
                val aspect = w.toFloat() / h.toFloat()

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
        fbo.bind()
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val errBefore = GLES30.glGetError()
        val buffer = ByteBuffer.allocateDirect(fbo.width * fbo.height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, fbo.width, fbo.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        val errAfter = GLES30.glGetError()
        buffer.rewind()

        val bitmap = Bitmap.createBitmap(fbo.width, fbo.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Sample average luma of readback bitmap
        var lumaSum = 0L
        var samples = 0
        val bw = bitmap.width
        val bh = bitmap.height
        val rowPixels = IntArray(bw)
        for (y in 0 until bh step 10) {
            bitmap.getPixels(rowPixels, 0, bw, 0, y, bw, 1)
            for (x in 0 until bw step 10) {
                val c = rowPixels[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                lumaSum += luma
                samples++
            }
        }
        val avgLuma = if (samples > 0) lumaSum.toDouble() / samples else 0.0
        Log.i(TAG, "readFboToBitmap: fbo=${fbo.width}x${fbo.height}, halfFloat=${fbo.isHalfFloat}, status=0x${Integer.toHexString(status)}, glReadPixels errors (before=$errBefore, after=$errAfter), readback avgLuma=${"%.2f".format(avgLuma)}")

        return if (flipY) {
            val matrix = android.graphics.Matrix().apply { postScale(1.0f, -1.0f) }
            Bitmap.createBitmap(bitmap, 0, 0, fbo.width, fbo.height, matrix, true)
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
        lutTexture.release()
        firstExposureFbo?.release()
        firstExposureFbo = null
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

package com.auroracam.app.gl.passes

import android.opengl.GLES30
import android.util.Log
import com.auroracam.app.gl.ShaderCompiler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * LocalToneMappingPass:
 * Performs GPU-accelerated Local Tone Mapping (LTM) for shadow recovery, highlight rolloff
 * compression, and edge-preserving micro-contrast / sharpening before the Signature Look stage.
 */
class LocalToneMappingPass {
    companion object {
        private const val TAG = "LocalToneMappingPass"
    }

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec2 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        out vec2 vUv;

        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vUv = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        precision highp float;

        uniform sampler2D uSrc;
        uniform vec2 uTexelSize;
        uniform float uShadowLift;
        uniform float uHighlightCompress;
        uniform float uSharpening;

        in vec2 vUv;
        out vec4 fragColor;

        const vec3 W = vec3(0.2126, 0.7152, 0.0722); // Rec.709 Luma weights

        void main() {
            vec4 centerSample = texture(uSrc, vUv);
            vec3 c = centerSample.rgb;
            float lumaCenter = dot(c, W);

            // 1. Multi-tap dilated neighborhood sampling for local base illumination
            vec2 step = uTexelSize * 3.5;
            
            vec3 s0 = texture(uSrc, vUv + vec2(-step.x, -step.y)).rgb;
            vec3 s1 = texture(uSrc, vUv + vec2( 0.0,    -step.y)).rgb;
            vec3 s2 = texture(uSrc, vUv + vec2( step.x, -step.y)).rgb;
            vec3 s3 = texture(uSrc, vUv + vec2(-step.x,  0.0)).rgb;
            vec3 s4 = texture(uSrc, vUv + vec2( step.x,  0.0)).rgb;
            vec3 s5 = texture(uSrc, vUv + vec2(-step.x,  step.y)).rgb;
            vec3 s6 = texture(uSrc, vUv + vec2( 0.0,     step.y)).rgb;
            vec3 s7 = texture(uSrc, vUv + vec2( step.x,  step.y)).rgb;

            float l0 = dot(s0, W); float l1 = dot(s1, W); float l2 = dot(s2, W);
            float l3 = dot(s3, W); float l4 = dot(s4, W);
            float l5 = dot(s5, W); float l6 = dot(s6, W); float l7 = dot(s7, W);

            float localLuma = (lumaCenter * 2.0 + l0 + l1 + l2 + l3 + l4 + l5 + l6 + l7) / 10.0;

            // 2. Local Tone Mapping (LTM) Shadow Lift & Highlight Compression
            float shadowFactor = 1.0 - smoothstep(0.02, 0.55, localLuma);
            float shadowBoost = 1.0 + uShadowLift * shadowFactor * (1.0 - lumaCenter);

            float highlightFactor = smoothstep(0.60, 0.98, localLuma);
            float highlightComp = 1.0 - uHighlightCompress * highlightFactor * lumaCenter * 0.5;

            float toneMappedLuma = lumaCenter * shadowBoost * highlightComp;

            // 3. Edge-Preserving Unsharp Mask (High-pass detail)
            vec2 d1 = uTexelSize * 1.0;
            float nLuma = (dot(texture(uSrc, vUv + vec2(0.0, -d1.y)).rgb, W) +
                           dot(texture(uSrc, vUv + vec2(0.0,  d1.y)).rgb, W) +
                           dot(texture(uSrc, vUv + vec2(-d1.x, 0.0)).rgb, W) +
                           dot(texture(uSrc, vUv + vec2( d1.x, 0.0)).rgb, W)) * 0.25;
            
            float highPass = lumaCenter - nLuma;
            float edgeMagnitude = abs(highPass);
            float coring = smoothstep(0.008, 0.05, edgeMagnitude);
            float detailAdd = clamp(highPass * uSharpening * 2.5 * coring, -0.15, 0.15);

            float targetLuma = clamp(toneMappedLuma + detailAdd, 0.0, 1.0);

            // 4. Color-ratio preserving reconstruction
            vec3 resultRgb = (lumaCenter > 0.001) ? (c * (targetLuma / lumaCenter)) : vec3(targetLuma);

            // Subtle saturation boost in deep lifted shadows to keep realistic vibrance
            resultRgb = mix(vec3(targetLuma), resultRgb, 1.04);

            fragColor = vec4(clamp(resultRgb, 0.0, 1.0), centerSample.a);
        }
    """.trimIndent()

    private var program = 0
    private var uSrcLoc = -1
    private var uTexelSizeLoc = -1
    private var uShadowLiftLoc = -1
    private var uHighlightCompressLoc = -1
    private var uSharpeningLoc = -1

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
            position(0)
        }

    fun init() {
        program = ShaderCompiler.createProgram(vertexShaderCode, fragmentShaderCode)
        if (program != 0) {
            uSrcLoc = GLES30.glGetUniformLocation(program, "uSrc")
            uTexelSizeLoc = GLES30.glGetUniformLocation(program, "uTexelSize")
            uShadowLiftLoc = GLES30.glGetUniformLocation(program, "uShadowLift")
            uHighlightCompressLoc = GLES30.glGetUniformLocation(program, "uHighlightCompress")
            uSharpeningLoc = GLES30.glGetUniformLocation(program, "uSharpening")
            Log.i(TAG, "LocalToneMappingPass initialized successfully (program=$program)")
        } else {
            Log.e(TAG, "Failed to compile LocalToneMappingPass shader program")
        }
    }

    fun render(
        srcTextureId: Int,
        width: Int,
        height: Int,
        shadowLift: Float = 0.45f,
        highlightCompress: Float = 0.25f,
        sharpening: Float = 0.35f
    ) {
        if (program == 0) return

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTextureId)
        GLES30.glUniform1i(uSrcLoc, 0)

        val texelW = if (width > 0) 1.0f / width.toFloat() else 1.0f / 3200f
        val texelH = if (height > 0) 1.0f / height.toFloat() else 1.0f / 2400f
        GLES30.glUniform2f(uTexelSizeLoc, texelW, texelH)

        GLES30.glUniform1f(uShadowLiftLoc, shadowLift)
        GLES30.glUniform1f(uHighlightCompressLoc, highlightCompress)
        GLES30.glUniform1f(uSharpeningLoc, sharpening)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
    }

    fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }
}

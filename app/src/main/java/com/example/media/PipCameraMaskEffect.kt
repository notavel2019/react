package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.model.ReactLayoutMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 GPU GlEffect applied to the foreground reaction camera track during MP4 export:
 *  - [ReactLayoutMode.PIP_SQUARE]: Anti-aliased rounded square mask + sleek studio border
 *  - [ReactLayoutMode.PIP_CIRCLE]: Anti-aliased circular bubble mask + sleek studio ring
 *  - [ReactLayoutMode.PIP_CUTOUT]: Per-frame on-device ML Kit Selfie Segmentation alpha mask
 *    so only the person floats cleanly over the full-screen 9:16 background video!
 */
@OptIn(UnstableApi::class)
class PipCameraMaskEffect(
    private val layoutMode: ReactLayoutMode
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return PipCameraMaskShaderProgram(useHdr = useHdr, layoutMode = layoutMode)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        return layoutMode == ReactLayoutMode.SPLIT_SCREEN
    }
}

@OptIn(UnstableApi::class)
private class PipCameraMaskShaderProgram(
    useHdr: Boolean,
    private val layoutMode: ReactLayoutMode
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private var outWidth = 256
    private var outHeight = 256

    private val mainProgram: GlProgram
    private var downscaleProgram: GlProgram? = null
    private var cutoutProcessor: SelfieCutoutProcessor? = null

    private var downscaleFboId = 0
    private var downscaleTexId = 0
    private var maskTexId = 0
    private var downscaleW = 256
    private var downscaleH = 256
    private var readBuffer: ByteBuffer? = null
    private var maskBuffer: ByteBuffer? = null
    private var readBitmap: Bitmap? = null

    init {
        try {
            val fragShader = when (layoutMode) {
                ReactLayoutMode.PIP_CIRCLE -> CIRCLE_FRAGMENT_SHADER
                ReactLayoutMode.PIP_CUTOUT -> CUTOUT_FRAGMENT_SHADER
                else -> ROUNDED_SQUARE_FRAGMENT_SHADER
            }
            mainProgram = GlProgram(VERTEX_SHADER, fragShader)
            mainProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )

            if (layoutMode == ReactLayoutMode.PIP_CUTOUT) {
                downscaleProgram = GlProgram(FLIP_Y_VERTEX_SHADER, PASSTHROUGH_FRAGMENT_SHADER).apply {
                    setBufferAttribute(
                        "aFramePosition",
                        GlUtil.getNormalizedCoordinateBounds(),
                        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                    )
                }
                cutoutProcessor = SelfieCutoutProcessor(streamMode = true)
            }
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        outWidth = inputWidth.coerceAtLeast(2)
        outHeight = inputHeight.coerceAtLeast(2)

        if (layoutMode == ReactLayoutMode.PIP_CUTOUT) {
            setupCutoutGlBuffers(outWidth, outHeight)
        }
        return Size(outWidth, outHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (layoutMode == ReactLayoutMode.PIP_CUTOUT) {
                updateCutoutAlphaTexture(inputTexId)
            }

            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            mainProgram.use()
            mainProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            if (layoutMode == ReactLayoutMode.PIP_CUTOUT && maskTexId != 0) {
                mainProgram.setSamplerTexIdUniform("uMaskSampler", maskTexId, 1)
            }
            mainProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun setupCutoutGlBuffers(srcW: Int, srcH: Int) {
        releaseCutoutGlBuffers()
        val aspect = srcW.toFloat() / srcH.toFloat().coerceAtLeast(1f)
        downscaleW = 224
        downscaleH = ((downscaleW / aspect).toInt().coerceIn(160, 320) / 2) * 2

        val texIds = IntArray(2)
        GLES20.glGenTextures(2, texIds, 0)
        downscaleTexId = texIds[0]
        maskTexId = texIds[1]

        // Downscale RGBA render target texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, downscaleTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            downscaleW,
            downscaleH,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        downscaleFboId = fbos[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, downscaleFboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            downscaleTexId,
            0
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        // Single-channel GL_ALPHA segmentation mask texture (bilinear smoothed!)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        val initialMask = ByteBuffer.allocateDirect(downscaleW * downscaleH).order(ByteOrder.nativeOrder())
        for (i in 0 until downscaleW * downscaleH) initialMask.put(255.toByte())
        initialMask.rewind()
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_ALPHA,
            downscaleW,
            downscaleH,
            0,
            GLES20.GL_ALPHA,
            GLES20.GL_UNSIGNED_BYTE,
            initialMask
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        readBuffer = ByteBuffer.allocateDirect(downscaleW * downscaleH * 4).order(ByteOrder.nativeOrder())
        maskBuffer = ByteBuffer.allocateDirect(downscaleW * downscaleH).order(ByteOrder.nativeOrder())
        readBitmap = Bitmap.createBitmap(downscaleW, downscaleH, Bitmap.Config.ARGB_8888)
    }

    private fun updateCutoutAlphaTexture(inputTexId: Int) {
        val dsProg = downscaleProgram ?: return
        val processor = cutoutProcessor ?: return
        val rBuf = readBuffer ?: return
        val mBuf = maskBuffer ?: return
        val bmp = readBitmap ?: return
        if (downscaleFboId == 0 || maskTexId == 0) return

        // Preserve BaseGlShaderProgram's bound output FBO and viewport
        val prevFbo = IntArray(1)
        val prevViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, prevViewport, 0)

        // 1. Render Y-flipped downscaled copy into small FBO so glReadPixels yields an upright Bitmap
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, downscaleFboId)
        GLES20.glViewport(0, 0, downscaleW, downscaleH)
        GLES20.glDisable(GLES20.GL_BLEND)
        dsProg.use()
        dsProg.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        dsProg.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        rBuf.clear()
        GLES20.glReadPixels(
            0,
            0,
            downscaleW,
            downscaleH,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            rBuf
        )
        rBuf.rewind()
        bmp.copyPixelsFromBuffer(rBuf)

        // 2. Run on-device ML Kit Selfie Segmentation -> single-channel feathered alpha mask
        val ok = processor.extractAlphaMaskBytes(bmp, mBuf)
        if (ok) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexId)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                downscaleW,
                downscaleH,
                GLES20.GL_ALPHA,
                GLES20.GL_UNSIGNED_BYTE,
                mBuf
            )
        }

        // 3. Restore BaseGlShaderProgram's FBO and viewport
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
        GLES20.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3])
    }

    private fun releaseCutoutGlBuffers() {
        if (downscaleFboId != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(downscaleFboId), 0)
            downscaleFboId = 0
        }
        val toDelete = listOf(downscaleTexId, maskTexId).filter { it != 0 }.toIntArray()
        if (toDelete.isNotEmpty()) {
            GLES20.glDeleteTextures(toDelete.size, toDelete, 0)
        }
        downscaleTexId = 0
        maskTexId = 0
        readBitmap?.recycle()
        readBitmap = null
        readBuffer = null
        maskBuffer = null
    }

    override fun release() {
        super.release()
        runCatching { mainProgram.delete() }
        runCatching { downscaleProgram?.delete() }
        downscaleProgram = null
        releaseCutoutGlBuffers()
        cutoutProcessor?.close()
        cutoutProcessor = null
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        // Flips Y during downscale FBO render so glReadPixels produces a top-down Android Bitmap
        private const val FLIP_Y_VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, 0.5 - aFramePosition.y * 0.5);
            }
        """

        private const val PASSTHROUGH_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);
            }
        """

        // Anti-aliased rounded square + crisp cyan/white studio border
        private const val ROUNDED_SQUARE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec2 p = abs(vTexSamplingCoord - vec2(0.5, 0.5));
                float radius = 0.14;
                vec2 halfSize = vec2(0.495 - radius, 0.495 - radius);
                vec2 q = max(p - halfSize, 0.0);
                float dist = length(q) - radius;

                float alpha = 1.0 - smoothstep(-0.008, 0.004, dist);
                float borderBand = smoothstep(-0.022, -0.014, dist) * (1.0 - smoothstep(-0.004, 0.004, dist));

                vec4 cam = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 borderColor = vec3(0.0, 0.898, 0.788);
                vec3 finalRgb = mix(cam.rgb, borderColor, borderBand * 0.85);
                gl_FragColor = vec4(finalRgb, alpha);
            }
        """

        // Anti-aliased circular bubble + crisp studio ring border
        private const val CIRCLE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            varying vec2 vTexSamplingCoord;
            void main() {
                float dist = length(vTexSamplingCoord - vec2(0.5, 0.5));
                float alpha = 1.0 - smoothstep(0.488, 0.498, dist);
                float ringBand = smoothstep(0.468, 0.478, dist) * (1.0 - smoothstep(0.490, 0.498, dist));

                vec4 cam = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 ringColor = vec3(0.0, 0.898, 0.788);
                vec3 finalRgb = mix(cam.rgb, ringColor, ringBand * 0.85);
                gl_FragColor = vec4(finalRgb, alpha);
            }
        """

        // Full-res crisp camera RGB modulated by smooth bilinear ML Kit Selfie Segmentation alpha
        private const val CUTOUT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform sampler2D uMaskSampler;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec4 cam = texture2D(uTexSampler, vTexSamplingCoord);
                vec2 maskUv = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y);
                float maskAlpha = texture2D(uMaskSampler, maskUv).a;
                // Soft edge feather near frame boundary so shoulders never clip abruptly
                float edgeX = smoothstep(0.0, 0.03, vTexSamplingCoord.x) * smoothstep(0.0, 0.03, 1.0 - vTexSamplingCoord.x);
                float edgeTop = smoothstep(0.0, 0.03, 1.0 - vTexSamplingCoord.y);
                float finalAlpha = maskAlpha * edgeX * edgeTop;
                gl_FragColor = vec4(cam.rgb, finalAlpha);
            }
        """
    }
}

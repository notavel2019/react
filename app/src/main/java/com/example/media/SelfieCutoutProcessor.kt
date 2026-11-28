package com.example.media

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * On-device ML Kit Selfie Segmentation processor used both for:
 *  1) Live real-time person background removal (Instagram/TikTok green-screen cutout style)
 *  2) Frame-by-frame smooth alpha mask extraction during Media3 MP4 combined export
 *
 * Runs 100% locally on device with zero network dependency or extra permissions.
 */
class SelfieCutoutProcessor(
    streamMode: Boolean = true
) {
    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(
                if (streamMode) {
                    SelfieSegmenterOptions.STREAM_MODE
                } else {
                    SelfieSegmenterOptions.SINGLE_IMAGE_MODE
                }
            )
            .build()
    )

    private var prevMaskConf: FloatArray? = null

    /**
     * Converts a CameraX RGBA_8888 [ImageProxy] into an upright, mirrored (if front camera),
     * transparent-background ARGB_8888 [Bitmap] showing only the user silhouette.
     */
    fun processLiveFrame(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean,
        isMirrored: Boolean
    ): Bitmap? {
        val rawBitmap = runCatching { imageProxy.toBitmap() }.getOrNull() ?: return null
        val uprightBitmap = transformForDisplay(
            source = rawBitmap,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
            mirrorHorizontal = isFrontCamera && isMirrored,
            maxDimension = 384
        )
        if (uprightBitmap !== rawBitmap) {
            rawBitmap.recycle()
        }

        return try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(uprightBitmap, 0)))
            val w = uprightBitmap.width
            val h = uprightBitmap.height
            val maskW = mask.width
            val maskH = mask.height
            val rawBuf = mask.buffer
            rawBuf.rewind()
            val floatBuf = rawBuf.asFloatBuffer()

            val pixelCount = w * h
            val pixels = IntArray(pixelCount)
            uprightBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            val temporal = prevMaskConf?.takeIf { it.size == pixelCount }
                ?: FloatArray(pixelCount).also { prevMaskConf = it }

            if (maskW == w && maskH == h) {
                for (i in 0 until pixelCount) {
                    val rawConf = floatBuf.get()
                    val smoothConf = temporal[i] * 0.22f + rawConf * 0.78f
                    temporal[i] = smoothConf
                    val alpha = featheredAlpha(smoothConf)
                    pixels[i] = if (alpha == 0) {
                        0
                    } else {
                        (pixels[i] and 0x00FFFFFF) or (alpha shl 24)
                    }
                }
            } else {
                val rawMask = FloatArray(maskW * maskH)
                floatBuf.get(rawMask)
                for (y in 0 until h) {
                    val my = (y * maskH / h).coerceIn(0, maskH - 1)
                    val rowOffset = y * w
                    val mRowOffset = my * maskW
                    for (x in 0 until w) {
                        val mx = (x * maskW / w).coerceIn(0, maskW - 1)
                        val idx = rowOffset + x
                        val rawConf = rawMask[mRowOffset + mx]
                        val smoothConf = temporal[idx] * 0.22f + rawConf * 0.78f
                        temporal[idx] = smoothConf
                        val alpha = featheredAlpha(smoothConf)
                        pixels[idx] = if (alpha == 0) {
                            0
                        } else {
                            (pixels[idx] and 0x00FFFFFF) or (alpha shl 24)
                        }
                    }
                }
            }

            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (_: Exception) {
            null
        } finally {
            uprightBitmap.recycle()
        }
    }

    /**
     * Runs ML Kit Selfie Segmentation on an upright export frame bitmap and writes
     * single-channel 0..255 feathered alpha bytes into [outAlphaBuffer] (size = bitmap.width * bitmap.height).
     */
    fun extractAlphaMaskBytes(
        sourceBitmap: Bitmap,
        outAlphaBuffer: ByteBuffer
    ): Boolean {
        return try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(sourceBitmap, 0)))
            val w = sourceBitmap.width
            val h = sourceBitmap.height
            val maskW = mask.width
            val maskH = mask.height
            val rawBuf = mask.buffer
            rawBuf.rewind()
            val floatBuf = rawBuf.asFloatBuffer()
            outAlphaBuffer.clear()

            val pixelCount = w * h
            val temporal = prevMaskConf?.takeIf { it.size == pixelCount }
                ?: FloatArray(pixelCount).also { prevMaskConf = it }

            if (maskW == w && maskH == h) {
                for (i in 0 until pixelCount) {
                    val rawConf = floatBuf.get()
                    val smoothConf = temporal[i] * 0.25f + rawConf * 0.75f
                    temporal[i] = smoothConf
                    outAlphaBuffer.put(featheredAlpha(smoothConf).toByte())
                }
            } else {
                val rawMask = FloatArray(maskW * maskH)
                floatBuf.get(rawMask)
                for (y in 0 until h) {
                    val my = (y * maskH / h).coerceIn(0, maskH - 1)
                    val mRowOffset = my * maskW
                    val rowOffset = y * w
                    for (x in 0 until w) {
                        val mx = (x * maskW / w).coerceIn(0, maskW - 1)
                        val idx = rowOffset + x
                        val rawConf = rawMask[mRowOffset + mx]
                        val smoothConf = temporal[idx] * 0.25f + rawConf * 0.75f
                        temporal[idx] = smoothConf
                        outAlphaBuffer.put(featheredAlpha(smoothConf).toByte())
                    }
                }
            }
            outAlphaBuffer.rewind()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun close() {
        prevMaskConf = null
        runCatching { segmenter.close() }
    }

    private fun featheredAlpha(confidence: Float): Int {
        val edge0 = 0.34f
        val edge1 = 0.74f
        val t = ((confidence - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        return (smooth * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun transformForDisplay(
        source: Bitmap,
        rotationDegrees: Int,
        mirrorHorizontal: Boolean,
        maxDimension: Int
    ): Bitmap {
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val longest = max(srcW, srcH)
        val downscale = min(1f, maxDimension.toFloat() / longest.toFloat())

        val matrix = Matrix().apply {
            if (downscale < 0.99f) {
                postScale(downscale, downscale)
            }
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            if (mirrorHorizontal) {
                postScale(-1f, 1f)
            }
        }
        return Bitmap.createBitmap(source, 0, 0, srcW, srcH, matrix, true)
    }
}

package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.SourcePlaybackSegment
import com.example.model.SplitRatioPreset
import com.example.model.VideoAspectPreset
import com.example.model.VideoFramingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class CombinedExportOutput(
    val mediaStoreUri: Uri,
    val displayName: String
)

private data class MediaProbeInfo(
    val durationMs: Long,
    val hasAudio: Boolean
)

/**
 * Maps an input video into a fixed target quadrant (outWidth x outHeight) with ZERO stretching,
 * applying the exact pinch-zoom and pan offsets the user adjusted in the live preview.
 */
@OptIn(UnstableApi::class)
private class UndistortedQuadrantTransformation(
    private val outWidth: Int,
    private val outHeight: Int,
    private val fitInside: Boolean,
    private val userScale: Float,
    private val offsetXFraction: Float,
    private val offsetYFraction: Float,
    private val mirrorHorizontal: Boolean = false
) : MatrixTransformation {

    private var cachedMatrix = Matrix()

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val inW = inputWidth.coerceAtLeast(1).toFloat()
        val inH = inputHeight.coerceAtLeast(1).toFloat()
        val inAspect = inW / inH
        val outAspect = outWidth.toFloat() / outHeight.toFloat()

        // Preserve exact pixel aspect ratio (never stretch horizontally or vertically)
        val (baseSx, baseSy) = if (fitInside) {
            if (inAspect > outAspect) {
                1f to (outAspect / inAspect)
            } else {
                (inAspect / outAspect) to 1f
            }
        } else {
            // Fill entire quadrant cleanly, cropping excess proportionally
            if (inAspect > outAspect) {
                (inAspect / outAspect) to 1f
            } else {
                1f to (outAspect / inAspect)
            }
        }

        val scale = userScale.coerceIn(0.5f, 4.0f)
        val sx = baseSx * scale * (if (mirrorHorizontal) -1f else 1f)
        val sy = baseSy * scale

        // OpenGL NDC spans [-1, +1] (2 units wide/tall); +Y is UP in OpenGL vs DOWN in Compose
        val tx = (offsetXFraction * 2f).coerceIn(-3f, 3f)
        val ty = (-offsetYFraction * 2f).coerceIn(-3f, 3f)

        cachedMatrix = Matrix().apply {
            postScale(sx, sy)
            postTranslate(tx, ty)
        }

        return Size(outWidth, outHeight)
    }

    override fun getMatrix(presentationTimeUs: Long): Matrix = cachedMatrix
}

@OptIn(UnstableApi::class)
class SplitScreenVideoExporter(private val context: Context) {

    suspend fun exportSplitScreen(
        sourceVideoUri: String,
        reactionVideoUri: String,
        sourceStartMs: Long,
        sourceDurationMs: Long,
        reactionDurationMs: Long,
        recordedSegments: List<SourcePlaybackSegment>,
        isSourceMuted: Boolean,
        splitRatio: SplitRatioPreset,
        framing: VideoFramingState,
        mirrorBottomCamera: Boolean,
        onProgress: (Float) -> Unit
    ): Result<CombinedExportOutput> {
        val outputFile = File(
            context.cacheDir,
            "react_combined_${System.currentTimeMillis()}.mp4"
        )
        val tempFiles = mutableListOf<File>()

        // Probe exact hardware media durations directly from both MP4 files so sync never drifts
        val (sourceProbe, reactionProbe) = withContext(Dispatchers.IO) {
            probeMedia(sourceVideoUri) to probeMedia(reactionVideoUri)
        }
        val actualSourceDurationMs = when {
            sourceProbe.durationMs > 0L -> sourceProbe.durationMs
            sourceDurationMs > 0L -> sourceDurationMs
            else -> 0L
        }
        val actualReactionDurationMs = when {
            reactionProbe.durationMs > 0L -> reactionProbe.durationMs
            reactionDurationMs > 0L -> reactionDurationMs
            else -> 2_000L
        }

        try {
            // Try exact recorded timeline (including pauses/seeks/loops) first; if an OEM codec rejects
            // mixed freeze-frame + video items, automatically retry with continuous looping video clips.
            val hasPausedSegments = recordedSegments.any { it.isPaused && it.durationMs >= 250L }
            var transformResult = runMedia3Composition(
                sourceVideoUri = sourceVideoUri,
                reactionVideoUri = reactionVideoUri,
                sourceStartMs = sourceStartMs.coerceAtLeast(0L),
                sourceDurationMs = actualSourceDurationMs,
                sourceHasAudio = sourceProbe.hasAudio && !isSourceMuted,
                reactionDurationMs = actualReactionDurationMs,
                recordedSegments = recordedSegments,
                allowFreezeFrames = hasPausedSegments,
                splitRatio = splitRatio,
                framing = framing,
                mirrorBottomCamera = mirrorBottomCamera,
                outputPath = outputFile.absolutePath,
                tempFiles = tempFiles,
                onProgress = onProgress
            )

            if (transformResult.isFailure && hasPausedSegments) {
                outputFile.delete()
                transformResult = runMedia3Composition(
                    sourceVideoUri = sourceVideoUri,
                    reactionVideoUri = reactionVideoUri,
                    sourceStartMs = sourceStartMs.coerceAtLeast(0L),
                    sourceDurationMs = actualSourceDurationMs,
                    sourceHasAudio = sourceProbe.hasAudio && !isSourceMuted,
                    reactionDurationMs = actualReactionDurationMs,
                    recordedSegments = recordedSegments.filterNot { it.isPaused },
                    allowFreezeFrames = false,
                    splitRatio = splitRatio,
                    framing = framing,
                    mirrorBottomCamera = mirrorBottomCamera,
                    outputPath = outputFile.absolutePath,
                    tempFiles = tempFiles,
                    onProgress = onProgress
                )
            }

            if (transformResult.isFailure) {
                outputFile.delete()
                return Result.failure(
                    transformResult.exceptionOrNull()
                        ?: IllegalStateException("Não foi possível combinar os vídeos.")
                )
            }

            return withContext(Dispatchers.IO) {
                runCatching {
                    val saved = copyFileToMediaStore(outputFile)
                    outputFile.delete()
                    saved
                }.onFailure {
                    outputFile.delete()
                }
            }
        } finally {
            tempFiles.forEach { runCatching { it.delete() } }
        }
    }

    private suspend fun runMedia3Composition(
        sourceVideoUri: String,
        reactionVideoUri: String,
        sourceStartMs: Long,
        sourceDurationMs: Long,
        sourceHasAudio: Boolean,
        reactionDurationMs: Long,
        recordedSegments: List<SourcePlaybackSegment>,
        allowFreezeFrames: Boolean,
        splitRatio: SplitRatioPreset,
        framing: VideoFramingState,
        mirrorBottomCamera: Boolean,
        outputPath: String,
        tempFiles: MutableList<File>,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        val outWidth = 720
        val outHeight = 1280
        val topWeight = splitRatio.topWeight.coerceIn(0.35f, 0.65f)
        val topHeight = ((outHeight * topWeight).toInt() / 2) * 2
        val bottomHeight = outHeight - topHeight

        val topItems = withContext(Dispatchers.IO) {
            buildTopSequenceItems(
                sourceVideoUri = sourceVideoUri,
                sourceStartMs = sourceStartMs,
                sourceDurationMs = sourceDurationMs,
                sourceHasAudio = sourceHasAudio,
                targetTotalDurationMs = reactionDurationMs,
                recordedSegments = recordedSegments,
                allowFreezeFrames = allowFreezeFrames,
                outWidth = outWidth,
                topHeight = topHeight,
                framing = framing,
                tempFiles = tempFiles
            )
        }

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val mainHandler = Handler(Looper.getMainLooper())

                val reactionItem = MediaItem.fromUri(reactionVideoUri)
                val bottomTransformation = UndistortedQuadrantTransformation(
                    outWidth = outWidth,
                    outHeight = bottomHeight,
                    fitInside = false,
                    userScale = 1f,
                    offsetXFraction = 0f,
                    offsetYFraction = 0f,
                    mirrorHorizontal = mirrorBottomCamera
                )
                val bottomEdited = EditedMediaItem.Builder(reactionItem)
                    .setEffects(Effects(emptyList(), listOf(bottomTransformation)))
                    .build()

                val topSequence = EditedMediaItemSequence(topItems)
                val bottomSequence = EditedMediaItemSequence(bottomEdited)

                // Each input is rendered at its exact quadrant pixel height (topHeight + bottomHeight == outHeight).
                // Scale (1f, 1f) aligns top [0..topHeight] and bottom [topHeight..outHeight] seamlessly.
                val compositorSettings = object : VideoCompositorSettings {
                    override fun getOutputSize(inputSizes: MutableList<Size>): Size {
                        return Size(outWidth, outHeight)
                    }

                    override fun getOverlaySettings(
                        inputId: Int,
                        presentationTimeUs: Long
                    ): OverlaySettings {
                        return when (inputId) {
                            0 -> OverlaySettings.Builder()
                                .setOverlayFrameAnchor(0f, 1f)
                                .setBackgroundFrameAnchor(0f, 1f)
                                .setScale(1f, 1f)
                                .build()
                            else -> OverlaySettings.Builder()
                                .setOverlayFrameAnchor(0f, -1f)
                                .setBackgroundFrameAnchor(0f, -1f)
                                .setScale(1f, 1f)
                                .build()
                        }
                    }
                }

                val composition = Composition.Builder(topSequence, bottomSequence)
                    .setVideoCompositorSettings(compositorSettings)
                    .build()

                val progressHolder = ProgressHolder()
                var progressRunnable: Runnable? = null

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressRunnable?.let { mainHandler.removeCallbacks(it) }
                            onProgress(1f)
                            if (continuation.isActive) {
                                continuation.resume(Result.success(Unit))
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            progressRunnable?.let { mainHandler.removeCallbacks(it) }
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(exportException))
                            }
                        }
                    })
                    .build()

                progressRunnable = object : Runnable {
                    override fun run() {
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                        }
                        mainHandler.postDelayed(this, 200L)
                    }
                }

                try {
                    transformer.start(composition, outputPath)
                    mainHandler.post(progressRunnable)
                } catch (e: Exception) {
                    progressRunnable.let { mainHandler.removeCallbacks(it) }
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }

                continuation.invokeOnCancellation {
                    progressRunnable.let { mainHandler.removeCallbacks(it) }
                    mainHandler.post { transformer.cancel() }
                }
            }
        }
    }

    /**
     * Builds a top-video EditedMediaItem list that ALWAYS covers the full [targetTotalDurationMs]
     * (matching the camera recording duration exactly) so the top video NEVER freezes halfway.
     */
    private fun buildTopSequenceItems(
        sourceVideoUri: String,
        sourceStartMs: Long,
        sourceDurationMs: Long,
        sourceHasAudio: Boolean,
        targetTotalDurationMs: Long,
        recordedSegments: List<SourcePlaybackSegment>,
        allowFreezeFrames: Boolean,
        outWidth: Int,
        topHeight: Int,
        framing: VideoFramingState,
        tempFiles: MutableList<File>
    ): List<EditedMediaItem> {
        val items = mutableListOf<EditedMediaItem>()
        var accumulatedMs = 0L

        fun makeTopTransform(): UndistortedQuadrantTransformation =
            UndistortedQuadrantTransformation(
                outWidth = outWidth,
                outHeight = topHeight,
                fitInside = framing.aspectPreset == VideoAspectPreset.FIT,
                userScale = framing.scale,
                offsetXFraction = framing.offsetXFraction,
                offsetYFraction = framing.offsetYFraction,
                mirrorHorizontal = false
            )

        fun appendClippedVideo(startMs: Long, endMs: Long) {
            val safeEnd = if (sourceDurationMs > 0L) {
                endMs.coerceAtMost(sourceDurationMs)
            } else {
                endMs
            }
            val safeStart = startMs.coerceIn(0L, (safeEnd - 150L).coerceAtLeast(0L))
            val clipDur = safeEnd - safeStart
            if (clipDur < 150L) return

            val mediaItem = MediaItem.Builder()
                .setUri(sourceVideoUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(safeStart)
                        .setEndPositionMs(safeEnd)
                        .build()
                )
                .build()

            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(!sourceHasAudio)
                .setEffects(Effects(emptyList(), listOf(makeTopTransform())))
                .build()

            items.add(edited)
            accumulatedMs += clipDur
        }

        fun appendFreezeFrame(framePosMs: Long, durMs: Long): Boolean {
            if (!allowFreezeFrames || durMs < 250L) return false
            val frameFile = extractFrameToTempJpeg(sourceVideoUri, framePosMs) ?: return false
            tempFiles.add(frameFile)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(frameFile))
            val edited = EditedMediaItem.Builder(mediaItem)
                .setDurationUs(durMs * 1_000L)
                .setFrameRate(30)
                .setEffects(Effects(emptyList(), listOf(makeTopTransform())))
                .build()
            items.add(edited)
            accumulatedMs += durMs
            return true
        }

        // 1. Follow exact recorded segments when available (captures seeks, loops, and pauses in sync!)
        if (recordedSegments.isNotEmpty()) {
            for (seg in recordedSegments) {
                val remaining = targetTotalDurationMs - accumulatedMs
                if (remaining < 150L) break
                val segDur = seg.durationMs.coerceAtMost(remaining)
                if (segDur < 150L) continue

                if (seg.isPaused) {
                    appendFreezeFrame(seg.sourceStartMs, segDur)
                } else {
                    val start = seg.sourceStartMs.coerceAtLeast(0L)
                    if (sourceDurationMs > 0L) {
                        var cursor = start % sourceDurationMs
                        var left = segDur
                        var guard = 0
                        while (left >= 150L && guard < 12) {
                            val chunkEnd = (cursor + left).coerceAtMost(sourceDurationMs)
                            val chunkDur = chunkEnd - cursor
                            if (chunkDur >= 150L) {
                                appendClippedVideo(cursor, chunkEnd)
                                left -= chunkDur
                            } else {
                                break
                            }
                            cursor = 0L
                            guard++
                        }
                    } else {
                        appendClippedVideo(start, start + segDur)
                    }
                }
            }
        }

        // 2. Ensure total top sequence duration covers the ENTIRE reaction duration (loop seamlessly if needed!)
        var cursorMs = if (items.isEmpty()) {
            if (sourceDurationMs > 0L) sourceStartMs % sourceDurationMs else sourceStartMs.coerceAtLeast(0L)
        } else {
            0L
        }
        var loopGuard = 0
        while (accumulatedMs < targetTotalDurationMs - 150L && loopGuard < 16) {
            val remaining = targetTotalDurationMs - accumulatedMs
            if (sourceDurationMs > 0L) {
                val available = sourceDurationMs - cursorMs
                if (available < 150L) {
                    cursorMs = 0L
                    loopGuard++
                    continue
                }
                val chunkDur = remaining.coerceAtMost(available)
                appendClippedVideo(cursorMs, cursorMs + chunkDur)
                cursorMs = 0L
            } else {
                appendClippedVideo(cursorMs, cursorMs + remaining)
                break
            }
            loopGuard++
        }

        // Safety fallback: guarantee at least one valid item
        if (items.isEmpty()) {
            val fallbackItem = MediaItem.fromUri(sourceVideoUri)
            items.add(
                EditedMediaItem.Builder(fallbackItem)
                    .setRemoveAudio(!sourceHasAudio)
                    .setEffects(Effects(emptyList(), listOf(makeTopTransform())))
                    .build()
            )
        }

        return items
    }

    private fun probeMedia(uriString: String): MediaProbeInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "http" || uri.scheme == "https") {
                retriever.setDataSource(uriString, emptyMap())
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                .equals("yes", ignoreCase = true)
            MediaProbeInfo(durationMs = durationMs, hasAudio = hasAudio)
        } catch (_: Exception) {
            MediaProbeInfo(durationMs = 0L, hasAudio = true)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractFrameToTempJpeg(sourceVideoUri: String, positionMs: Long): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(sourceVideoUri)
            if (uri.scheme == "http" || uri.scheme == "https") {
                retriever.setDataSource(sourceVideoUri, emptyMap())
            } else {
                retriever.setDataSource(context, uri)
            }
            val bitmap = retriever.getFrameAtTime(
                positionMs.coerceAtLeast(0L) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val outFile = File(context.cacheDir, "react_freeze_${System.nanoTime()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            bitmap.recycle()
            outFile
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun copyFileToMediaStore(sourceFile: File): CombinedExportOutput {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "ReactStudio_Combined_$stamp.mp4"
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ReactStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Falha ao criar arquivo na galeria.")

        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: throw IllegalStateException("Falha ao gravar arquivo combinado.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }

        return CombinedExportOutput(mediaStoreUri = uri, displayName = displayName)
    }
}

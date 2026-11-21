package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.SplitRatioPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class CombinedExportOutput(
    val mediaStoreUri: Uri,
    val displayName: String
)

@OptIn(UnstableApi::class)
class SplitScreenVideoExporter(private val context: Context) {

    suspend fun exportSplitScreen(
        sourceVideoUri: String,
        reactionVideoUri: String,
        sourceStartMs: Long,
        reactionDurationMs: Long,
        splitRatio: SplitRatioPreset,
        onProgress: (Float) -> Unit
    ): Result<CombinedExportOutput> {
        val outputFile = File(
            context.cacheDir,
            "react_combined_${System.currentTimeMillis()}.mp4"
        )

        val transformResult = runMedia3Composition(
            sourceVideoUri = sourceVideoUri,
            reactionVideoUri = reactionVideoUri,
            sourceStartMs = sourceStartMs.coerceAtLeast(0L),
            reactionDurationMs = reactionDurationMs.coerceAtLeast(1500L),
            splitRatio = splitRatio,
            outputPath = outputFile.absolutePath,
            onProgress = onProgress
        )

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
    }

    private suspend fun runMedia3Composition(
        sourceVideoUri: String,
        reactionVideoUri: String,
        sourceStartMs: Long,
        reactionDurationMs: Long,
        splitRatio: SplitRatioPreset,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            val outWidth = 720
            val outHeight = 1280
            val topWeight = splitRatio.topWeight.coerceIn(0.35f, 0.65f)
            val bottomWeight = 1f - topWeight

            val topHeight = ((outHeight * topWeight).toInt() / 2) * 2
            val bottomHeight = outHeight - topHeight

            // Clip top source video to match the recorded reaction segment
            val clippedTopItem = MediaItem.Builder()
                .setUri(sourceVideoUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(sourceStartMs)
                        .setEndPositionMs(sourceStartMs + reactionDurationMs)
                        .build()
                )
                .build()

            val reactionItem = MediaItem.fromUri(reactionVideoUri)

            val topEdited = EditedMediaItem.Builder(clippedTopItem)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            Presentation.createForWidthAndHeight(
                                outWidth,
                                topHeight,
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                            )
                        )
                    )
                )
                .build()

            val bottomEdited = EditedMediaItem.Builder(reactionItem)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            Presentation.createForWidthAndHeight(
                                outWidth,
                                bottomHeight,
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                            )
                        )
                    )
                )
                .build()

            val compositorSettings = object : VideoCompositorSettings {
                override fun getOutputSize(inputSizes: MutableList<Size>): Size {
                    return Size(outWidth, outHeight)
                }

                override fun getOverlaySettings(
                    inputId: Int,
                    presentationTimeUs: Long
                ): OverlaySettings {
                    return when (inputId) {
                        // Top source video anchored to top edge (NDC y = +1.0)
                        0 -> OverlaySettings.Builder()
                            .setOverlayFrameAnchor(0f, 1f)
                            .setBackgroundFrameAnchor(0f, 1f)
                            .setScale(1f, topWeight)
                            .build()
                        // Bottom reaction camera anchored to bottom edge (NDC y = -1.0)
                        else -> OverlaySettings.Builder()
                            .setOverlayFrameAnchor(0f, -1f)
                            .setBackgroundFrameAnchor(0f, -1f)
                            .setScale(1f, bottomWeight)
                            .build()
                    }
                }
            }

            val composition = Composition.Builder(
                EditedMediaItemSequence(topEdited),
                EditedMediaItemSequence(bottomEdited)
            )
                .setVideoCompositorSettings(compositorSettings)
                .build()

            val progressHolder = ProgressHolder()
            var progressRunnable: Runnable? = null

            val transformer = Transformer.Builder(context)
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

package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.media.CamcorderProfile
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.media.SelfieCutoutProcessor
import com.example.model.StudioUiState
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Stable
class CameraStudioController(
    val previewView: PreviewView,
    val cutoutBitmap: ImageBitmap?,
    val onToggleRecording: () -> Unit
) {
    /**
     * Detaches the shared [PreviewView] from any prior parent before attaching it to a new
     * AndroidView host (e.g. when switching between Split-Screen and Full-Screen 9:16 PiP).
     */
    fun obtainDetachedPreviewView(): PreviewView {
        (previewView.parent as? ViewGroup)?.removeView(previewView)
        return previewView
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun rememberCameraStudioController(
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    isFrontCamera: Boolean,
    isCameraMirrored: Boolean,
    enableCutout: Boolean,
    onRequestPermissions: () -> Unit,
    onBeforeRecordingStart: () -> Unit,
    onRecordingStarted: () -> Unit,
    onRecordingStatus: (Long, Double) -> Unit,
    onRecordingFinalized: (Uri?, String?) -> Unit
): CameraStudioController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sharedPreviewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isSessionMarkerRecording by remember { mutableStateOf(false) }
    var cutoutBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val latestFrontCamera by rememberUpdatedState(isFrontCamera)
    val latestMirrored by rememberUpdatedState(isCameraMirrored)
    val latestEnableCutout by rememberUpdatedState(enableCutout)
    val latestHasAudio by rememberUpdatedState(hasAudioPermission)
    val latestBeforeStart by rememberUpdatedState(onBeforeRecordingStart)
    val latestStarted by rememberUpdatedState(onRecordingStarted)
    val latestStatus by rememberUpdatedState(onRecordingStatus)
    val latestFinalized by rememberUpdatedState(onRecordingFinalized)

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val isDisposed = remember { AtomicBoolean(false) }
    var cutoutProcessor by remember { mutableStateOf<SelfieCutoutProcessor?>(null) }

    // Create/release ML Kit SelfieCutoutProcessor only when Cutout mode is active
    LaunchedEffect(enableCutout) {
        if (!enableCutout) {
            cutoutBitmap = null
            cutoutProcessor?.close()
            cutoutProcessor = null
        } else if (cutoutProcessor == null) {
            cutoutProcessor = SelfieCutoutProcessor(streamMode = true)
        }
    }

    // Bind CameraX Preview + VideoCapture + (when in Cutout mode) ML Kit ImageAnalysis
    LaunchedEffect(hasCameraPermission, isFrontCamera, enableCutout) {
        if (!hasCameraPermission) return@LaunchedEffect

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (isDisposed.get()) return@addListener
                runCatching {
                    val cameraProvider = providerFuture.get()
                    val preferredSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    val fallbackSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    val selector = when {
                        cameraProvider.hasCamera(preferredSelector) -> preferredSelector
                        cameraProvider.hasCamera(fallbackSelector) -> fallbackSelector
                        else -> return@runCatching
                    }

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = sharedPreviewView.surfaceProvider
                    }

                    val imageAnalysis = if (enableCutout) {
                        val resSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(480, 640),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                        ImageAnalysis.Builder()
                            .setResolutionSelector(resSelector)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .apply {
                                setAnalyzer(analysisExecutor) { imageProxy ->
                                    try {
                                        if (!latestEnableCutout || isDisposed.get()) return@setAnalyzer
                                        val proc = cutoutProcessor ?: return@setAnalyzer
                                        val bmp = proc.processLiveFrame(
                                            imageProxy = imageProxy,
                                            isFrontCamera = latestFrontCamera,
                                            isMirrored = latestMirrored
                                        )
                                        if (bmp != null && !isDisposed.get()) {
                                            val imgBmp = bmp.asImageBitmap()
                                            mainHandler.post {
                                                if (latestEnableCutout && !isDisposed.get()) {
                                                    cutoutBitmap = imgBmp
                                                }
                                            }
                                        }
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }
                    } else {
                        null
                    }

                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview
                    )

                    val cameraId = runCatching {
                        Camera2CameraInfo.from(boundCamera.cameraInfo).cameraId
                    }.getOrNull()

                    val supportsVideo = cameraId != null && hasSupportedVideoEncoderProfiles(cameraId)
                    if (supportsVideo) {
                        val qualitySelector = QualitySelector.fromOrderedList(
                            listOf(Quality.HD, Quality.SD, Quality.LOWEST),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                        val recorder = Recorder.Builder()
                            .setQualitySelector(qualitySelector)
                            .build()
                        val capture = VideoCapture.withOutput(recorder)

                        if (imageAnalysis != null) {
                            // Try 3 simultaneous use cases: Preview + VideoCapture + ImageAnalysis
                            val boundAll = runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    selector,
                                    preview,
                                    capture,
                                    imageAnalysis
                                )
                                videoCapture = capture
                            }
                            if (boundAll.isFailure) {
                                // Fallback for LEGACY/LIMITED camera hardware: lower SD quality + Analysis,
                                // or VideoCapture alone so recording never breaks
                                val fallbackSd = runCatching {
                                    val sdRecorder = Recorder.Builder()
                                        .setQualitySelector(
                                            QualitySelector.from(
                                                Quality.SD,
                                                FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST)
                                            )
                                        )
                                        .build()
                                    val sdCapture = VideoCapture.withOutput(sdRecorder)
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        sdCapture,
                                        imageAnalysis
                                    )
                                    videoCapture = sdCapture
                                }
                                if (fallbackSd.isFailure) {
                                    runCatching {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            selector,
                                            preview,
                                            capture
                                        )
                                        videoCapture = capture
                                    }.onFailure {
                                        videoCapture = null
                                    }
                                }
                            }
                        } else {
                            runCatching {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    selector,
                                    capture
                                )
                                videoCapture = capture
                            }.onFailure {
                                videoCapture = null
                            }
                        }
                    } else {
                        videoCapture = null
                        if (imageAnalysis != null) {
                            runCatching {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    selector,
                                    imageAnalysis
                                )
                            }
                        }
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            activeRecording?.stop()
            activeRecording = null
            isSessionMarkerRecording = false
            cutoutProcessor?.close()
            cutoutProcessor = null
            analysisExecutor.shutdownNow()
        }
    }

    val toggleRecording = remember(context) {
        {
            if (!hasCameraPermission) {
                onRequestPermissions()
            } else {
                val currentRec = activeRecording
                if (currentRec != null) {
                    currentRec.stop()
                    activeRecording = null
                } else if (isSessionMarkerRecording) {
                    isSessionMarkerRecording = false
                    latestFinalized(null, "Sessão de reação finalizada")
                } else {
                    val capture = videoCapture
                    if (capture == null) {
                        isSessionMarkerRecording = true
                        latestStarted()
                    } else {
                        latestBeforeStart()
                        val outputOptions = buildMediaStoreOutputOptions(context)
                        var pending = capture.output.prepareRecording(context, outputOptions)
                        if (latestHasAudio) {
                            pending = pending.withAudioEnabled()
                        }
                        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> latestStarted()
                                is VideoRecordEvent.Status -> {
                                    val recMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                                    val amp = event.recordingStats.audioStats.audioAmplitude
                                    latestStatus(recMs, amp)
                                }
                                is VideoRecordEvent.Finalize -> {
                                    activeRecording = null
                                    if (event.hasError()) {
                                        latestFinalized(null, "Erro ao finalizar gravação (${event.error})")
                                    } else {
                                        latestFinalized(event.outputResults.outputUri, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return remember(sharedPreviewView, cutoutBitmap, toggleRecording) {
        CameraStudioController(
            previewView = sharedPreviewView,
            cutoutBitmap = cutoutBitmap,
            onToggleRecording = toggleRecording
        )
    }
}

@Composable
fun StudioControlDock(
    uiState: StudioUiState,
    hasCameraPermission: Boolean,
    onOpenAudioPanel: () -> Unit,
    onToggleManualReact: () -> Unit,
    onRecordClick: () -> Unit,
    onOpenTakesPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xD9080A10), Color(0xF5080A10))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StudioDockButton(
            icon = Icons.Rounded.Tune,
            label = stringResource(R.string.studio_audio_panel),
            accentColor = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber,
            isActive = uiState.audioSettings.headphoneMonitoringEnabled,
            onClick = onOpenAudioPanel,
            testTag = "dock_audio_button"
        )

        StudioDockButton(
            icon = Icons.Rounded.RecordVoiceOver,
            label = stringResource(R.string.studio_react_now),
            accentColor = CoralCrimson,
            isActive = uiState.isManualReacting,
            onClick = onToggleManualReact,
            testTag = "dock_react_button"
        )

        RecordShutterButton(
            isRecording = uiState.isRecording,
            enabled = hasCameraPermission,
            onClick = onRecordClick
        )

        StudioDockButton(
            icon = Icons.Rounded.GraphicEq,
            label = if (uiState.audioSettings.autoReactEnabled) "Auto ON" else "Auto OFF",
            accentColor = ElectricCyan,
            isActive = uiState.audioSettings.autoReactEnabled,
            onClick = onOpenAudioPanel,
            testTag = "dock_autoreact_button"
        )

        StudioDockButton(
            icon = Icons.Rounded.Movie,
            label = if (uiState.recordedTakes.isNotEmpty()) {
                "Takes (${uiState.recordedTakes.size})"
            } else {
                stringResource(R.string.studio_takes_panel)
            },
            accentColor = MaterialTheme.colorScheme.onSurface,
            isActive = uiState.reactionMarkers.isNotEmpty(),
            onClick = onOpenTakesPanel,
            testTag = "dock_takes_button"
        )
    }
}

@Composable
private fun StudioDockButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.24f) else Color(0xB3171D2B),
        label = "dock_bg"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = if (isActive) accentColor else Color(0x44FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun RecordShutterButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 26.dp else 46.dp,
        animationSpec = spring(),
        label = "shutter_size"
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 24.dp,
        animationSpec = spring(),
        label = "shutter_corner"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(
                width = 3.dp,
                color = if (isRecording) CoralCrimson else Color.White.copy(alpha = if (enabled) 0.9f else 0.4f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .testTag("record_shutter_button"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(if (enabled) CoralCrimson else CoralCrimson.copy(alpha = 0.45f))
        )
    }
}

@Composable
fun CameraPermissionPrompt(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A2236), StudioVoid)
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(StudioSurfaceElevated)
                .border(1.dp, CoralCrimson.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = null,
                tint = CoralCrimson,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.studio_camera_permission_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.studio_camera_permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(
                containerColor = CoralCrimson,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier.testTag("grant_camera_audio_button")
        ) {
            Text(
                text = stringResource(R.string.studio_grant_permissions),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun buildMediaStoreOutputOptions(context: Context): MediaStoreOutputOptions {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "ReactStudio_$stamp.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ReactStudio")
        }
    }
    return MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()
}

@Suppress("DEPRECATION")
private fun hasSupportedVideoEncoderProfiles(cameraId: String): Boolean {
    return runCatching {
        val qualities = intArrayOf(
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_LOW
        )
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            qualities.any { quality ->
                val profiles = CamcorderProfile.getAll(cameraId, quality)
                val videoProfiles = profiles?.videoProfiles.orEmpty()
                videoProfiles.any { vp ->
                    val mime = vp.mediaType
                    encoders.any { codec ->
                        codec.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                            runCatching {
                                codec.getCapabilitiesForType(mime)
                                    .videoCapabilities
                                    ?.isSizeSupported(vp.width, vp.height) == true
                            }.getOrDefault(false)
                    }
                }
            }
        } else {
            val numericId = cameraId.toIntOrNull() ?: return@runCatching false
            qualities.any { quality -> CamcorderProfile.hasProfile(numericId, quality) }
        }
    }.getOrDefault(false)
}

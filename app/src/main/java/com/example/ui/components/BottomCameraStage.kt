package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.media.CamcorderProfile
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.StudioUiState
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun BottomCameraStage(
    uiState: StudioUiState,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onToggleCameraLens: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleManualReact: () -> Unit,
    onOpenAudioPanel: () -> Unit,
    onOpenTakesPanel: () -> Unit,
    onRecordingStarted: () -> Unit,
    onRecordingFinalized: (Uri?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isSessionMarkerRecording by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val pulseTransition = rememberInfiniteTransition(label = "reaction_halo")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (uiState.isReactingNow) 3.dp else 1.dp,
        animationSpec = spring(),
        label = "camera_border_width"
    )

    val borderBrush = if (uiState.isReactingNow) {
        Brush.linearGradient(
            colors = listOf(
                CoralCrimson.copy(alpha = pulseAlpha),
                ElectricCyan.copy(alpha = pulseAlpha)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0x552E3A54), Color(0x552E3A54))
        )
    }

    // Bind CameraX Preview first; only bind VideoCapture if camera exposes valid EncoderProfiles
    LaunchedEffect(hasCameraPermission, uiState.isFrontCamera, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val cameraProvider = providerFuture.get()
                    val preferredSelector = if (uiState.isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    val fallbackSelector = if (uiState.isFrontCamera) {
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
                        it.surfaceProvider = pv.surfaceProvider
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

                    if (cameraId != null && hasSupportedVideoEncoderProfiles(cameraId)) {
                        val qualitySelector = QualitySelector.fromOrderedList(
                            listOf(Quality.HD, Quality.SD, Quality.LOWEST),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                        val recorder = Recorder.Builder()
                            .setQualitySelector(qualitySelector)
                            .build()
                        val capture = VideoCapture.withOutput(recorder)
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
                    } else {
                        videoCapture = null
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            isSessionMarkerRecording = false
        }
    }

    fun toggleRecording() {
        val currentRec = activeRecording
        if (currentRec != null) {
            currentRec.stop()
            activeRecording = null
            return
        }

        if (isSessionMarkerRecording) {
            isSessionMarkerRecording = false
            onRecordingFinalized(null, "Sessão de reação finalizada")
            return
        }

        val capture = videoCapture
        if (capture == null) {
            // Camera preview works, but hardware has no video EncoderProfiles (e.g., virtual camera)
            isSessionMarkerRecording = true
            onRecordingStarted()
            return
        }

        val outputOptions = buildMediaStoreOutputOptions(context)
        var pending = capture.output.prepareRecording(context, outputOptions)
        if (hasAudioPermission) {
            pending = pending.withAudioEnabled()
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onRecordingStarted()
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (event.hasError()) {
                        onRecordingFinalized(null, "Erro ao finalizar gravação (${event.error})")
                    } else {
                        onRecordingFinalized(event.outputResults.outputUri, null)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioVoid)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(borderWidth, borderBrush, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        if (!hasCameraPermission) {
            CameraPermissionPrompt(onRequestPermissions = onRequestPermissions)
        } else {
            // Live CameraX PreviewView
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = this
                    }
                },
                update = { pv -> previewView = pv },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // CameraX front preview is mirrored by default; invert if user disables mirror
                        scaleX = if (uiState.isFrontCamera && !uiState.isCameraMirrored) -1f else 1f
                    }
            )
        }

        // Top HUD inside Camera Stage: Reaction / Voice Status + Live Audio Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xB8080A10), Color.Transparent)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(
                    visible = uiState.isReactingNow,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    StudioStatusPill(
                        text = if (uiState.isSpeaking) {
                            "${stringResource(R.string.studio_reacting_badge)} · VOZ"
                        } else {
                            stringResource(R.string.studio_reacting_badge)
                        },
                        dotColor = CoralCrimson,
                        containerColor = Color(0xD942101C),
                        borderColor = CoralCrimson
                    )
                }

                if (uiState.isRecording) {
                    StudioStatusPill(
                        text = "REC ${formatTimecode(uiState.recordingElapsedMs)}",
                        dotColor = CoralCrimson,
                        containerColor = Color(0xD910141E),
                        borderColor = CoralCrimson
                    )
                } else if (!uiState.isReactingNow) {
                    StudioStatusPill(
                        text = "CÂMERA · REACT",
                        dotColor = ElectricCyan
                    )
                }
            }

            // Live Mini VU + Headphone Badge (taps straight to Mesa de Áudio!)
            Surface(
                onClick = onOpenAudioPanel,
                shape = RoundedCornerShape(50),
                color = Color(0xCC10141E),
                modifier = Modifier
                    .border(
                        1.dp,
                        if (uiState.hasHeadphonesConnected) {
                            ElectricCyan.copy(alpha = 0.5f)
                        } else {
                            SignalAmber.copy(alpha = 0.5f)
                        },
                        RoundedCornerShape(50)
                    )
                    .testTag("camera_audio_hud_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniMicBars(
                        levels = uiState.waveformBars,
                        isSpeaking = uiState.isSpeaking,
                        modifier = Modifier.width(34.dp)
                    )
                    Icon(
                        imageVector = if (uiState.hasHeadphonesConnected) {
                            Icons.Rounded.Headphones
                        } else {
                            Icons.Rounded.VolumeUp
                        },
                        contentDescription = stringResource(R.string.cd_audio_settings),
                        tint = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Camera Utility Floating Buttons (Mirror & Flip on Right Edge)
        if (hasCameraPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onToggleCameraLens,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(Color(0xB310141E))
                        .testTag("flip_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = stringResource(R.string.cd_flip_camera),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onToggleMirror,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (uiState.isCameraMirrored) Color(0xB3073630) else Color(0xB310141E)
                        )
                        .testTag("mirror_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flip,
                        contentDescription = stringResource(R.string.cd_mirror_camera),
                        tint = if (uiState.isCameraMirrored) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Studio Control Dock (Safe Navigation Bar Insets!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xD9080A10), Color(0xF5080A10))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. Audio Console Button
            StudioDockButton(
                icon = Icons.Rounded.Tune,
                label = stringResource(R.string.studio_audio_panel),
                accentColor = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber,
                isActive = uiState.audioSettings.headphoneMonitoringEnabled,
                onClick = onOpenAudioPanel,
                testTag = "dock_audio_button"
            )

            // 2. Manual Instant React Toggle Button
            StudioDockButton(
                icon = Icons.Rounded.RecordVoiceOver,
                label = stringResource(R.string.studio_react_now),
                accentColor = CoralCrimson,
                isActive = uiState.isManualReacting,
                onClick = onToggleManualReact,
                testTag = "dock_react_button"
            )

            // 3. Center Record / Stop Shutter Button
            RecordShutterButton(
                isRecording = uiState.isRecording,
                enabled = hasCameraPermission,
                onClick = {
                    if (hasCameraPermission) toggleRecording() else onRequestPermissions()
                }
            )

            // 4. Voice Auto-React Indicator / Quick Audio Mic Tab
            StudioDockButton(
                icon = Icons.Rounded.GraphicEq,
                label = if (uiState.audioSettings.autoReactEnabled) "Auto ON" else "Auto OFF",
                accentColor = ElectricCyan,
                isActive = uiState.audioSettings.autoReactEnabled,
                onClick = onOpenAudioPanel,
                testTag = "dock_autoreact_button"
            )

            // 5. Takes & Reaction Markers History
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
}

@Composable
private fun StudioDockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun CameraPermissionPrompt(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
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


package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.model.ActiveSheet
import com.example.model.AudioPanelTab
import com.example.model.StudioUiState
import com.example.model.VideoSourceType
import com.example.ui.components.AppUpdateModalSheet
import com.example.ui.components.AudioConsoleContent
import com.example.ui.components.AudioConsoleModalSheet
import com.example.ui.components.BottomCameraStage
import com.example.ui.components.ExportShareModalSheet
import com.example.ui.components.TakesHistoryModalSheet
import com.example.ui.components.TopVideoStage
import com.example.ui.components.VideoUrlDialog
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioOutlineSubtle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.viewmodel.PlayerCommand
import com.example.viewmodel.ReactStudioViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun ReactStudioScreen(
    viewModel: ReactStudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) {
            viewModel.startAudioMonitoring()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadSourceVideo(uri)
        }
    }

    fun launchVideoPicker() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    fun requestStudioPermissions() {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val rot = videoSize.unappliedRotationDegrees
                    val effW = if (rot == 90 || rot == 270) videoSize.height else videoSize.width
                    val effH = if (rot == 90 || rot == 270) videoSize.width else videoSize.height
                    val displayW = (effW * videoSize.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
                    viewModel.onVideoSizeChanged(displayW, effH)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onPlaybackStateUpdated(
                    isPlaying = isPlaying,
                    isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                viewModel.onPlaybackStateUpdated(
                    isPlaying = exoPlayer.isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                viewModel.attachExoPlayerAudioSession(audioSessionId)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, hasAudioPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (hasAudioPermission) viewModel.startAudioMonitoring()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopAudioMonitoring()
                    if (exoPlayer.isPlaying) exoPlayer.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (hasAudioPermission) {
            viewModel.startAudioMonitoring()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopAudioMonitoring()
        }
    }

    // Load MediaItem into ExoPlayer only when not using Instagram Embed WebView
    LaunchedEffect(uiState.sourceVideoUri, uiState.sourceVideoType) {
        val uriString = uiState.sourceVideoUri
        if (uriString != null && uiState.sourceVideoType != VideoSourceType.INSTAGRAM_EMBED) {
            exoPlayer.setMediaItem(MediaItem.fromUri(uriString))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            if (exoPlayer.audioSessionId != 0) {
                viewModel.attachExoPlayerAudioSession(exoPlayer.audioSessionId)
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    val animatedVideoVolume by animateFloatAsState(
        targetValue = uiState.effectiveVideoVolume,
        animationSpec = spring(stiffness = 280f),
        label = "exo_duck_volume"
    )
    LaunchedEffect(animatedVideoVolume) {
        exoPlayer.volume = animatedVideoVolume.coerceIn(0f, 1f)
    }

    LaunchedEffect(exoPlayer) {
        viewModel.playerCommands.collect { cmd ->
            when (cmd) {
                PlayerCommand.Play -> exoPlayer.play()
                PlayerCommand.Pause -> exoPlayer.pause()
                is PlayerCommand.SeekBy -> {
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    val target = (exoPlayer.currentPosition + cmd.deltaMs).coerceIn(0L, duration)
                    exoPlayer.seekTo(target)
                    viewModel.onPlaybackStateUpdated(
                        isPlaying = exoPlayer.isPlaying,
                        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                        positionMs = target,
                        durationMs = duration
                    )
                }
                is PlayerCommand.SeekTo -> {
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    val target = cmd.positionMs.coerceIn(0L, duration)
                    exoPlayer.seekTo(target)
                    viewModel.onPlaybackStateUpdated(
                        isPlaying = exoPlayer.isPlaying,
                        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                        positionMs = target,
                        durationMs = duration
                    )
                }
                is PlayerCommand.SetSpeed -> {
                    exoPlayer.setPlaybackSpeed(cmd.speed)
                }
            }
        }
    }

    LaunchedEffect(uiState.sourceVideoUri, uiState.sourceVideoType, uiState.isPlaying) {
        while (uiState.sourceVideoUri != null && uiState.sourceVideoType != VideoSourceType.INSTAGRAM_EMBED) {
            viewModel.onPlaybackStateUpdated(
                isPlaying = exoPlayer.isPlaying,
                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                positionMs = exoPlayer.currentPosition,
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            )
            delay(200L)
        }
    }

    LaunchedEffect(uiState.statusToast) {
        val msg = uiState.statusToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusToast()
    }

    BackHandler(enabled = uiState.isFramingMode || uiState.activeSheet != ActiveSheet.NONE) {
        if (uiState.activeSheet != ActiveSheet.NONE) {
            viewModel.closeSheet()
        } else if (uiState.isFramingMode) {
            viewModel.setFramingMode(false)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioVoid)
    ) {
        val isWideScreen = maxWidth >= 760.dp

        val topWeight by animateFloatAsState(
            targetValue = uiState.splitRatio.topWeight,
            animationSpec = spring(stiffness = 380f),
            label = "split_weight"
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                TopVideoStage(
                    uiState = uiState,
                    exoPlayer = exoPlayer,
                    onPickVideo = ::launchVideoPicker,
                    onOpenUrlDialog = { viewModel.openSheet(ActiveSheet.VIDEO_URL_DIALOG) },
                    onOpenAppUpdates = { viewModel.openSheet(ActiveSheet.APP_UPDATES) },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onRewind = { viewModel.seekBy(-5_000L) },
                    onForward = { viewModel.seekBy(5_000L) },
                    onSeekTo = viewModel::seekTo,
                    onCycleSpeed = viewModel::cyclePlaybackSpeed,
                    onToggleMute = viewModel::toggleVideoMute,
                    onToggleFramingMode = viewModel::setFramingMode,
                    onFramingGesture = viewModel::updateFramingTransform,
                    onFramingScaleChange = viewModel::setFramingScale,
                    onSelectAspectPreset = viewModel::setAspectPreset,
                    onToggleGrid = viewModel::toggleRuleOfThirds,
                    onResetFraming = viewModel::resetFraming,
                    modifier = Modifier.weight(topWeight)
                )

                StudioSplitDividerBar(
                    uiState = uiState,
                    showAudioShortcut = !isWideScreen,
                    onCycleSplit = viewModel::cycleSplitRatio,
                    onMarkReaction = { viewModel.addReactionMarker(isAuto = false) },
                    onOpenAudioHeadphones = {
                        viewModel.openSheet(ActiveSheet.AUDIO_CONSOLE, AudioPanelTab.HEADPHONES)
                    }
                )

                BottomCameraStage(
                    uiState = uiState,
                    hasCameraPermission = hasCameraPermission,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermissions = ::requestStudioPermissions,
                    onToggleCameraLens = viewModel::toggleCameraLens,
                    onToggleMirror = viewModel::toggleCameraMirror,
                    onToggleManualReact = viewModel::toggleManualReaction,
                    onOpenAudioPanel = {
                        if (!isWideScreen) {
                            viewModel.openSheet(ActiveSheet.AUDIO_CONSOLE)
                        }
                    },
                    onOpenTakesPanel = { viewModel.openSheet(ActiveSheet.TAKES_HISTORY) },
                    onBeforeRecordingStart = viewModel::prepareForCameraRecording,
                    onRecordingStarted = {
                        val hasExoVideo = uiState.sourceVideoUri != null &&
                            uiState.sourceVideoType != VideoSourceType.INSTAGRAM_EMBED
                        if (hasExoVideo) {
                            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                                exoPlayer.prepare()
                            } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
                            }
                            if (!exoPlayer.isPlaying) {
                                exoPlayer.play()
                            }
                        }
                        viewModel.onRecordingStarted(
                            exactSourcePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            isSourcePlaying = if (hasExoVideo) true else exoPlayer.isPlaying
                        )
                    },
                    onRecordingStatus = { recordedMs, amplitude ->
                        val activePlayback = exoPlayer.isPlaying ||
                            (exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_BUFFERING)
                        viewModel.onRecordingStatusTick(
                            recordedDurationMs = recordedMs,
                            audioAmplitude = amplitude,
                            liveSourcePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            isSourcePlaying = activePlayback
                        )
                    },
                    onRecordingFinalized = { uri, err ->
                        viewModel.onRecordingFinalized(
                            savedUri = uri,
                            errorMessage = err,
                            resumeAudioMonitor = hasAudioPermission
                        )
                    },
                    modifier = Modifier.weight(1f - topWeight)
                )
            }

            if (isWideScreen) {
                Box(
                    modifier = Modifier
                        .width(370.dp)
                        .fillMaxHeight()
                        .background(StudioSurface)
                        .border(1.dp, StudioOutlineSubtle)
                        .statusBarsPadding()
                ) {
                    AudioConsoleContent(
                        uiState = uiState,
                        hasAudioPermission = hasAudioPermission,
                        onRequestAudioPermission = ::requestStudioPermissions,
                        onClose = {},
                        showCloseButton = false,
                        onSelectTab = viewModel::selectAudioTab,
                        onUpdateSettings = viewModel::updateAudioSettings,
                        onSelectInputDevice = viewModel::selectInputDevice,
                        onSelectOutputDevice = viewModel::selectOutputDevice,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 54.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = StudioSurfaceElevated,
                contentColor = ElectricCyan,
                shape = RoundedCornerShape(14.dp)
            )
        }

        if (!isWideScreen && uiState.activeSheet == ActiveSheet.AUDIO_CONSOLE) {
            AudioConsoleModalSheet(
                uiState = uiState,
                hasAudioPermission = hasAudioPermission,
                onRequestAudioPermission = ::requestStudioPermissions,
                onDismiss = viewModel::closeSheet,
                onSelectTab = viewModel::selectAudioTab,
                onUpdateSettings = viewModel::updateAudioSettings,
                onSelectInputDevice = viewModel::selectInputDevice,
                onSelectOutputDevice = viewModel::selectOutputDevice
            )
        }

        if (uiState.activeSheet == ActiveSheet.TAKES_HISTORY) {
            TakesHistoryModalSheet(
                uiState = uiState,
                onJumpToMarker = { pos -> viewModel.seekTo(pos) },
                onOpenExportShare = viewModel::openExportShareForTake,
                onDismiss = viewModel::closeSheet
            )
        }

        if (uiState.activeSheet == ActiveSheet.EXPORT_SHARE) {
            ExportShareModalSheet(
                uiState = uiState,
                directShareLink = viewModel.buildDirectShareLink(),
                onCombineWithSource = viewModel::combineWithMainVideo,
                onShowMessage = viewModel::showStatusToast,
                onDismiss = viewModel::closeSheet
            )
        }

        if (uiState.activeSheet == ActiveSheet.APP_UPDATES) {
            AppUpdateModalSheet(
                uiState = uiState,
                onSaveRepoAndCheck = viewModel::saveGithubRepoAndCheck,
                onShowMessage = viewModel::showStatusToast,
                onDismiss = viewModel::closeSheet
            )
        }

        if (uiState.activeSheet == ActiveSheet.VIDEO_URL_DIALOG) {
            VideoUrlDialog(
                isResolving = uiState.isResolvingLink,
                onDismiss = viewModel::closeSheet,
                onLoadUrl = viewModel::loadSourceVideoUrl
            )
        }
    }
}

@Composable
private fun StudioSplitDividerBar(
    uiState: StudioUiState,
    showAudioShortcut: Boolean,
    onCycleSplit: () -> Unit,
    onMarkReaction: () -> Unit,
    onOpenAudioHeadphones: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(StudioSurface)
            .border(1.dp, StudioOutlineSubtle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCycleSplit)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("split_ratio_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Splitscreen,
                contentDescription = stringResource(R.string.cd_split_ratio),
                tint = ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = uiState.splitRatio.label,
                style = StudioMonoStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (uiState.sourceVideoUri != null) {
            Surface(
                onClick = onMarkReaction,
                shape = RoundedCornerShape(50),
                color = StudioSurfaceElevated,
                modifier = Modifier.testTag("mark_reaction_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = stringResource(R.string.cd_mark_reaction),
                        tint = CoralCrimson,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (uiState.reactionMarkers.isEmpty()) {
                            "Marcar"
                        } else {
                            "Marcar (${uiState.reactionMarkers.size})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(StudioOutlineSubtle)
            )
        }

        if (showAudioShortcut) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenAudioHeadphones)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("divider_audio_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (uiState.hasHeadphonesConnected) Icons.Rounded.Headphones else Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_audio_settings),
                    tint = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (uiState.hasHeadphonesConnected) "Fone ON" else "Áudio",
                    style = StudioMonoStyle,
                    color = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber
                )
            }
        } else {
            Text(
                text = if (uiState.audioSettings.autoReactEnabled) "AUTO-REACT ON" else "MANUAL",
                style = StudioMonoStyle,
                color = if (uiState.audioSettings.autoReactEnabled) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.example.ui.components

import android.annotation.SuppressLint
import android.view.TextureView
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.media.InstagramLinkResolver
import com.example.model.StudioUiState
import com.example.model.VideoAspectPreset
import com.example.model.VideoSourceType
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioVoid
import kotlin.math.min

@Composable
fun TopVideoStage(
    uiState: StudioUiState,
    exoPlayer: ExoPlayer,
    onPickVideo: () -> Unit,
    onOpenUrlDialog: () -> Unit,
    onOpenAppUpdates: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFramingMode: (Boolean) -> Unit,
    onFramingGesture: (Float, Float, Float) -> Unit,
    onFramingScaleChange: (Float) -> Unit,
    onSelectAspectPreset: (VideoAspectPreset) -> Unit,
    onToggleGrid: () -> Unit,
    onResetFraming: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInstagramEmbed = uiState.sourceVideoType == VideoSourceType.INSTAGRAM_EMBED

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioVoid)
            .clipToBounds()
    ) {
        if (uiState.sourceVideoUri == null) {
            EmptyVideoStage(
                hasUpdateAvailable = uiState.hasUpdateAvailable,
                onPickVideo = onPickVideo,
                onOpenUrlDialog = onOpenUrlDialog,
                onOpenAppUpdates = onOpenAppUpdates
            )
        } else {
            ActiveVideoViewport(
                uiState = uiState,
                exoPlayer = exoPlayer,
                onTapVideo = {
                    if (!uiState.isFramingMode && !isInstagramEmbed) onTogglePlayPause()
                },
                onFramingGesture = onFramingGesture
            )

            // Top Scrim + Video Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xD9080A10), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color(0xB310141E),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clickable(onClick = onPickVideo)
                                .testTag("change_video_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = stringResource(R.string.studio_change_video),
                                    tint = if (isInstagramEmbed) CoralCrimson else ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = uiState.sourceVideoTitle.ifBlank { "Vídeo" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (uiState.isAutoDuckingActive) {
                            StudioStatusPill(text = "DUCK", dotColor = SignalAmber)
                        } else if (uiState.isAutoPausedByVoice) {
                            StudioStatusPill(text = "PAUSA", dotColor = CoralCrimson)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Quick Link / Instagram Reels button
                        IconButton(
                            onClick = onOpenUrlDialog,
                            modifier = Modifier
                                .size(38.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(Color(0xB310141E))
                                .testTag("header_link_reels_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = stringResource(R.string.studio_paste_url),
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Mute Toggle (ExoPlayer mode)
                        if (!isInstagramEmbed) {
                            IconButton(
                                onClick = onToggleMute,
                                modifier = Modifier
                                    .size(38.dp)
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(Color(0xB310141E))
                                    .testTag("video_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isVideoMuted) {
                                        Icons.AutoMirrored.Rounded.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Rounded.VolumeUp
                                    },
                                    contentDescription = stringResource(R.string.cd_toggle_mute),
                                    tint = if (uiState.isVideoMuted) CoralCrimson else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // GitHub Updates Button
                        UpdateBadgeIconButton(
                            hasUpdateAvailable = uiState.hasUpdateAvailable,
                            onClick = onOpenAppUpdates
                        )

                        // Framing Mode Button
                        Surface(
                            onClick = { onToggleFramingMode(!uiState.isFramingMode) },
                            shape = RoundedCornerShape(50),
                            color = if (uiState.isFramingMode) ElectricCyan else Color(0xCC10141E),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("framing_mode_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Crop,
                                    contentDescription = stringResource(R.string.cd_framing_settings),
                                    tint = if (uiState.isFramingMode) StudioVoid else ElectricCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (uiState.isFramingMode) {
                                        stringResource(R.string.studio_framing_done)
                                    } else {
                                        stringResource(R.string.studio_framing_mode)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isFramingMode) StudioVoid else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isBuffering && !isInstagramEmbed) {
                CircularProgressIndicator(
                    color = ElectricCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                )
            }

            // Bottom Overlay: Either Framing Panel OR Playback Controls
            AnimatedVisibility(
                visible = uiState.isFramingMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                FramingControlsOverlay(
                    uiState = uiState,
                    onScaleChange = onFramingScaleChange,
                    onSelectAspect = onSelectAspectPreset,
                    onToggleGrid = onToggleGrid,
                    onReset = onResetFraming
                )
            }

            AnimatedVisibility(
                visible = !uiState.isFramingMode && !isInstagramEmbed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlaybackTransportBar(
                    uiState = uiState,
                    onTogglePlayPause = onTogglePlayPause,
                    onRewind = onRewind,
                    onForward = onForward,
                    onSeekTo = onSeekTo,
                    onCycleSpeed = onCycleSpeed
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ActiveVideoViewport(
    uiState: StudioUiState,
    exoPlayer: ExoPlayer,
    onTapVideo: () -> Unit,
    onFramingGesture: (Float, Float, Float) -> Unit
) {
    val framing = uiState.framing
    val isInstagramEmbed = uiState.sourceVideoType == VideoSourceType.INSTAGRAM_EMBED

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val naturalAspect = if (isInstagramEmbed) {
            9f / 16f
        } else {
            (uiState.videoNaturalWidth.toFloat() / uiState.videoNaturalHeight.toFloat())
                .coerceIn(0.35f, 3.5f)
        }
        val containerAspect = containerW / containerH

        val (targetW, targetH) = when (framing.aspectPreset) {
            VideoAspectPreset.FILL -> {
                if (naturalAspect > containerAspect) {
                    val h = containerH
                    val w = h * naturalAspect
                    w to h
                } else {
                    val w = containerW
                    val h = w / naturalAspect
                    w to h
                }
            }
            VideoAspectPreset.FIT -> {
                if (naturalAspect > containerAspect) {
                    val w = containerW
                    val h = w / naturalAspect
                    w to h
                } else {
                    val h = containerH
                    val w = h * naturalAspect
                    w to h
                }
            }
            VideoAspectPreset.RATIO_9_16 -> fitBox(containerW, containerH, 9f / 16f)
            VideoAspectPreset.RATIO_16_9 -> fitBox(containerW, containerH, 16f / 9f)
            VideoAspectPreset.RATIO_1_1 -> fitBox(containerW, containerH, 1f)
            VideoAspectPreset.RATIO_4_3 -> fitBox(containerW, containerH, 4f / 3f)
        }

        val density = LocalDensity.current
        val widthDp = with(density) { targetW.toDp() }
        val heightDp = with(density) { targetH.toDp() }

        Box(
            modifier = Modifier
                .size(widthDp, heightDp)
                .clipToBounds()
                .then(
                    if (framing.aspectPreset != VideoAspectPreset.FILL && framing.aspectPreset != VideoAspectPreset.FIT) {
                        Modifier.border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                )
        ) {
            if (isInstagramEmbed) {
                val embedUrl = uiState.sourceVideoUri.orEmpty()
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return true
                                    return !InstagramLinkResolver.isAllowedInstagramEmbedUrl(url)
                                }
                            }
                            if (InstagramLinkResolver.isAllowedInstagramEmbedUrl(embedUrl)) {
                                loadUrl(embedUrl)
                            }
                        }
                    },
                    update = { webView ->
                        if (webView.url != embedUrl && InstagramLinkResolver.isAllowedInstagramEmbedUrl(embedUrl)) {
                            webView.loadUrl(embedUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = framing.scale
                            scaleY = framing.scale
                            translationX = framing.offsetX
                            translationY = framing.offsetY
                        }
                )
            } else {
                var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

                DisposableEffect(exoPlayer) {
                    onDispose {
                        textureViewRef?.let { exoPlayer.clearVideoTextureView(it) }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            textureViewRef = tv
                            exoPlayer.setVideoTextureView(tv)
                        }
                    },
                    update = { tv ->
                        textureViewRef = tv
                        exoPlayer.setVideoTextureView(tv)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = !uiState.isFramingMode, onClick = onTapVideo)
                        .graphicsLayer {
                            scaleX = framing.scale
                            scaleY = framing.scale
                            translationX = framing.offsetX
                            translationY = framing.offsetY
                        }
                )
            }
        }

        // Gesture Interceptor + Rule of Thirds Grid while in Framing Mode (works for BOTH MP4 & Instagram Reels!)
        if (uiState.isFramingMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onFramingGesture(zoom, pan.x, pan.y)
                        }
                    }
            ) {
                if (framing.showRuleOfThirds) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 1.dp.toPx()
                        val lineColor = ElectricCyan.copy(alpha = 0.45f)
                        val thirdW = size.width / 3f
                        val thirdH = size.height / 3f
                        drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), stroke)
                        drawLine(lineColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), stroke)
                        drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), stroke)
                        drawLine(lineColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), stroke)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val arm = 12.dp.toPx()
                        drawLine(CoralCrimson.copy(alpha = 0.8f), Offset(cx - arm, cy), Offset(cx + arm, cy), 1.5.dp.toPx())
                        drawLine(CoralCrimson.copy(alpha = 0.8f), Offset(cx, cy - arm), Offset(cx, cy + arm), 1.5.dp.toPx())
                    }
                }
            }
        }
    }
}

private fun fitBox(maxW: Float, maxH: Float, aspect: Float): Pair<Float, Float> {
    return if (aspect > maxW / maxH) {
        val w = maxW
        val h = w / aspect
        w to min(h, maxH)
    } else {
        val h = maxH
        val w = h * aspect
        min(w, maxW) to h
    }
}

@Composable
private fun UpdateBadgeIconButton(
    hasUpdateAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(38.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(Color(0xB310141E))
                .testTag("app_updates_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = stringResource(R.string.cd_app_updates),
                tint = if (hasUpdateAvailable) CoralCrimson else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        if (hasUpdateAvailable) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(CoralCrimson)
            )
        }
    }
}

@Composable
private fun FramingControlsOverlay(
    uiState: StudioUiState,
    onScaleChange: (Float) -> Unit,
    onSelectAspect: (VideoAspectPreset) -> Unit,
    onToggleGrid: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xEB0A0D14), Color(0xF70A0D14))
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.studio_framing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = ElectricCyan
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = onToggleGrid,
                    shape = RoundedCornerShape(50),
                    color = if (uiState.framing.showRuleOfThirds) {
                        ElectricCyan.copy(alpha = 0.2f)
                    } else {
                        Color(0x99171D2B)
                    },
                    modifier = Modifier.testTag("framing_grid_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GridOn,
                        contentDescription = "Grade de enquadramento",
                        tint = if (uiState.framing.showRuleOfThirds) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .size(16.dp)
                    )
                }
                Surface(
                    onClick = onReset,
                    shape = RoundedCornerShape(50),
                    color = Color(0xCC171D2B),
                    modifier = Modifier.testTag("framing_reset_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.studio_framing_reset),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.studio_framing_reset),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Aspect Ratio Presets Row (includes 9:16 for Reels!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VideoAspectPreset.entries.forEach { preset ->
                val selected = uiState.framing.aspectPreset == preset
                Surface(
                    onClick = { onSelectAspect(preset) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) ElectricCyan else Color(0xCC171D2B),
                    modifier = Modifier.testTag("aspect_preset_${preset.name.lowercase()}")
                ) {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) StudioVoid else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Precision Zoom Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ZOOM",
                style = StudioMonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = uiState.framing.scale,
                onValueChange = onScaleChange,
                valueRange = 1f..3.5f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = Color(0x552E3A54)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("framing_zoom_slider")
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1fx", uiState.framing.scale),
                style = StudioMonoStyle,
                color = ElectricCyan
            )
        }
    }
}

@Composable
private fun PlaybackTransportBar(
    uiState: StudioUiState,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit
) {
    val duration = uiState.durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val progress = if (isDragging) {
        dragFraction
    } else {
        (uiState.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC080A10), Color(0xF2080A10))
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = progress,
                onValueChange = { value ->
                    isDragging = true
                    dragFraction = value
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeekTo((dragFraction * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = Color(0x66A0AEC6)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_scrubber_slider")
            )

            if (uiState.reactionMarkers.isNotEmpty() && uiState.durationMs > 0L) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .padding(horizontal = 10.dp)
                ) {
                    uiState.reactionMarkers.forEach { marker ->
                        val frac = (marker.videoPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        drawCircle(
                            color = CoralCrimson,
                            radius = 3.5.dp.toPx(),
                            center = Offset(size.width * frac, size.height / 2f)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatTimecode(uiState.currentPositionMs)} / ${formatTimecode(uiState.durationMs)}",
                style = StudioMonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onRewind,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(Color(0x99171D2B))
                        .testTag("video_rewind_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.cd_rewind),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(ElectricCyan)
                        .testTag("video_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (uiState.isPlaying) R.string.cd_pause else R.string.cd_play
                        ),
                        tint = StudioVoid,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onForward,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(Color(0x99171D2B))
                        .testTag("video_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = stringResource(R.string.cd_forward),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Surface(
                onClick = onCycleSpeed,
                shape = RoundedCornerShape(10.dp),
                color = Color(0xCC171D2B),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_speed_button")
            ) {
                Text(
                    text = "${uiState.playbackSpeed}x",
                    style = StudioMonoStyle,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyVideoStage(
    hasUpdateAvailable: Boolean,
    onPickVideo: () -> Unit,
    onOpenUrlDialog: () -> Unit,
    onOpenAppUpdates: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.img_stage_empty),
            contentDescription = stringResource(R.string.studio_empty_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x88080A10),
                            Color(0xB8080A10),
                            Color(0xE6080A10)
                        )
                    )
                )
        )

        // Top-right Updates shortcut button
        UpdateBadgeIconButton(
            hasUpdateAvailable = hasUpdateAvailable,
            onClick = onOpenAppUpdates,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StudioStatusPill(
                text = "PALCO SUPERIOR · VÍDEO / REELS",
                dotColor = ElectricCyan
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.studio_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.studio_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPickVideo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = StudioVoid
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("pick_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.studio_pick_video),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onOpenUrlDialog,
                    shape = RoundedCornerShape(14.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(CoralCrimson.copy(alpha = 0.7f))
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("paste_url_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = CoralCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.studio_paste_url),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

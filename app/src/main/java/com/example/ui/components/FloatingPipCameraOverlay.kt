package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.ReactLayoutMode
import com.example.model.StudioUiState
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StudioOutlineSubtle
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Draggable & pinch-resizable floating camera overlay rendered on top of the full-screen 9:16 video
 * in [ReactLayoutMode.PIP_SQUARE], [ReactLayoutMode.PIP_CIRCLE], and [ReactLayoutMode.PIP_CUTOUT].
 *
 * Uses exact normalized (centerXFraction, centerYFraction, widthFraction) coordinates so the
 * exported 720x1280 MP4 matches the user's live placement 1:1.
 */
@Composable
fun FloatingPipCameraOverlay(
    uiState: StudioUiState,
    cameraController: CameraStudioController,
    hasCameraPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onTransformPip: (deltaXFraction: Float, deltaYFraction: Float, zoomFactor: Float) -> Unit,
    onToggleCameraLens: () -> Unit,
    onToggleMirror: () -> Unit,
    onResetPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutMode = uiState.layoutMode
    val pipState = uiState.pipOverlay
    val isCutout = layoutMode == ReactLayoutMode.PIP_CUTOUT
    var showQuickActions by remember { mutableStateOf(false) }

    LaunchedEffect(showQuickActions, uiState.isRecording) {
        if (uiState.isRecording) {
            showQuickActions = false
        } else if (showQuickActions) {
            delay(4_000L)
            showQuickActions = false
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "pip_reaction_halo")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(620),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pip_pulse_alpha"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (uiState.isReactingNow) 3.5.dp else 2.dp,
        animationSpec = spring(),
        label = "pip_border_width"
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
            colors = listOf(
                ElectricCyan.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.65f)
            )
        )
    }

    val pipShape = when (layoutMode) {
        ReactLayoutMode.PIP_CIRCLE -> CircleShape
        else -> RoundedCornerShape(22.dp)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current

        val pipWidthPx = (containerW * pipState.widthFraction).coerceIn(containerW * 0.22f, containerW * 0.72f)
        val pipHeightPx = (pipWidthPx * pipState.heightToWidthRatio(layoutMode)).coerceAtMost(containerH * 0.72f)
        val leftPx = (pipState.centerXFraction * containerW - pipWidthPx / 2f).roundToInt()
        val topPx = (pipState.centerYFraction * containerH - pipHeightPx / 2f).roundToInt()

        val pipWidthDp = with(density) { pipWidthPx.toDp() }
        val pipHeightDp = with(density) { pipHeightPx.toDp() }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .offset { IntOffset(leftPx, topPx) }
        ) {
            Box(
                modifier = Modifier
                    .size(pipWidthDp, pipHeightDp)
                    .then(
                        if (!isCutout) {
                            Modifier
                                .shadow(14.dp, pipShape, ambientColor = Color.Black, spotColor = Color.Black)
                                .clip(pipShape)
                                .background(StudioVoid)
                                .border(borderWidth, borderBrush, pipShape)
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .then(
                                    if (showQuickActions || uiState.isReactingNow) {
                                        Modifier.border(
                                            width = if (uiState.isReactingNow) 2.dp else 1.dp,
                                            brush = if (uiState.isReactingNow) {
                                                borderBrush
                                            } else {
                                                Brush.linearGradient(
                                                    listOf(
                                                        ElectricCyan.copy(alpha = 0.55f),
                                                        ElectricCyan.copy(alpha = 0.25f)
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        }
                    )
                    .pointerInput(containerW, containerH) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onTransformPip(
                                pan.x / containerW,
                                pan.y / containerH,
                                zoom
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!hasCameraPermission) {
                                    onRequestPermissions()
                                } else {
                                    showQuickActions = !showQuickActions
                                }
                            }
                        )
                    }
                    .testTag("floating_pip_camera_overlay"),
                contentAlignment = Alignment.Center
            ) {
                if (!hasCameraPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xD910141E))
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = stringResource(R.string.studio_grant_permissions),
                            tint = CoralCrimson,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Permitir câmera",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    val hasLiveCutout = isCutout && cameraController.cutoutBitmap != null

                    // Keep shared PreviewView attached so CameraX stays live; hide behind cutout bitmap
                    AndroidView(
                        factory = { cameraController.obtainDetachedPreviewView() },
                        update = { },
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (hasLiveCutout) 0f else 1f)
                            .graphicsLayer {
                                scaleX = if (uiState.isFrontCamera && !uiState.isCameraMirrored) -1f else 1f
                            }
                    )

                    if (isCutout) {
                        val bmp = cameraController.cutoutBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = stringResource(R.string.layout_pip_cutout),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Brief warm-up indicator while ML Kit initializes on first frame
                            Surface(
                                color = Color(0xCC0A0D14),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = ElectricCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.pip_cutout_warming),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tap-to-reveal mini floating camera tools (Flip, Mirror, Reset Position)
            AnimatedVisibility(
                visible = showQuickActions && hasCameraPermission && !uiState.isRecording,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE010141E))
                        .border(1.dp, StudioOutlineSubtle, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onToggleCameraLens,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("pip_flip_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = stringResource(R.string.cd_flip_camera),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleMirror,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("pip_mirror_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flip,
                            contentDescription = stringResource(R.string.cd_mirror_camera),
                            tint = if (uiState.isCameraMirrored) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onResetPip,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("pip_reset_position_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = stringResource(R.string.cd_reset_pip),
                            tint = ElectricCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleek 4-option Layout Mode Switcher Pill Bar:
 *  - Dividida (top/bottom split screen)
 *  - Quadrado (full-screen 9:16 + draggable rounded square PiP)
 *  - Redondo (full-screen 9:16 + draggable circular PiP)
 *  - Recorte (full-screen 9:16 + draggable on-device AI person cutout)
 */
@Composable
fun LayoutModeSelectorBar(
    selectedMode: ReactLayoutMode,
    onSelectMode: (ReactLayoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(50))
            .background(Color(0xD910141E))
            .border(1.dp, StudioOutlineSubtle, RoundedCornerShape(50))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ReactLayoutMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            val icon = when (mode) {
                ReactLayoutMode.SPLIT_SCREEN -> Icons.Rounded.Splitscreen
                ReactLayoutMode.PIP_SQUARE -> Icons.Rounded.PictureInPictureAlt
                ReactLayoutMode.PIP_CIRCLE -> Icons.Rounded.AccountCircle
                ReactLayoutMode.PIP_CUTOUT -> Icons.Rounded.AutoFixHigh
            }
            val labelRes = when (mode) {
                ReactLayoutMode.SPLIT_SCREEN -> R.string.layout_split
                ReactLayoutMode.PIP_SQUARE -> R.string.layout_pip_square
                ReactLayoutMode.PIP_CIRCLE -> R.string.layout_pip_circle
                ReactLayoutMode.PIP_CUTOUT -> R.string.layout_pip_cutout
            }
            val accent = if (mode == ReactLayoutMode.PIP_CUTOUT) CoralCrimson else ElectricCyan

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) accent else Color.Transparent)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("layout_mode_${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(labelRes),
                    tint = if (selected) StudioVoid else accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) StudioVoid else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

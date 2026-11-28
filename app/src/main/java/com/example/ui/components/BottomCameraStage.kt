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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.StudioUiState
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioVoid

/**
 * Split-screen bottom camera stage using the shared [CameraStudioController] and [StudioControlDock].
 */
@Composable
fun BottomCameraStage(
    uiState: StudioUiState,
    cameraController: CameraStudioController,
    hasCameraPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onToggleCameraLens: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleManualReact: () -> Unit,
    onOpenAudioPanel: () -> Unit,
    onOpenTakesPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            AndroidView(
                factory = { cameraController.obtainDetachedPreviewView() },
                update = { },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
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

            // Live Mini VU + Headphone Badge
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

        // Bottom Studio Control Dock
        StudioControlDock(
            uiState = uiState,
            hasCameraPermission = hasCameraPermission,
            onOpenAudioPanel = onOpenAudioPanel,
            onToggleManualReact = onToggleManualReact,
            onRecordClick = cameraController.onToggleRecording,
            onOpenTakesPanel = onOpenTakesPanel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

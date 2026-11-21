package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.ReactionShareHelper
import com.example.media.SocialShareTarget
import com.example.model.ExportStatus
import com.example.model.StudioUiState
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioLavender
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioOutlineSubtle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportShareModalSheet(
    uiState: StudioUiState,
    directShareLink: String,
    onCombineWithSource: () -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeTake = uiState.lastRecordedTake
    val exportStatus = uiState.exportStatus

    val activeShareUri = when (exportStatus) {
        is ExportStatus.CombinedReady -> exportStatus.combinedUri
        else -> activeTake?.uriString
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        scrimColor = Color(0xB3000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CoralCrimson.copy(alpha = 0.25f), ElectricCyan.copy(alpha = 0.25f))
                                )
                            )
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MovieCreation,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.export_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.export_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(StudioSurfaceElevated)
                        .testTag("close_export_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Combined Split-Screen Render Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudioSurfaceElevated)
                    .border(
                        1.dp,
                        when (exportStatus) {
                            is ExportStatus.CombinedReady -> ElectricCyan.copy(alpha = 0.65f)
                            is ExportStatus.Error -> CoralCrimson.copy(alpha = 0.6f)
                            else -> StudioOutlineSubtle
                        },
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (exportStatus is ExportStatus.CombinedReady) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.Splitscreen
                        },
                        contentDescription = null,
                        tint = if (exportStatus is ExportStatus.CombinedReady) ElectricCyan else CoralCrimson,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (exportStatus) {
                                is ExportStatus.CombinedReady -> stringResource(R.string.export_combined_ready)
                                is ExportStatus.Combining -> stringResource(R.string.export_combining)
                                else -> "Vídeo Combinado (Tela Dividida 9:16)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (exportStatus) {
                                is ExportStatus.CombinedReady -> exportStatus.displayName
                                is ExportStatus.Combining -> "${(exportStatus.progress * 100).toInt()}% · Vídeo superior + Câmera inferior"
                                is ExportStatus.Error -> exportStatus.message
                                else -> "Gera um novo arquivo MP4 na Galeria unindo o vídeo de cima com sua reação de baixo."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (exportStatus is ExportStatus.Error) CoralCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (exportStatus) {
                    is ExportStatus.Combining -> {
                        LinearProgressIndicator(
                            progress = { exportStatus.progress.coerceIn(0.02f, 1f) },
                            color = ElectricCyan,
                            trackColor = StudioVoid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                    is ExportStatus.CombinedReady -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(exportStatus.combinedUri), "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = SolidColor(ElectricCyan.copy(alpha = 0.6f))
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_combined_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Assistir Combinado", color = ElectricCyan)
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = onCombineWithSource,
                            enabled = uiState.sourceVideoUri != null && activeTake != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = StudioVoid
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("combine_videos_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.export_combine_cta),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Social Media Quick Share Section
            if (activeShareUri != null) {
                Text(
                    text = stringResource(R.string.export_share_social).uppercase(),
                    style = StudioMonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val caption = buildString {
                    append("Minha reação gravada no React Studio!")
                    if (!uiState.sourceOriginalLink.isNullOrBlank()) {
                        append("\nVídeo original: ${uiState.sourceOriginalLink}")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val palette = listOf(
                        SocialShareTarget.INSTAGRAM to Color(0xFFE1306C),
                        SocialShareTarget.TIKTOK to ElectricCyan,
                        SocialShareTarget.WHATSAPP to Color(0xFF25D366),
                        SocialShareTarget.YOUTUBE to Color(0xFFFF3344),
                        SocialShareTarget.SYSTEM to StudioLavender
                    )
                    palette.forEach { (target, tint) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .width(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(StudioSurfaceElevated)
                                .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .clickable {
                                    val msg = ReactionShareHelper.shareVideoToTarget(
                                        context = context,
                                        videoUriString = activeShareUri,
                                        target = target,
                                        captionText = caption
                                    )
                                    onShowMessage(msg)
                                }
                                .padding(vertical = 12.dp, horizontal = 6.dp)
                                .testTag("share_social_${target.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = target.label,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = target.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Direct Link Section
                Text(
                    text = stringResource(R.string.export_direct_link_title).uppercase(),
                    style = StudioMonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioSurfaceElevated)
                        .border(1.dp, StudioOutlineSubtle, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioVoid)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = directShareLink,
                            style = StudioMonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val copied = ReactionShareHelper.copyDirectLink(context, directShareLink)
                                onShowMessage(
                                    if (copied) "Link direto copiado!" else "Não foi possível copiar"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = SolidColor(ElectricCyan.copy(alpha = 0.55f))
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_direct_link_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.export_copy_link),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }

                        Button(
                            onClick = {
                                val msg = ReactionShareHelper.shareToCloudForDirectLink(
                                    context = context,
                                    videoUriString = activeShareUri,
                                    title = activeTake?.displayName ?: "ReactStudio.mp4"
                                )
                                onShowMessage(msg)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalAmber,
                                contentColor = StudioVoid
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("cloud_direct_link_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Link na Nuvem",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

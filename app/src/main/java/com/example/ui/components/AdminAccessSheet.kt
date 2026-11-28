package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.firebase.AccessControlEntry
import com.example.firebase.AccessOverrideStatus
import com.example.model.SubscriptionUiState
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioOutlineSubtle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAccessModalSheet(
    subscription: SubscriptionUiState,
    onGrantOrBlock: (email: String, status: AccessOverrideStatus, note: String) -> Unit,
    onRemoveRule: (entryId: String) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var emailInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        scrimColor = Color(0xB8000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(ElectricCyan.copy(alpha = 0.16f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.admin_panel_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.admin_panel_subtitle),
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
                        .testTag("close_admin_panel_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Form card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudioSurfaceElevated)
                    .border(1.dp, StudioOutlineSubtle, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        if (subscription.adminErrorMessage != null) onClearError()
                    },
                    label = { Text(stringResource(R.string.admin_email_label)) },
                    placeholder = { Text(stringResource(R.string.admin_email_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = StudioOutlineSubtle,
                        focusedLabelColor = ElectricCyan,
                        cursorColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_email_input")
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it.take(140) },
                    label = { Text(stringResource(R.string.admin_note_label)) },
                    placeholder = { Text(stringResource(R.string.admin_note_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = StudioOutlineSubtle,
                        focusedLabelColor = ElectricCyan,
                        cursorColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_note_input")
                )

                if (!subscription.adminErrorMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoralCrimson.copy(alpha = 0.14f))
                            .border(1.dp, CoralCrimson.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = CoralCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = subscription.adminErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onGrantOrBlock(emailInput, AccessOverrideStatus.ALLOWED, noteInput)
                            emailInput = ""
                            noteInput = ""
                        },
                        enabled = !subscription.isAdminMutating && emailInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = StudioVoid
                        ),
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admin_grant_access_button")
                    ) {
                        if (subscription.isAdminMutating) {
                            CircularProgressIndicator(
                                color = StudioVoid,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.admin_grant_cta),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onGrantOrBlock(emailInput, AccessOverrideStatus.BLOCKED, noteInput)
                            emailInput = ""
                            noteInput = ""
                        },
                        enabled = !subscription.isAdminMutating && emailInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralCrimson,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admin_block_access_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.admin_block_cta),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Google Play Compliance Reminder
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElectricCyan.copy(alpha = 0.08f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GppGood,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = stringResource(R.string.admin_play_policy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Managed Rules List
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "REGRAS ATIVAS (${subscription.adminEntries.size})",
                    style = StudioMonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (subscription.isAdminLoading) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (subscription.adminEntries.isEmpty() && !subscription.isAdminLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioSurfaceElevated.copy(alpha = 0.6f))
                        .border(1.dp, StudioOutlineSubtle, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.admin_empty_rules),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subscription.adminEntries, key = { it.id.ifBlank { it.emailLower } }) { entry ->
                        AdminRuleCard(
                            entry = entry,
                            isMutating = subscription.isAdminMutating,
                            onToggleStatus = {
                                val nextStatus = if (entry.parsedStatus == AccessOverrideStatus.ALLOWED) {
                                    AccessOverrideStatus.BLOCKED
                                } else {
                                    AccessOverrideStatus.ALLOWED
                                }
                                onGrantOrBlock(entry.emailLower, nextStatus, entry.note)
                            },
                            onDelete = { onRemoveRule(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminRuleCard(
    entry: AccessControlEntry,
    isMutating: Boolean,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val isAllowed = entry.parsedStatus == AccessOverrideStatus.ALLOWED
    val accent = if (isAllowed) ElectricCyan else CoralCrimson
    val dateLabel = remember(entry.updatedAt) {
        entry.updatedAt?.toDate()?.let { date ->
            SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(date)
        } ?: "Agora"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSurfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.emailLower,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isAllowed) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isAllowed) "LIBERADO" else "BLOQUEADO",
                        style = StudioMonoStyle,
                        color = accent
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Atualizado: $dateLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleStatus,
                    enabled = !isMutating,
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(if (isAllowed) CoralCrimson.copy(alpha = 0.5f) else ElectricCyan.copy(alpha = 0.5f))
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("toggle_rule_${entry.id}")
                ) {
                    Text(
                        text = if (isAllowed) "Bloquear" else "Liberar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAllowed) CoralCrimson else ElectricCyan
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !isMutating,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(StudioSurface)
                        .testTag("delete_rule_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_remove_rule),
                        tint = SignalAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

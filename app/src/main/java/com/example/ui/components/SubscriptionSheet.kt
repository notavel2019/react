package com.example.ui.components

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.SubscriptionAccessState
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
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionModalSheet(
    subscription: SubscriptionUiState,
    onSubscribeClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLegalDialog by remember { mutableStateOf(false) }

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
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = 0.25f),
                                        CoralCrimson.copy(alpha = 0.22f)
                                    )
                                )
                            )
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (subscription.isPro) Icons.Rounded.Verified else Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.sub_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Versão v${BuildConfig.VERSION_NAME} · Google Play",
                            style = StudioMonoStyle,
                            color = ElectricCyan
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(StudioSurfaceElevated)
                        .testTag("close_subscription_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            SubscriptionContentBody(
                subscription = subscription,
                isBlockingPaywall = false,
                onSubscribeClick = onSubscribeClick,
                onRestoreClick = onRestoreClick,
                onManageSubscriptionClick = onManageSubscriptionClick,
                onOpenLegal = { showLegalDialog = true }
            )
        }
    }

    if (showLegalDialog) {
        PrivacyAndTermsDialog(
            priceLabel = subscription.formattedAnnualPrice,
            onDismiss = { showLegalDialog = false }
        )
    }
}

@Composable
fun SubscriptionPaywallOverlay(
    subscription: SubscriptionUiState,
    onSubscribeClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit
) {
    var showLegalDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0E17), StudioVoid, Color(0xFF0F1524))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CoralCrimson.copy(alpha = 0.16f))
                    .border(1.5.dp, CoralCrimson.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (subscription.accessState == SubscriptionAccessState.PENDING_PAYMENT) {
                        Icons.Rounded.HourglassTop
                    } else {
                        Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    tint = CoralCrimson,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = if (subscription.accessState == SubscriptionAccessState.PENDING_PAYMENT) {
                    stringResource(R.string.sub_pending_title)
                } else {
                    stringResource(R.string.sub_expired_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (subscription.isClockTampered) {
                    stringResource(R.string.sub_clock_tampered_desc)
                } else if (subscription.accessState == SubscriptionAccessState.PENDING_PAYMENT) {
                    stringResource(R.string.sub_pending_desc)
                } else {
                    stringResource(R.string.sub_expired_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            SubscriptionContentBody(
                subscription = subscription,
                isBlockingPaywall = true,
                onSubscribeClick = onSubscribeClick,
                onRestoreClick = onRestoreClick,
                onManageSubscriptionClick = onManageSubscriptionClick,
                onOpenLegal = { showLegalDialog = true }
            )
        }
    }

    if (showLegalDialog) {
        PrivacyAndTermsDialog(
            priceLabel = subscription.formattedAnnualPrice,
            onDismiss = { showLegalDialog = false }
        )
    }
}

@Composable
private fun SubscriptionContentBody(
    subscription: SubscriptionUiState,
    isBlockingPaywall: Boolean,
    onSubscribeClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
    onOpenLegal: () -> Unit
) {
    // 1. Current Access Status Banner
    when (subscription.accessState) {
        SubscriptionAccessState.PRO_SUBSCRIBED -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ElectricCyan.copy(alpha = 0.14f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.sub_active_pro_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricCyan
                    )
                    Text(
                        text = stringResource(R.string.sub_active_pro_desc, subscription.formattedAnnualPrice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        SubscriptionAccessState.TRIAL_ACTIVE -> {
            val endLabel = remember(subscription.trialEndsAtMs) {
                if (subscription.trialEndsAtMs > 0L) {
                    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
                        .format(Date(subscription.trialEndsAtMs))
                } else {
                    "—"
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SignalAmber.copy(alpha = 0.13f))
                    .border(1.dp, SignalAmber.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassTop,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.sub_trial_active_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = SignalAmber
                    )
                }
                Text(
                    text = "Tempo restante: ${formatRemainingTrial(subscription.trialRemainingMs)} (até $endLabel)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        else -> Unit
    }

    // 2. Annual Plan Pricing Hero Card (R$ 20,00 / ano)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF151E30), Color(0xFF101624))
                )
            )
            .border(1.5.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.sub_plan_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.sub_plan_trial_badge),
                    style = StudioMonoStyle,
                    color = ElectricCyan
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = subscription.formattedAnnualPrice,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElectricCyan
                )
                Text(
                    text = stringResource(R.string.sub_per_year),
                    style = StudioMonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = StudioOutlineSubtle)

        val features = listOf(
            "Gravação sincronizada em tela dividida (vídeo + câmera)",
            "Enquadramento livre com pinça e arraste sem distorção",
            "Mesa de Áudio profissional: microfone, fone, EQ e Auto-React",
            "Exportação combinada em MP4 HD e compartilhamento nas redes",
            "Suporte a vídeos da galeria, links MP4 e Instagram Reels"
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Optional Billing Message / Warning
    if (!subscription.billingMessage.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioSurfaceElevated)
                .border(1.dp, SignalAmber.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = SignalAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = subscription.billingMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // 3. Primary Subscribe CTA (when not yet PRO)
    if (!subscription.isPro) {
        Button(
            onClick = onSubscribeClick,
            enabled = !subscription.isBillingLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = StudioVoid
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (isBlockingPaywall) "paywall_subscribe_button" else "sheet_subscribe_button")
        ) {
            if (subscription.isBillingLoading) {
                CircularProgressIndicator(
                    color = StudioVoid,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Conectando à Google Play...")
            } else {
                val ctaText = if (subscription.hasPlayFreeTrialOffer && !isBlockingPaywall) {
                    stringResource(R.string.sub_cta_with_trial, subscription.formattedAnnualPrice)
                } else {
                    stringResource(R.string.sub_cta_annual, subscription.formattedAnnualPrice)
                }
                Text(
                    text = ctaText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onManageSubscriptionClick,
            shape = RoundedCornerShape(14.dp),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = SolidColor(ElectricCyan.copy(alpha = 0.6f))
            ),
            contentPadding = PaddingValues(vertical = 13.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manage_subscription_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.ManageAccounts,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sub_manage_in_play),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // 4. Restore Purchases + Manage Subscription Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onRestoreClick,
            shape = RoundedCornerShape(14.dp),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = SolidColor(StudioOutlineSubtle)
            ),
            contentPadding = PaddingValues(vertical = 11.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("restore_purchases_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.sub_restore_purchases),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!subscription.isPro) {
            OutlinedButton(
                onClick = onManageSubscriptionClick,
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = SolidColor(StudioOutlineSubtle)
                ),
                contentPadding = PaddingValues(vertical = 11.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("play_subscriptions_link_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ManageAccounts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Google Play",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // 5. Mandatory Google Play Subscription Transparency Disclosure
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSurfaceElevated.copy(alpha = 0.7f))
            .border(1.dp, StudioOutlineSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.sub_play_disclosure, subscription.formattedAnnualPrice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenLegal)
                .padding(vertical = 4.dp)
                .testTag("privacy_terms_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.GppGood,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.sub_privacy_and_terms),
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan
            )
        }
    }
}

@Composable
private fun PrivacyAndTermsDialog(
    priceLabel: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = {
            Text(
                text = stringResource(R.string.sub_privacy_and_terms),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Privacidade e Dados Locais",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Text(
                    text = "O React Studio processa câmera, microfone e renderização de vídeos 100% localmente no seu dispositivo. Não coletamos, vendemos nem enviamos seus vídeos ou áudios para servidores externos. As gravações são salvas diretamente na galeria do seu celular (Movies/ReactStudio).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Assinatura Anual e Teste de 4 Dias",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Text(
                    text = "Novos usuários contam com 4 dias corridos de teste completo de todas as funções do estúdio. Após esse período, o acesso contínuo requer a assinatura anual de $priceLabel/ano processada exclusivamente pelo Google Play Billing. A assinatura renova automaticamente a cada ano, salvo cancelamento em Google Play > Pagamentos e assinaturas antes da data de renovação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Permissões Utilizadas",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Text(
                    text = "• Câmera e Microfone: usados apenas para gravar sua reação e detectar voz no estúdio.\n• Seletor de Fotos do Android: usado para escolher vídeos da galeria sem pedir acesso amplo ao armazenamento.\n• Internet: usada apenas para validar sua assinatura na Google Play e carregar links de vídeo informados por você.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendi", color = ElectricCyan)
            }
        }
    )
}

@Composable
fun SubscriptionStatusChip(
    subscription: SubscriptionUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (subscription.accessState) {
        SubscriptionAccessState.PRO_SUBSCRIBED -> ElectricCyan
        SubscriptionAccessState.TRIAL_ACTIVE -> SignalAmber
        SubscriptionAccessState.TRIAL_EXPIRED,
        SubscriptionAccessState.PENDING_PAYMENT -> CoralCrimson
        SubscriptionAccessState.CHECKING -> ElectricCyan
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0xCC10141E),
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .testTag("subscription_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (subscription.isPro) Icons.Rounded.Verified else Icons.Rounded.AutoAwesome,
                contentDescription = stringResource(R.string.cd_subscription_plan),
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = subscription.badgeLabel,
                style = StudioMonoStyle,
                color = accent
            )
        }
    }
}

private fun formatRemainingTrial(remainingMs: Long): String {
    val totalMinutes = (remainingMs / 60_000L).coerceAtLeast(0L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val mins = totalMinutes % 60L
    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${mins}m"
        else -> "${mins} min"
    }
}

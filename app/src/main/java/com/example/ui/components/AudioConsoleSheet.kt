package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioDeviceItem
import com.example.model.AudioPanelTab
import com.example.model.AudioSettingsState
import com.example.model.AutoReactMode
import com.example.model.HeadphoneEqProfile
import com.example.model.StudioUiState
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioLavender
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioOutlineSubtle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.StudioVoid
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioConsoleModalSheet(
    uiState: StudioUiState,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onDismiss: () -> Unit,
    onSelectTab: (AudioPanelTab) -> Unit,
    onUpdateSettings: ((AudioSettingsState) -> AudioSettingsState) -> Unit,
    onSelectInputDevice: (Int) -> Unit,
    onSelectOutputDevice: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        scrimColor = Color(0xB3000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        AudioConsoleContent(
            uiState = uiState,
            hasAudioPermission = hasAudioPermission,
            onRequestAudioPermission = onRequestAudioPermission,
            onClose = onDismiss,
            showCloseButton = true,
            onSelectTab = onSelectTab,
            onUpdateSettings = onUpdateSettings,
            onSelectInputDevice = onSelectInputDevice,
            onSelectOutputDevice = onSelectOutputDevice,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@Composable
fun AudioConsoleContent(
    uiState: StudioUiState,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onClose: () -> Unit,
    showCloseButton: Boolean,
    onSelectTab: (AudioPanelTab) -> Unit,
    onUpdateSettings: ((AudioSettingsState) -> AudioSettingsState) -> Unit,
    onSelectInputDevice: (Int) -> Unit,
    onSelectOutputDevice: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(StudioSurface)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
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
                                colors = listOf(ElectricCyan.copy(alpha = 0.28f), StudioLavender.copy(alpha = 0.25f))
                            )
                        )
                        .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.audio_panel_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.audio_panel_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(StudioSurfaceElevated)
                        .testTag("close_audio_console_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Live Studio VU Meter & Waveform Visualizer Card
        LiveStudioVuMeterCard(
            uiState = uiState,
            hasAudioPermission = hasAudioPermission,
            onRequestAudioPermission = onRequestAudioPermission
        )

        // 3-Segment Studio Switcher: Microfone | Fone & Mix | Auto-React
        AudioTabSelector(
            selected = uiState.activeAudioTab,
            onSelect = onSelectTab
        )

        // Active Tab Body
        when (uiState.activeAudioTab) {
            AudioPanelTab.MICROPHONE -> MicrophoneSettingsTab(
                uiState = uiState,
                onUpdateSettings = onUpdateSettings,
                onSelectInputDevice = onSelectInputDevice
            )
            AudioPanelTab.HEADPHONES -> HeadphoneSettingsTab(
                uiState = uiState,
                onUpdateSettings = onUpdateSettings,
                onSelectOutputDevice = onSelectOutputDevice
            )
            AudioPanelTab.AUTO_REACT -> AutoReactSettingsTab(
                uiState = uiState,
                onUpdateSettings = onUpdateSettings
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LiveStudioVuMeterCard(
    uiState: StudioUiState,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit
) {
    val thresholdNormalized = ((uiState.audioSettings.voiceThresholdDb + 60f) / 60f).coerceIn(0.05f, 0.95f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(StudioSurfaceElevated, Color(0xFF131929))
                )
            )
            .border(
                1.dp,
                if (uiState.isSpeaking) CoralCrimson.copy(alpha = 0.65f) else StudioOutlineSubtle,
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioStatusPill(
                    text = if (uiState.isSpeaking) "VOZ ATIVA" else "MONITOR MIC",
                    dotColor = if (uiState.isSpeaking) CoralCrimson else ElectricCyan
                )
                Text(
                    text = formatDb(uiState.currentMicDb),
                    style = StudioMonoStyle,
                    color = if (uiState.isSpeaking) CoralCrimson else ElectricCyan
                )
            }

            // Headphone live status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (uiState.hasHeadphonesConnected) {
                            ElectricCyan.copy(alpha = 0.14f)
                        } else {
                            SignalAmber.copy(alpha = 0.14f)
                        }
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (uiState.hasHeadphonesConnected) Icons.Rounded.Headphones else Icons.Rounded.Speaker,
                    contentDescription = null,
                    tint = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = uiState.activeOutputName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber
                )
            }
        }

        if (!hasAudioPermission) {
            Surface(
                onClick = onRequestAudioPermission,
                shape = RoundedCornerShape(12.dp),
                color = CoralCrimson.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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
                        text = stringResource(R.string.studio_mic_permission_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // 28-bar Real-Time Audio Spectrum + Voice Threshold Line
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioVoid)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val bars = uiState.waveformBars
                val count = bars.size.coerceAtLeast(1)
                val gap = 3.dp.toPx()
                val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())

                bars.forEachIndexed { index, level ->
                    val clamped = level.coerceIn(0.06f, 1f)
                    val barH = size.height * clamped
                    val topY = size.height - barH
                    val color = when {
                        clamped >= 0.82f -> CoralCrimson
                        clamped >= thresholdNormalized -> ElectricCyan
                        else -> Color(0xFF334466)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(index * (barW + gap), topY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }

                // Voice Gate Threshold dashed line
                val thresholdY = size.height * (1f - thresholdNormalized)
                drawLine(
                    color = SignalAmber.copy(alpha = 0.9f),
                    start = Offset(0f, thresholdY),
                    end = Offset(size.width, thresholdY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entrada: ${uiState.activeInputName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Limiar: ${formatDb(uiState.audioSettings.voiceThresholdDb)}",
                    style = StudioMonoStyle,
                    color = SignalAmber
                )
            }
        }
    }
}

@Composable
private fun AudioTabSelector(
    selected: AudioPanelTab,
    onSelect: (AudioPanelTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioVoid)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AudioTabChip(
            title = stringResource(R.string.audio_tab_mic),
            icon = Icons.Rounded.Mic,
            selected = selected == AudioPanelTab.MICROPHONE,
            onClick = { onSelect(AudioPanelTab.MICROPHONE) },
            modifier = Modifier
                .weight(1f)
                .testTag("audio_tab_microphone")
        )
        AudioTabChip(
            title = stringResource(R.string.audio_tab_headphones),
            icon = Icons.Rounded.Headphones,
            selected = selected == AudioPanelTab.HEADPHONES,
            onClick = { onSelect(AudioPanelTab.HEADPHONES) },
            modifier = Modifier
                .weight(1f)
                .testTag("audio_tab_headphones")
        )
        AudioTabChip(
            title = stringResource(R.string.audio_tab_react),
            icon = Icons.Rounded.RecordVoiceOver,
            selected = selected == AudioPanelTab.AUTO_REACT,
            onClick = { onSelect(AudioPanelTab.AUTO_REACT) },
            modifier = Modifier
                .weight(1f)
                .testTag("audio_tab_autoreact")
        )
    }
}

@Composable
private fun AudioTabChip(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) StudioSurfaceHigh else Color.Transparent,
        label = "tab_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab_fg"
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) ElectricCyan.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun MicrophoneSettingsTab(
    uiState: StudioUiState,
    onUpdateSettings: ((AudioSettingsState) -> AudioSettingsState) -> Unit,
    onSelectInputDevice: (Int) -> Unit
) {
    val settings = uiState.audioSettings

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Connected Microphone Devices
        SectionLabel(text = stringResource(R.string.audio_input_device))
        DeviceChipCarousel(
            devices = uiState.inputDevices,
            selectedId = settings.selectedInputDeviceId ?: uiState.inputDevices.firstOrNull()?.id,
            onSelect = onSelectInputDevice,
            isInput = true
        )

        // Mic Gain Slider Card
        StudioSliderCard(
            title = stringResource(R.string.audio_mic_gain),
            valueLabel = "${(settings.micGain * 100).toInt()}%",
            value = settings.micGain,
            valueRange = 0.25f..2.0f,
            accentColor = ElectricCyan,
            onValueChange = { gain -> onUpdateSettings { it.copy(micGain = gain) } },
            testTag = "mic_gain_slider"
        )

        // Hardware DSP Switches
        StudioToggleCard(
            title = stringResource(R.string.audio_noise_suppression),
            subtitle = if (uiState.noiseSuppressorSupported) {
                stringResource(R.string.audio_noise_suppression_desc)
            } else {
                "Processamento por software · Hardware DSP indisponível"
            },
            checked = settings.noiseSuppression,
            accentColor = ElectricCyan,
            onCheckedChange = { checked -> onUpdateSettings { it.copy(noiseSuppression = checked) } },
            testTag = "noise_suppression_switch"
        )

        StudioToggleCard(
            title = stringResource(R.string.audio_echo_cancel),
            subtitle = stringResource(R.string.audio_echo_cancel_desc),
            checked = settings.echoCancellation,
            accentColor = ElectricCyan,
            onCheckedChange = { checked -> onUpdateSettings { it.copy(echoCancellation = checked) } },
            testTag = "echo_cancel_switch"
        )

        StudioToggleCard(
            title = stringResource(R.string.audio_agc),
            subtitle = stringResource(R.string.audio_agc_desc),
            checked = settings.autoGainControl,
            accentColor = ElectricCyan,
            onCheckedChange = { checked -> onUpdateSettings { it.copy(autoGainControl = checked) } },
            testTag = "agc_switch"
        )
    }
}

@Composable
private fun HeadphoneSettingsTab(
    uiState: StudioUiState,
    onUpdateSettings: ((AudioSettingsState) -> AudioSettingsState) -> Unit,
    onSelectOutputDevice: (Int) -> Unit
) {
    val settings = uiState.audioSettings

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Headphone Detection Hero Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (uiState.hasHeadphonesConnected) {
                        ElectricCyan.copy(alpha = 0.14f)
                    } else {
                        SignalAmber.copy(alpha = 0.14f)
                    }
                )
                .border(
                    1.dp,
                    if (uiState.hasHeadphonesConnected) ElectricCyan.copy(alpha = 0.45f) else SignalAmber.copy(alpha = 0.45f),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (uiState.hasHeadphonesConnected) Icons.Rounded.Headphones else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (uiState.hasHeadphonesConnected) ElectricCyan else SignalAmber,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.hasHeadphonesConnected) {
                        "${stringResource(R.string.audio_headphones_connected)} · ${uiState.activeOutputName}"
                    } else {
                        stringResource(R.string.audio_speaker_warning)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (uiState.hasHeadphonesConnected) {
                        "Monitoramento vocal em tempo real liberado sem eco."
                    } else {
                        "Com fone de ouvido, o áudio do vídeo superior não vaza no microfone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Connected Output Devices
        SectionLabel(text = stringResource(R.string.audio_output_device))
        DeviceChipCarousel(
            devices = uiState.outputDevices,
            selectedId = settings.selectedOutputDeviceId ?: uiState.outputDevices.firstOrNull()?.id,
            onSelect = onSelectOutputDevice,
            isInput = false
        )

        // Headphone Sidetone Monitoring Toggle (Protected against speaker feedback!)
        StudioToggleCard(
            title = stringResource(R.string.audio_monitoring_title),
            subtitle = if (uiState.hasHeadphonesConnected) {
                stringResource(R.string.audio_monitoring_desc)
            } else {
                stringResource(R.string.audio_monitoring_locked)
            },
            checked = settings.headphoneMonitoringEnabled && uiState.hasHeadphonesConnected,
            enabled = uiState.hasHeadphonesConnected,
            accentColor = ElectricCyan,
            leadingIcon = Icons.Rounded.Hearing,
            onCheckedChange = { checked ->
                onUpdateSettings { it.copy(headphoneMonitoringEnabled = checked) }
            },
            testTag = "headphone_monitor_switch"
        )

        if (settings.headphoneMonitoringEnabled && uiState.hasHeadphonesConnected) {
            StudioSliderCard(
                title = stringResource(R.string.audio_monitor_volume),
                valueLabel = "${(settings.monitorVolume * 100).toInt()}%",
                value = settings.monitorVolume,
                valueRange = 0.05f..1.0f,
                accentColor = ElectricCyan,
                onValueChange = { vol -> onUpdateSettings { it.copy(monitorVolume = vol) } },
                testTag = "monitor_volume_slider"
            )
        }

        // Video Volume in Headphones
        StudioSliderCard(
            title = stringResource(R.string.audio_video_volume),
            valueLabel = "${(settings.videoVolume * 100).toInt()}%",
            value = settings.videoVolume,
            valueRange = 0f..1.0f,
            accentColor = StudioLavender,
            onValueChange = { vol -> onUpdateSettings { it.copy(videoVolume = vol) } },
            testTag = "video_volume_slider"
        )

        // Mix Balance Slider (Video <-> Voice)
        StudioSliderCard(
            title = stringResource(R.string.audio_mix_balance),
            valueLabel = when {
                settings.mixBalance < 0.4f -> "Mais Vídeo"
                settings.mixBalance > 0.6f -> "Mais Voz"
                else -> "Equilibrado"
            },
            value = settings.mixBalance,
            valueRange = 0f..1.0f,
            accentColor = ElectricCyan,
            onValueChange = { bal -> onUpdateSettings { it.copy(mixBalance = bal) } },
            testTag = "mix_balance_slider"
        )

        // Equalizer Acoustic Profile Selector
        SectionLabel(text = stringResource(R.string.audio_eq_profile))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeadphoneEqProfile.entries.forEach { profile ->
                val selected = settings.eqProfile == profile
                Column(
                    modifier = Modifier
                        .width(128.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) ElectricCyan.copy(alpha = 0.16f) else StudioSurfaceElevated)
                        .border(
                            1.dp,
                            if (selected) ElectricCyan else StudioOutlineSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onUpdateSettings { it.copy(eqProfile = profile) } }
                        .padding(12.dp)
                        .testTag("eq_profile_${profile.name.lowercase()}"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = null,
                        tint = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = profile.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoReactSettingsTab(
    uiState: StudioUiState,
    onUpdateSettings: ((AudioSettingsState) -> AudioSettingsState) -> Unit
) {
    val settings = uiState.audioSettings

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioToggleCard(
            title = stringResource(R.string.audio_auto_react_title),
            subtitle = stringResource(R.string.audio_auto_react_desc),
            checked = settings.autoReactEnabled,
            accentColor = CoralCrimson,
            leadingIcon = Icons.Rounded.RecordVoiceOver,
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoReactEnabled = enabled) } },
            testTag = "auto_react_master_switch"
        )

        // Voice Threshold Slider (-48 dB to -6 dB)
        StudioSliderCard(
            title = stringResource(R.string.audio_threshold_label),
            valueLabel = formatDb(settings.voiceThresholdDb),
            value = settings.voiceThresholdDb,
            valueRange = -48f..-6f,
            accentColor = SignalAmber,
            onValueChange = { db -> onUpdateSettings { it.copy(voiceThresholdDb = db) } },
            testTag = "voice_threshold_slider"
        )

        // Reaction Behavior Cards
        SectionLabel(text = stringResource(R.string.audio_react_mode_label))
        AutoReactMode.entries.forEach { mode ->
            val selected = settings.autoReactMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) CoralCrimson.copy(alpha = 0.15f) else StudioSurfaceElevated)
                    .border(
                        1.dp,
                        if (selected) CoralCrimson else StudioOutlineSubtle,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onUpdateSettings { it.copy(autoReactMode = mode) } }
                    .padding(14.dp)
                    .testTag("react_mode_${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) CoralCrimson else StudioOutline,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = mode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (settings.autoReactMode == AutoReactMode.DUCK_VIDEO) {
            StudioSliderCard(
                title = stringResource(R.string.audio_duck_level),
                valueLabel = "${(settings.duckVolumeRatio * 100).toInt()}%",
                value = settings.duckVolumeRatio,
                valueRange = 0.05f..0.70f,
                accentColor = CoralCrimson,
                onValueChange = { ratio -> onUpdateSettings { it.copy(duckVolumeRatio = ratio) } },
                testTag = "duck_level_slider"
            )
        }

        if (settings.autoReactMode == AutoReactMode.AUTO_PAUSE) {
            StudioSliderCard(
                title = stringResource(R.string.audio_resume_delay),
                valueLabel = String.format(Locale.US, "%.1fs", settings.autoResumeDelaySec),
                value = settings.autoResumeDelaySec,
                valueRange = 0.8f..4.5f,
                accentColor = CoralCrimson,
                onValueChange = { sec -> onUpdateSettings { it.copy(autoResumeDelaySec = sec) } },
                testTag = "auto_resume_delay_slider"
            )
        }
    }
}

@Composable
private fun DeviceChipCarousel(
    devices: List<AudioDeviceItem>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    isInput: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        devices.forEach { device ->
            val selected = device.id == selectedId
            val icon = when {
                device.isBluetooth -> Icons.Rounded.BluetoothAudio
                device.isUsb -> Icons.Rounded.Usb
                device.isHeadphone -> Icons.Rounded.Headphones
                isInput -> Icons.Rounded.Mic
                else -> Icons.Rounded.Speaker
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) ElectricCyan.copy(alpha = 0.16f) else StudioSurfaceElevated)
                    .border(
                        1.dp,
                        if (selected) ElectricCyan else StudioOutlineSubtle,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(device.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = device.typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = StudioMonoStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun StudioSliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSurfaceElevated)
            .border(1.dp, StudioOutlineSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueLabel,
                style = StudioMonoStyle,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioOutline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag(testTag)
        )
    }
}

@Composable
private fun StudioToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSurfaceElevated)
            .border(
                1.dp,
                if (checked && enabled) accentColor.copy(alpha = 0.4f) else StudioOutlineSubtle,
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (checked && enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StudioVoid,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = StudioSurfaceHigh
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

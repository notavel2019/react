package com.example.model

enum class VideoAspectPreset(val label: String) {
    FILL("Preencher"),
    FIT("Ajustar"),
    RATIO_9_16("9:16"),
    RATIO_16_9("16:9"),
    RATIO_1_1("1:1"),
    RATIO_4_3("4:3")
}

enum class VideoSourceType {
    LOCAL_VIDEO,
    DIRECT_STREAM,
    INSTAGRAM_EMBED
}

enum class SplitRatioPreset(val topWeight: Float, val label: String) {
    BALANCED(0.50f, "50 / 50"),
    VIDEO_FOCUS(0.60f, "60 / 40"),
    CAMERA_FOCUS(0.40f, "40 / 60");

    fun next(): SplitRatioPreset = when (this) {
        BALANCED -> VIDEO_FOCUS
        VIDEO_FOCUS -> CAMERA_FOCUS
        CAMERA_FOCUS -> BALANCED
    }
}

enum class ReactLayoutMode(val label: String, val exportLabel: String) {
    SPLIT_SCREEN("Dividida", "Tela Dividida 9:16"),
    PIP_SQUARE("Quadrado", "Fundo 9:16 + Quadrado"),
    PIP_CIRCLE("Redondo", "Fundo 9:16 + Redondo"),
    PIP_CUTOUT("Recorte", "Fundo 9:16 + Recorte de Fundo")
}

data class PipCameraOverlayState(
    val centerXFraction: Float = 0.74f,
    val centerYFraction: Float = 0.56f,
    val widthFraction: Float = 0.36f
) {
    fun heightToWidthRatio(mode: ReactLayoutMode): Float = when (mode) {
        ReactLayoutMode.PIP_CUTOUT -> 1.34f
        else -> 1.0f
    }
}

enum class AutoReactMode(val title: String, val subtitle: String) {
    DUCK_VIDEO(
        title = "Abaixar vídeo (Auto-Duck)",
        subtitle = "Reduz suavemente o som do vídeo de cima enquanto você fala"
    ),
    AUTO_PAUSE(
        title = "Pausar vídeo ao falar",
        subtitle = "Pausa o vídeo automaticamente para você comentar e retoma depois"
    ),
    HIGHLIGHT_ONLY(
        title = "Destaque e marcador",
        subtitle = "Acende a borda de reação e salva o instante na linha do tempo"
    )
}

enum class HeadphoneEqProfile(val label: String, val description: String) {
    STUDIO_FLAT("Estúdio", "Resposta neutra e equilibrada"),
    CLEAR_VOICE("Voz Clara", "Realce de médios e presença vocal"),
    DEEP_BASS("Graves", "Mais corpo para músicas e trailers"),
    PODCAST("Podcast", "Foco em diálogos sem fadiga")
}

enum class AudioPanelTab {
    MICROPHONE,
    HEADPHONES,
    AUTO_REACT
}

enum class ActiveSheet {
    NONE,
    AUDIO_CONSOLE,
    TAKES_HISTORY,
    VIDEO_URL_DIALOG,
    EXPORT_SHARE,
    SUBSCRIPTION
}

sealed class ExportStatus {
    data object Idle : ExportStatus()
    data class Combining(val progress: Float) : ExportStatus()
    data class CombinedReady(val combinedUri: String, val displayName: String) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}

enum class SubscriptionAccessState {
    CHECKING,
    TRIAL_ACTIVE,
    PRO_SUBSCRIBED,
    PENDING_PAYMENT,
    TRIAL_EXPIRED
}

data class SubscriptionUiState(
    val accessState: SubscriptionAccessState = SubscriptionAccessState.CHECKING,
    val trialRemainingMs: Long = 4L * 24L * 60L * 60L * 1000L,
    val trialEndsAtMs: Long = 0L,
    val isClockTampered: Boolean = false,
    val formattedAnnualPrice: String = "R$ 20,00",
    val hasPlayFreeTrialOffer: Boolean = false,
    val isBillingLoading: Boolean = true,
    val isBillingUnavailable: Boolean = false,
    val billingMessage: String? = null
) {
    val isPro: Boolean
        get() = accessState == SubscriptionAccessState.PRO_SUBSCRIBED

    val isLocked: Boolean
        get() = accessState == SubscriptionAccessState.TRIAL_EXPIRED ||
            accessState == SubscriptionAccessState.PENDING_PAYMENT

    val remainingDaysRoundedUp: Int
        get() = ((trialRemainingMs + 86_399_999L) / 86_400_000L).toInt().coerceIn(0, 4)

    val badgeLabel: String
        get() = when (accessState) {
            SubscriptionAccessState.PRO_SUBSCRIBED -> "PRO"
            SubscriptionAccessState.TRIAL_ACTIVE -> {
                val totalHours = (trialRemainingMs / 3_600_000L).coerceAtLeast(1L)
                val days = totalHours / 24L
                val hours = totalHours % 24L
                if (days > 0L) "TESTE · ${days}d" else "TESTE · ${hours}h"
            }
            SubscriptionAccessState.PENDING_PAYMENT -> "PENDENTE"
            SubscriptionAccessState.TRIAL_EXPIRED -> "ASSINAR"
            SubscriptionAccessState.CHECKING -> "PRO"
        }
}

data class VideoFramingState(
    val scale: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val aspectPreset: VideoAspectPreset = VideoAspectPreset.FILL,
    val showRuleOfThirds: Boolean = true
)

data class AudioDeviceItem(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isHeadphone: Boolean = false,
    val isBluetooth: Boolean = false,
    val isUsb: Boolean = false,
    val isBuiltIn: Boolean = false
)

data class AudioSettingsState(
    val micGain: Float = 1.0f,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val autoGainControl: Boolean = true,
    val autoReactEnabled: Boolean = true,
    val autoReactMode: AutoReactMode = AutoReactMode.DUCK_VIDEO,
    val voiceThresholdDb: Float = -26f,
    val duckVolumeRatio: Float = 0.22f,
    val autoResumeDelaySec: Float = 1.8f,
    val headphoneMonitoringEnabled: Boolean = false,
    val monitorVolume: Float = 0.65f,
    val videoVolume: Float = 0.85f,
    val mixBalance: Float = 0.5f,
    val eqProfile: HeadphoneEqProfile = HeadphoneEqProfile.CLEAR_VOICE,
    val selectedInputDeviceId: Int? = null,
    val selectedOutputDeviceId: Int? = null
)

data class ReactionMarker(
    val id: String,
    val videoPositionMs: Long,
    val recordingElapsedMs: Long,
    val peakDb: Float,
    val label: String,
    val isAutoDetected: Boolean
)

data class SourcePlaybackSegment(
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val durationMs: Long,
    val isPaused: Boolean = false
)

data class RecordedTake(
    val id: String,
    val uriString: String,
    val displayName: String,
    val createdAtMs: Long,
    val durationMs: Long,
    val markerCount: Int,
    val isCombined: Boolean = false
)

data class StudioUiState(
    val sourceVideoUri: String? = null,
    val sourceOriginalLink: String? = null,
    val sourceVideoType: VideoSourceType = VideoSourceType.LOCAL_VIDEO,
    val sourceVideoTitle: String = "",
    val isResolvingLink: Boolean = false,
    val videoNaturalWidth: Int = 16,
    val videoNaturalHeight: Int = 9,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isVideoMuted: Boolean = false,
    val isFramingMode: Boolean = false,
    val framing: VideoFramingState = VideoFramingState(),
    val splitRatio: SplitRatioPreset = SplitRatioPreset.BALANCED,
    val layoutMode: ReactLayoutMode = ReactLayoutMode.SPLIT_SCREEN,
    val pipOverlay: PipCameraOverlayState = PipCameraOverlayState(),
    val isFrontCamera: Boolean = true,
    val isCameraMirrored: Boolean = true,
    val cameraZoomRatio: Float = 1.0f,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordingSourceStartMs: Long = 0L,
    val lastRecordedTake: RecordedTake? = null,
    val exportStatus: ExportStatus = ExportStatus.Idle,
    val isSpeaking: Boolean = false,
    val isManualReacting: Boolean = false,
    val isAutoDuckingActive: Boolean = false,
    val isAutoPausedByVoice: Boolean = false,
    val currentMicDb: Float = -60f,
    val waveformBars: List<Float> = List(28) { 0.05f },
    val audioSettings: AudioSettingsState = AudioSettingsState(),
    val inputDevices: List<AudioDeviceItem> = emptyList(),
    val outputDevices: List<AudioDeviceItem> = emptyList(),
    val hasHeadphonesConnected: Boolean = false,
    val activeInputName: String = "Microfone interno",
    val activeOutputName: String = "Alto-falante",
    val noiseSuppressorSupported: Boolean = true,
    val echoCancelerSupported: Boolean = true,
    val agcSupported: Boolean = true,
    val reactionMarkers: List<ReactionMarker> = emptyList(),
    val recordedTakes: List<RecordedTake> = emptyList(),
    val subscription: SubscriptionUiState = SubscriptionUiState(),
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val activeAudioTab: AudioPanelTab = AudioPanelTab.MICROPHONE,
    val statusToast: String? = null
) {
    val isFullScreenOverlayMode: Boolean
        get() = layoutMode != ReactLayoutMode.SPLIT_SCREEN

    val isReactingNow: Boolean
        get() = isManualReacting || (audioSettings.autoReactEnabled && isSpeaking)

    val canCombineWithSourceVideo: Boolean
        get() = sourceVideoUri != null &&
            sourceVideoType != VideoSourceType.INSTAGRAM_EMBED &&
            lastRecordedTake != null

    val effectiveVideoVolume: Float
        get() {
            if (isVideoMuted) return 0f
            val balanceScale = (1.35f - audioSettings.mixBalance * 0.7f).coerceIn(0.35f, 1.0f)
            val base = (audioSettings.videoVolume * balanceScale).coerceIn(0f, 1f)
            return if (isAutoDuckingActive && audioSettings.autoReactMode == AutoReactMode.DUCK_VIDEO) {
                (base * audioSettings.duckVolumeRatio).coerceIn(0f, 1f)
            } else {
                base
            }
        }
}

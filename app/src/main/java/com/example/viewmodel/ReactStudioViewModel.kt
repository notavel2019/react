package com.example.viewmodel

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.StudioAudioEngine
import com.example.data.AudioPreferencesRepository
import com.example.media.InstagramLinkResolver
import com.example.media.SplitScreenVideoExporter
import com.example.model.ActiveSheet
import com.example.model.AppUpdateStatus
import com.example.model.AudioPanelTab
import com.example.model.AudioSettingsState
import com.example.model.AutoReactMode
import com.example.model.ExportStatus
import com.example.model.HeadphoneEqProfile
import com.example.model.ReactionMarker
import com.example.model.RecordedTake
import com.example.model.SplitRatioPreset
import com.example.model.StudioUiState
import com.example.model.VideoAspectPreset
import com.example.model.VideoSourceType
import com.example.update.GitHubUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed class PlayerCommand {
    data object Play : PlayerCommand()
    data object Pause : PlayerCommand()
    data class SeekBy(val deltaMs: Long) : PlayerCommand()
    data class SeekTo(val positionMs: Long) : PlayerCommand()
    data class SetSpeed(val speed: Float) : PlayerCommand()
}

class ReactStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = AudioPreferencesRepository(application)
    private val splitExporter = SplitScreenVideoExporter(application)
    val audioEngine = StudioAudioEngine(application)

    private val _uiState = MutableStateFlow(
        StudioUiState(
            noiseSuppressorSupported = audioEngine.noiseSuppressorSupported,
            echoCancelerSupported = audioEngine.echoCancelerSupported,
            agcSupported = audioEngine.agcSupported
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _playerCommands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 16)
    val playerCommands: SharedFlow<PlayerCommand> = _playerCommands.asSharedFlow()

    private var recordingTimerJob: Job? = null
    private var autoResumeJob: Job? = null
    private var exportJob: Job? = null
    private var lastAutoMarkerAtMs = 0L
    private var wasPlayingBeforeAutoPause = false
    private var lastSpeakingState = false

    init {
        loadInitialPreferences()
        observeAudioTelemetry()
        refreshRecordedTakes()
    }

    private fun loadInitialPreferences() {
        viewModelScope.launch {
            val savedAudio = prefsRepo.audioSettingsFlow.first()
            val savedSplit = prefsRepo.splitRatioFlow.first()
            val savedRepo = prefsRepo.githubRepoFlow.first()
            audioEngine.updateSettings(savedAudio)
            _uiState.update {
                it.copy(
                    audioSettings = savedAudio,
                    splitRatio = savedSplit,
                    githubRepo = savedRepo
                )
            }
            if (savedRepo.isNotBlank()) {
                checkForAppUpdates(silent = true)
            }
        }
    }

    private fun observeAudioTelemetry() {
        viewModelScope.launch {
            audioEngine.telemetry.collect { telemetry ->
                val currentBars = _uiState.value.waveformBars
                val nextBars = currentBars.drop(1) + telemetry.normalizedLevel.coerceIn(0.05f, 1f)

                _uiState.update { state ->
                    val autoMonitorAllowed = telemetry.hasHeadphones && state.audioSettings.headphoneMonitoringEnabled
                    state.copy(
                        currentMicDb = telemetry.currentDb,
                        waveformBars = nextBars,
                        isSpeaking = telemetry.isSpeaking,
                        inputDevices = telemetry.inputDevices,
                        outputDevices = telemetry.outputDevices,
                        hasHeadphonesConnected = telemetry.hasHeadphones,
                        activeInputName = telemetry.activeInputName,
                        activeOutputName = telemetry.activeOutputName,
                        audioSettings = if (!telemetry.hasHeadphones && state.audioSettings.headphoneMonitoringEnabled) {
                            state.audioSettings.copy(headphoneMonitoringEnabled = autoMonitorAllowed)
                        } else {
                            state.audioSettings
                        }
                    )
                }

                handleVoiceActivityTransition(telemetry.isSpeaking, telemetry.currentDb)
            }
        }
    }

    private fun handleVoiceActivityTransition(isSpeaking: Boolean, currentDb: Float) {
        if (isSpeaking == lastSpeakingState) return
        lastSpeakingState = isSpeaking

        val state = _uiState.value
        val settings = state.audioSettings
        if (!settings.autoReactEnabled) {
            if (state.isAutoDuckingActive || state.isAutoPausedByVoice) {
                _uiState.update { it.copy(isAutoDuckingActive = false, isAutoPausedByVoice = false) }
            }
            return
        }

        if (isSpeaking) {
            autoResumeJob?.cancel()
            val now = System.currentTimeMillis()
            if (state.sourceVideoUri != null && now - lastAutoMarkerAtMs >= 4500L) {
                lastAutoMarkerAtMs = now
                addReactionMarker(isAuto = true, peakDb = currentDb)
            }

            if (state.sourceVideoType == VideoSourceType.INSTAGRAM_EMBED) return

            when (settings.autoReactMode) {
                AutoReactMode.DUCK_VIDEO -> {
                    _uiState.update { it.copy(isAutoDuckingActive = true) }
                }
                AutoReactMode.AUTO_PAUSE -> {
                    if (state.isPlaying && state.sourceVideoUri != null) {
                        wasPlayingBeforeAutoPause = true
                        _uiState.update { it.copy(isAutoPausedByVoice = true) }
                        _playerCommands.tryEmit(PlayerCommand.Pause)
                    }
                }
                AutoReactMode.HIGHLIGHT_ONLY -> Unit
            }
        } else {
            if (state.isAutoDuckingActive) {
                _uiState.update { it.copy(isAutoDuckingActive = false) }
            }
            if (wasPlayingBeforeAutoPause && state.isAutoPausedByVoice) {
                val delayMs = (settings.autoResumeDelaySec * 1000L).toLong().coerceIn(700L, 5000L)
                autoResumeJob?.cancel()
                autoResumeJob = viewModelScope.launch {
                    delay(delayMs)
                    if (!_uiState.value.isSpeaking && !_uiState.value.isManualReacting) {
                        wasPlayingBeforeAutoPause = false
                        _uiState.update { it.copy(isAutoPausedByVoice = false) }
                        _playerCommands.tryEmit(PlayerCommand.Play)
                    }
                }
            }
        }
    }

    fun startAudioMonitoring() {
        audioEngine.startMicrophoneMonitor()
    }

    fun stopAudioMonitoring() {
        audioEngine.stopMicrophoneMonitor()
    }

    fun attachExoPlayerAudioSession(sessionId: Int) {
        audioEngine.attachPlayerEqualizer(sessionId, _uiState.value.audioSettings.eqProfile)
    }

    // --- Source Video & Instagram / Reels Link Loader ---

    fun loadSourceVideo(uri: Uri) {
        val title = resolveDisplayName(uri)
        _uiState.update {
            it.copy(
                sourceVideoUri = uri.toString(),
                sourceOriginalLink = null,
                sourceVideoType = VideoSourceType.LOCAL_VIDEO,
                sourceVideoTitle = title,
                currentPositionMs = 0L,
                durationMs = 0L,
                isAutoPausedByVoice = false,
                isManualReacting = false,
                framing = it.framing.copy(
                    scale = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                    aspectPreset = VideoAspectPreset.FIT
                )
            )
        }
    }

    fun loadSourceVideoUrl(rawUrl: String) {
        if (rawUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingLink = true) }
            val result = InstagramLinkResolver.resolve(rawUrl)
            result.fold(
                onSuccess = { resolved ->
                    val defaultPreset = if (resolved.sourceType == VideoSourceType.INSTAGRAM_EMBED) {
                        VideoAspectPreset.RATIO_9_16
                    } else {
                        VideoAspectPreset.FIT
                    }
                    _uiState.update {
                        it.copy(
                            isResolvingLink = false,
                            sourceVideoUri = resolved.playableUri,
                            sourceOriginalLink = resolved.originalLink,
                            sourceVideoType = resolved.sourceType,
                            sourceVideoTitle = resolved.title,
                            currentPositionMs = 0L,
                            durationMs = 0L,
                            activeSheet = ActiveSheet.NONE,
                            isAutoPausedByVoice = false,
                            isManualReacting = false,
                            framing = it.framing.copy(
                                scale = 1f,
                                offsetX = 0f,
                                offsetY = 0f,
                                aspectPreset = defaultPreset
                            ),
                            statusToast = if (resolved.sourceType == VideoSourceType.INSTAGRAM_EMBED) {
                                "Reel carregado · Use 'Enquadrar' para ajustar no palco superior"
                            } else {
                                "Vídeo carregado no palco superior"
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isResolvingLink = false,
                            statusToast = err.message ?: "Link inválido"
                        )
                    }
                }
            )
        }
    }

    fun onPlaybackStateUpdated(
        isPlaying: Boolean,
        isBuffering: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        _uiState.update {
            it.copy(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPositionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L)
            )
        }
    }

    fun onVideoSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _uiState.update { it.copy(videoNaturalWidth = width, videoNaturalHeight = height) }
        }
    }

    fun togglePlayPause() {
        autoResumeJob?.cancel()
        wasPlayingBeforeAutoPause = false
        _uiState.update { it.copy(isAutoPausedByVoice = false) }
        if (_uiState.value.isPlaying) {
            _playerCommands.tryEmit(PlayerCommand.Pause)
        } else {
            _uiState.update { it.copy(isManualReacting = false) }
            _playerCommands.tryEmit(PlayerCommand.Play)
        }
    }

    fun seekBy(deltaMs: Long) {
        _playerCommands.tryEmit(PlayerCommand.SeekBy(deltaMs))
    }

    fun seekTo(positionMs: Long) {
        _playerCommands.tryEmit(PlayerCommand.SeekTo(positionMs.coerceAtLeast(0L)))
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIdx = speeds.indexOfFirst { it == _uiState.value.playbackSpeed }
        val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
        _uiState.update { it.copy(playbackSpeed = nextSpeed) }
        _playerCommands.tryEmit(PlayerCommand.SetSpeed(nextSpeed))
    }

    fun toggleVideoMute() {
        _uiState.update { it.copy(isVideoMuted = !it.isVideoMuted) }
    }

    // --- Video Framing Controls ---

    fun setFramingMode(enabled: Boolean) {
        _uiState.update { it.copy(isFramingMode = enabled) }
    }

    fun updateFramingTransform(zoomFactor: Float, panX: Float, panY: Float) {
        _uiState.update { state ->
            val newScale = (state.framing.scale * zoomFactor).coerceIn(1f, 3.5f)
            val maxOffset = (newScale - 1f) * 320f + 140f
            val newX = (state.framing.offsetX + panX).coerceIn(-maxOffset, maxOffset)
            val newY = (state.framing.offsetY + panY).coerceIn(-maxOffset, maxOffset)
            state.copy(
                framing = state.framing.copy(
                    scale = newScale,
                    offsetX = newX,
                    offsetY = newY
                )
            )
        }
    }

    fun setFramingScale(scale: Float) {
        _uiState.update { state ->
            val clamped = scale.coerceIn(1f, 3.5f)
            state.copy(framing = state.framing.copy(scale = clamped))
        }
    }

    fun setAspectPreset(preset: VideoAspectPreset) {
        _uiState.update { state ->
            state.copy(
                framing = state.framing.copy(
                    aspectPreset = preset,
                    scale = 1f,
                    offsetX = 0f,
                    offsetY = 0f
                )
            )
        }
    }

    fun toggleRuleOfThirds() {
        _uiState.update { state ->
            state.copy(framing = state.framing.copy(showRuleOfThirds = !state.framing.showRuleOfThirds))
        }
    }

    fun resetFraming() {
        _uiState.update { state ->
            state.copy(
                framing = state.framing.copy(
                    scale = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                    aspectPreset = if (state.sourceVideoType == VideoSourceType.INSTAGRAM_EMBED) {
                        VideoAspectPreset.RATIO_9_16
                    } else {
                        VideoAspectPreset.FIT
                    }
                )
            )
        }
    }

    // --- Split Layout & Camera ---

    fun cycleSplitRatio() {
        val next = _uiState.value.splitRatio.next()
        _uiState.update { it.copy(splitRatio = next) }
        viewModelScope.launch { prefsRepo.saveSplitRatio(next) }
    }

    fun toggleCameraLens() {
        _uiState.update {
            val nextFront = !it.isFrontCamera
            it.copy(
                isFrontCamera = nextFront,
                isCameraMirrored = nextFront,
                cameraZoomRatio = 1.0f
            )
        }
    }

    fun toggleCameraMirror() {
        _uiState.update { it.copy(isCameraMirrored = !it.isCameraMirrored) }
    }

    // --- Reaction, Recording & Combined Video Export ---

    fun toggleManualReaction() {
        val state = _uiState.value
        val enteringReaction = !state.isManualReacting
        _uiState.update { it.copy(isManualReacting = enteringReaction, isAutoPausedByVoice = false) }
        if (enteringReaction) {
            if (state.isPlaying) {
                _playerCommands.tryEmit(PlayerCommand.Pause)
            }
            addReactionMarker(isAuto = false, peakDb = state.currentMicDb)
        } else {
            if (state.sourceVideoUri != null && !state.isPlaying) {
                _playerCommands.tryEmit(PlayerCommand.Play)
            }
        }
    }

    fun addReactionMarker(isAuto: Boolean = false, peakDb: Float = _uiState.value.currentMicDb) {
        val state = _uiState.value
        val count = state.reactionMarkers.size + 1
        val marker = ReactionMarker(
            id = UUID.randomUUID().toString(),
            videoPositionMs = state.currentPositionMs,
            recordingElapsedMs = state.recordingElapsedMs,
            peakDb = peakDb,
            label = if (isAuto) "Reação por voz #$count" else "Reação #$count",
            isAutoDetected = isAuto
        )
        _uiState.update { it.copy(reactionMarkers = (listOf(marker) + it.reactionMarkers).take(30)) }
    }

    fun onRecordingStarted() {
        recordingTimerJob?.cancel()
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingElapsedMs = 0L,
                recordingSourceStartMs = it.currentPositionMs,
                exportStatus = ExportStatus.Idle,
                statusToast = "Gravação iniciada"
            )
        }
        val startAt = System.currentTimeMillis()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(250L)
                _uiState.update { it.copy(recordingElapsedMs = System.currentTimeMillis() - startAt) }
            }
        }
    }

    fun onRecordingFinalized(savedUri: Uri?, errorMessage: String?) {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        val elapsed = _uiState.value.recordingElapsedMs
        val markerCount = _uiState.value.reactionMarkers.size

        if (savedUri != null) {
            val newTake = RecordedTake(
                id = UUID.randomUUID().toString(),
                uriString = savedUri.toString(),
                displayName = resolveDisplayName(savedUri),
                createdAtMs = System.currentTimeMillis(),
                durationMs = elapsed,
                markerCount = markerCount,
                isCombined = false
            )
            _uiState.update {
                it.copy(
                    isRecording = false,
                    recordingElapsedMs = 0L,
                    lastRecordedTake = newTake,
                    exportStatus = ExportStatus.Idle,
                    activeSheet = ActiveSheet.EXPORT_SHARE,
                    recordedTakes = listOf(newTake) + it.recordedTakes.filterNot { t -> t.uriString == newTake.uriString },
                    statusToast = "Reação gravada! Combine ou compartilhe agora."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isRecording = false,
                    recordingElapsedMs = 0L,
                    statusToast = errorMessage ?: "Gravação encerrada"
                )
            }
        }
    }

    fun openExportShareForTake(take: RecordedTake) {
        _uiState.update {
            it.copy(
                lastRecordedTake = take,
                exportStatus = if (take.isCombined) {
                    ExportStatus.CombinedReady(take.uriString, take.displayName)
                } else {
                    ExportStatus.Idle
                },
                activeSheet = ActiveSheet.EXPORT_SHARE
            )
        }
    }

    fun combineWithMainVideo() {
        val state = _uiState.value
        val sourceUri = state.sourceVideoUri ?: run {
            _uiState.update { it.copy(exportStatus = ExportStatus.Error("Carregue um vídeo principal primeiro.")) }
            return
        }
        if (state.sourceVideoType == VideoSourceType.INSTAGRAM_EMBED) {
            _uiState.update {
                it.copy(
                    exportStatus = ExportStatus.Error(
                        "Reels em modo incorporado não liberam o arquivo MP4 original para junção automática. Compartilhe sua reação ou carregue um vídeo da galeria/MP4."
                    )
                )
            }
            return
        }
        val take = state.lastRecordedTake ?: run {
            _uiState.update { it.copy(exportStatus = ExportStatus.Error("Nenhuma gravação selecionada.")) }
            return
        }

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(exportStatus = ExportStatus.Combining(0.02f)) }
            val result = splitExporter.exportSplitScreen(
                sourceVideoUri = sourceUri,
                reactionVideoUri = take.uriString,
                sourceStartMs = state.recordingSourceStartMs,
                reactionDurationMs = take.durationMs.coerceAtLeast(2_000L),
                splitRatio = state.splitRatio,
                onProgress = { progress ->
                    _uiState.update { it.copy(exportStatus = ExportStatus.Combining(progress)) }
                }
            )
            result.fold(
                onSuccess = { output ->
                    val combinedTake = RecordedTake(
                        id = UUID.randomUUID().toString(),
                        uriString = output.mediaStoreUri.toString(),
                        displayName = output.displayName,
                        createdAtMs = System.currentTimeMillis(),
                        durationMs = take.durationMs,
                        markerCount = take.markerCount,
                        isCombined = true
                    )
                    _uiState.update {
                        it.copy(
                            lastRecordedTake = combinedTake,
                            exportStatus = ExportStatus.CombinedReady(
                                combinedUri = output.mediaStoreUri.toString(),
                                displayName = output.displayName
                            ),
                            recordedTakes = listOf(combinedTake) + it.recordedTakes.filterNot { t ->
                                t.uriString == combinedTake.uriString
                            },
                            statusToast = "Vídeo combinado salvo na Galeria!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            exportStatus = ExportStatus.Error(
                                err.message ?: "Não foi possível combinar os vídeos neste dispositivo."
                            )
                        )
                    }
                }
            )
        }
    }

    fun buildDirectShareLink(): String {
        val state = _uiState.value
        val source = state.sourceOriginalLink
        if (!source.isNullOrBlank()) {
            val firstMarkerSec = state.reactionMarkers.firstOrNull()?.let { it.videoPositionMs / 1000L }
            return if (firstMarkerSec != null && firstMarkerSec > 0L) {
                "$source?t=$firstMarkerSec"
            } else {
                source
            }
        }
        val repo = state.githubRepo
        if (repo.isNotBlank()) {
            return "https://github.com/$repo/releases/latest"
        }
        return state.lastRecordedTake?.uriString ?: "https://reactstudio.app"
    }

    fun refreshRecordedTakes() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<RecordedTake>()
            runCatching {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED
                )
                val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("ReactStudio_%")
                val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

                getApplication<Application>().contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    while (cursor.moveToNext() && list.size < 30) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "ReactStudio.mp4"
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        list.add(
                            RecordedTake(
                                id = id.toString(),
                                uriString = contentUri.toString(),
                                displayName = name,
                                createdAtMs = cursor.getLong(dateCol) * 1000L,
                                durationMs = cursor.getLong(durCol),
                                markerCount = 0,
                                isCombined = name.contains("Combined", ignoreCase = true)
                            )
                        )
                    }
                }
            }
            if (list.isNotEmpty()) {
                _uiState.update { it.copy(recordedTakes = list) }
            }
        }
    }

    // --- GitHub In-App Updates ---

    fun saveGithubRepoAndCheck(rawInput: String) {
        val normalized = GitHubUpdateRepository.normalizeRepoSlug(rawInput)
        if (normalized == null) {
            _uiState.update {
                it.copy(
                    appUpdateStatus = AppUpdateStatus.Error(
                        "Formato inválido. Use usuario/repositorio (ex: meuusuario/react-studio)."
                    )
                )
            }
            return
        }
        _uiState.update { it.copy(githubRepo = normalized) }
        viewModelScope.launch {
            prefsRepo.saveGithubRepo(normalized)
            checkForAppUpdates(silent = false)
        }
    }

    fun checkForAppUpdates(silent: Boolean = false) {
        val repo = _uiState.value.githubRepo
        if (repo.isBlank()) {
            if (!silent) {
                _uiState.update {
                    it.copy(appUpdateStatus = AppUpdateStatus.Error("Informe seu repositório GitHub acima."))
                }
            }
            return
        }
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(appUpdateStatus = AppUpdateStatus.Checking) }
            }
            val currentVersion = BuildConfig.VERSION_NAME
            val result = GitHubUpdateRepository.fetchLatestRelease(repo)
            result.fold(
                onSuccess = { release ->
                    val newer = GitHubUpdateRepository.isRemoteNewer(currentVersion, release.versionName)
                    _uiState.update {
                        it.copy(
                            appUpdateStatus = if (newer) {
                                AppUpdateStatus.UpdateAvailable(currentVersion, release)
                            } else {
                                AppUpdateStatus.UpToDate(currentVersion, release.tagName)
                            }
                        )
                    }
                },
                onFailure = { err ->
                    if (!silent) {
                        _uiState.update {
                            it.copy(
                                appUpdateStatus = AppUpdateStatus.Error(
                                    err.message ?: "Não foi possível consultar o GitHub."
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    // --- Audio Settings ---

    fun updateAudioSettings(transform: (AudioSettingsState) -> AudioSettingsState) {
        val current = _uiState.value.audioSettings
        val updated = transform(current)
        _uiState.update { it.copy(audioSettings = updated) }
        audioEngine.updateSettings(updated)
        viewModelScope.launch { prefsRepo.saveAudioSettings(updated) }
    }

    fun selectInputDevice(deviceId: Int) {
        updateAudioSettings { it.copy(selectedInputDeviceId = deviceId) }
        audioEngine.refreshAudioDevices()
    }

    fun selectOutputDevice(deviceId: Int) {
        updateAudioSettings { it.copy(selectedOutputDeviceId = deviceId) }
        audioEngine.refreshAudioDevices()
    }

    // --- Sheets & Toasts ---

    fun openSheet(sheet: ActiveSheet, audioTab: AudioPanelTab? = null) {
        if (sheet == ActiveSheet.AUDIO_CONSOLE) {
            audioEngine.refreshAudioDevices()
        } else if (sheet == ActiveSheet.TAKES_HISTORY) {
            refreshRecordedTakes()
        }
        _uiState.update {
            it.copy(
                activeSheet = sheet,
                activeAudioTab = audioTab ?: it.activeAudioTab
            )
        }
    }

    fun selectAudioTab(tab: AudioPanelTab) {
        _uiState.update { it.copy(activeAudioTab = tab) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun clearStatusToast() {
        _uiState.update { it.copy(statusToast = null) }
    }

    fun showStatusToast(message: String) {
        _uiState.update { it.copy(statusToast = message) }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return runCatching {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Vídeo selecionado"
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        autoResumeJob?.cancel()
        exportJob?.cancel()
        audioEngine.release()
    }
}

package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.Equalizer
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.AudioDeviceItem
import com.example.model.AudioSettingsState
import com.example.model.HeadphoneEqProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioTelemetry(
    val currentDb: Float = -60f,
    val normalizedLevel: Float = 0f,
    val isSpeaking: Boolean = false,
    val inputDevices: List<AudioDeviceItem> = emptyList(),
    val outputDevices: List<AudioDeviceItem> = emptyList(),
    val hasHeadphones: Boolean = false,
    val activeInputName: String = "Microfone interno",
    val activeOutputName: String = "Alto-falante"
)

class StudioAudioEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _telemetry = MutableStateFlow(AudioTelemetry())
    val telemetry: StateFlow<AudioTelemetry> = _telemetry.asStateFlow()

    val noiseSuppressorSupported: Boolean = runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)
    val echoCancelerSupported: Boolean = runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)
    val agcSupported: Boolean = runCatching { AutomaticGainControl.isAvailable() }.getOrDefault(false)

    @Volatile
    private var currentSettings = AudioSettingsState()

    @Volatile
    private var preferredInputDevice: AudioDeviceInfo? = null

    @Volatile
    private var preferredOutputDevice: AudioDeviceInfo? = null

    private var monitorJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var monitorTrack: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var playerEqualizer: Equalizer? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioDevices()
        }
    }

    init {
        refreshAudioDevices()
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    fun updateSettings(settings: AudioSettingsState) {
        val deviceChanged = settings.selectedInputDeviceId != currentSettings.selectedInputDeviceId ||
            settings.selectedOutputDeviceId != currentSettings.selectedOutputDeviceId
        currentSettings = settings

        if (deviceChanged) {
            resolveSelectedDevices(settings.selectedInputDeviceId, settings.selectedOutputDeviceId)
        }

        runCatching { noiseSuppressor?.enabled = settings.noiseSuppression }
        runCatching { echoCanceler?.enabled = settings.echoCancellation }
        runCatching { automaticGainControl?.enabled = settings.autoGainControl }
        runCatching {
            val vol = if (_telemetry.value.hasHeadphones && settings.headphoneMonitoringEnabled) {
                settings.monitorVolume.coerceIn(0f, 1f)
            } else {
                0f
            }
            monitorTrack?.setVolume(vol)
        }
        applyEqProfile(settings.eqProfile)
    }

    fun attachPlayerEqualizer(audioSessionId: Int, profile: HeadphoneEqProfile) {
        if (audioSessionId == 0 || audioSessionId == AudioManager.AUDIO_SESSION_ID_GENERATE) return
        releasePlayerEqualizer()
        runCatching {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = true
            playerEqualizer = eq
            applyEqProfile(profile)
        }
    }

    private fun applyEqProfile(profile: HeadphoneEqProfile) {
        val eq = playerEqualizer ?: return
        runCatching {
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) return
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            fun clampLevel(mb: Int): Short = mb.coerceIn(minLevel, maxLevel).toShort()

            for (i in 0 until bands) {
                val freqHz = eq.getCenterFreq(i.toShort()) / 1000
                val targetMb = when (profile) {
                    HeadphoneEqProfile.STUDIO_FLAT -> 0
                    HeadphoneEqProfile.CLEAR_VOICE -> when {
                        freqHz < 200 -> -250
                        freqHz in 1000..4000 -> 450
                        freqHz > 6000 -> 150
                        else -> 100
                    }
                    HeadphoneEqProfile.DEEP_BASS -> when {
                        freqHz <= 180 -> 550
                        freqHz in 181..400 -> 250
                        freqHz > 4000 -> 150
                        else -> 0
                    }
                    HeadphoneEqProfile.PODCAST -> when {
                        freqHz < 160 -> -350
                        freqHz in 500..3000 -> 350
                        freqHz > 6000 -> -150
                        else -> 50
                    }
                }
                eq.setBandLevel(i.toShort(), clampLevel(targetMb))
            }
        }
    }

    fun refreshAudioDevices() {
        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val mappedInputs = rawInputs
            .filter { isUserRelevantInput(it.type) }
            .distinctBy { "${it.type}_${it.productName}" }
            .map { info ->
                AudioDeviceItem(
                    id = info.id,
                    name = formatDeviceName(info, isInput = true),
                    typeLabel = formatDeviceType(info.type),
                    isHeadphone = isHeadphoneType(info.type),
                    isBluetooth = isBluetoothType(info.type),
                    isUsb = isUsbType(info.type),
                    isBuiltIn = info.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                )
            }
            .ifEmpty {
                listOf(
                    AudioDeviceItem(
                        id = -1,
                        name = "Microfone interno",
                        typeLabel = "Integrado",
                        isBuiltIn = true
                    )
                )
            }

        val mappedOutputs = rawOutputs
            .filter { isUserRelevantOutput(it.type) }
            .distinctBy { "${it.type}_${it.productName}" }
            .map { info ->
                AudioDeviceItem(
                    id = info.id,
                    name = formatDeviceName(info, isInput = false),
                    typeLabel = formatDeviceType(info.type),
                    isHeadphone = isHeadphoneType(info.type),
                    isBluetooth = isBluetoothType(info.type),
                    isUsb = isUsbType(info.type),
                    isBuiltIn = info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                )
            }
            .ifEmpty {
                listOf(
                    AudioDeviceItem(
                        id = -2,
                        name = "Alto-falante",
                        typeLabel = "Integrado",
                        isBuiltIn = true
                    )
                )
            }

        val headphonesDetected = mappedOutputs.any { it.isHeadphone }
        val activeOutput = mappedOutputs.firstOrNull { it.id == currentSettings.selectedOutputDeviceId }
            ?: mappedOutputs.firstOrNull { it.isHeadphone }
            ?: mappedOutputs.first()
        val activeInput = mappedInputs.firstOrNull { it.id == currentSettings.selectedInputDeviceId }
            ?: mappedInputs.firstOrNull { !it.isBuiltIn }
            ?: mappedInputs.first()

        resolveSelectedDevices(currentSettings.selectedInputDeviceId, currentSettings.selectedOutputDeviceId)

        _telemetry.value = _telemetry.value.copy(
            inputDevices = mappedInputs,
            outputDevices = mappedOutputs,
            hasHeadphones = headphonesDetected,
            activeInputName = activeInput.name,
            activeOutputName = activeOutput.name
        )

        if (!headphonesDetected) {
            runCatching { monitorTrack?.setVolume(0f) }
        }
    }

    private fun resolveSelectedDevices(inputId: Int?, outputId: Int?) {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        preferredInputDevice = inputs.firstOrNull { it.id == inputId }
        preferredOutputDevice = outputs.firstOrNull { it.id == outputId }
        runCatching { audioRecord?.setPreferredDevice(preferredInputDevice) }
        runCatching { monitorTrack?.setPreferredDevice(preferredOutputDevice) }
    }

    fun startMicrophoneMonitor() {
        if (monitorJob?.isActive == true) return
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return

        monitorJob = engineScope.launch {
            val sampleRate = 44100
            val channelIn = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
            if (minBuf <= 0) return@launch
            val bufferSize = (minBuf * 2).coerceAtLeast(2048)

            val record = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelIn,
                    encoding,
                    bufferSize
                )
            }.getOrNull() ?: return@launch

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@launch
            }

            audioRecord = record
            preferredInputDevice?.let { runCatching { record.setPreferredDevice(it) } }

            attachInputDspEffects(record.audioSessionId)
            val track = createMonitorTrack(sampleRate, bufferSize)
            monitorTrack = track
            preferredOutputDevice?.let { runCatching { track?.setPreferredDevice(it) } }

            val shortBuffer = ShortArray(1024)
            val outBuffer = ShortArray(1024)
            var smoothedDb = -60f
            var lastSpeechAtMs = 0L
            var speechStartAtMs = 0L
            var currentlySpeaking = false

            try {
                record.startRecording()
                track?.play()

                while (isActive) {
                    val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        val settings = currentSettings
                        val gain = settings.micGain.coerceIn(0.2f, 2.0f)
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val boosted = (shortBuffer[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            outBuffer[i] = boosted.toShort()
                            val norm = boosted / 32768.0
                            sumSquares += norm * norm
                        }

                        val rms = sqrt(sumSquares / readCount).coerceAtLeast(1e-6)
                        val rawDb = (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)
                        smoothedDb = if (rawDb > smoothedDb) {
                            smoothedDb * 0.45f + rawDb * 0.55f
                        } else {
                            smoothedDb * 0.78f + rawDb * 0.22f
                        }

                        val now = System.currentTimeMillis()
                        val threshold = settings.voiceThresholdDb
                        if (smoothedDb >= threshold) {
                            if (speechStartAtMs == 0L) speechStartAtMs = now
                            lastSpeechAtMs = now
                            // 120ms attack prevents tiny clicks/pops from triggering reactions
                            if (now - speechStartAtMs >= 120L) {
                                currentlySpeaking = true
                            }
                        } else {
                            speechStartAtMs = 0L
                            // 550ms release holds speaking state smoothly across syllables
                            if (currentlySpeaking && now - lastSpeechAtMs >= 550L) {
                                currentlySpeaking = false
                            }
                        }

                        val normalized = ((smoothedDb + 60f) / 60f).coerceIn(0f, 1f)
                        _telemetry.value = _telemetry.value.copy(
                            currentDb = smoothedDb,
                            normalizedLevel = normalized,
                            isSpeaking = currentlySpeaking
                        )

                        // Safe headphone-only sidetone monitoring (prevents speaker feedback loop)
                        if (track != null && _telemetry.value.hasHeadphones && settings.headphoneMonitoringEnabled) {
                            track.setVolume(settings.monitorVolume.coerceIn(0f, 1f))
                            track.write(outBuffer, 0, readCount, AudioTrack.WRITE_NON_BLOCKING)
                        }
                    } else {
                        delay(40L)
                    }
                }
            } catch (_: Exception) {
                // Gracefully exit if mic is exclusively claimed by system
            } finally {
                releaseInputEffectsAndHardware()
                _telemetry.value = _telemetry.value.copy(
                    currentDb = -60f,
                    normalizedLevel = 0f,
                    isSpeaking = false
                )
            }
        }
    }

    fun stopMicrophoneMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        releaseInputEffectsAndHardware()
    }

    private fun attachInputDspEffects(sessionId: Int) {
        if (noiseSuppressorSupported) {
            runCatching {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = currentSettings.noiseSuppression
                }
            }
        }
        if (echoCancelerSupported) {
            runCatching {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = currentSettings.echoCancellation
                }
            }
        }
        if (agcSupported) {
            runCatching {
                automaticGainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = currentSettings.autoGainControl
                }
            }
        }
    }

    private fun createMonitorTrack(sampleRate: Int, bufferSize: Int): AudioTrack? {
        return runCatching {
            val minOut = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minOut, bufferSize))
                .build()
                .apply {
                    setVolume(0f)
                }
        }.getOrNull()
    }

    @Synchronized
    private fun releaseInputEffectsAndHardware() {
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching { automaticGainControl?.release() }
        automaticGainControl = null

        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { monitorTrack?.pause() }
        runCatching { monitorTrack?.flush() }
        runCatching { monitorTrack?.release() }
        monitorTrack = null
    }

    private fun releasePlayerEqualizer() {
        runCatching { playerEqualizer?.release() }
        playerEqualizer = null
    }

    fun release() {
        stopMicrophoneMonitor()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        releaseInputEffectsAndHardware()
        releasePlayerEqualizer()
        engineScope.cancel()
    }

    private fun isUserRelevantInput(type: Int): Boolean {
        val base = type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_USB_DEVICE
        val api26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            type == AudioDeviceInfo.TYPE_USB_HEADSET
        val api31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
        return base || api26 || api31
    }

    private fun isUserRelevantOutput(type: Int): Boolean {
        val base = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_USB_DEVICE
        val api26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            type == AudioDeviceInfo.TYPE_USB_HEADSET
        val api31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        return base || api26 || api31
    }

    private fun isHeadphoneType(type: Int): Boolean {
        val base = type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        val api26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            type == AudioDeviceInfo.TYPE_USB_HEADSET
        val api31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
        return base || api26 || api31
    }

    private fun isBluetoothType(type: Int): Boolean {
        val base = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        val api31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        return base || api31
    }

    private fun isUsbType(type: Int): Boolean {
        val api26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            type == AudioDeviceInfo.TYPE_USB_HEADSET
        return type == AudioDeviceInfo.TYPE_USB_DEVICE || api26
    }

    private fun formatDeviceName(info: AudioDeviceInfo, isInput: Boolean): String {
        val rawName = info.productName?.toString()?.trim().orEmpty()
        return when (info.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfone interno"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> if (rawName.isNotEmpty()) rawName else "Fone com fio"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> if (isInput) "Mic do fone com fio" else "Headset com fio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                if (rawName.isNotEmpty()) rawName else "Fone Bluetooth"
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    if (rawName.isNotEmpty()) rawName else "Fone USB-C"
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    if (rawName.isNotEmpty()) rawName else "Fone Bluetooth LE"
                } else {
                    rawName.ifEmpty { if (isInput) "Microfone externo" else "Saída de áudio" }
                }
            }
        }
    }

    private fun formatDeviceType(type: Int): String = when {
        isBluetoothType(type) -> "Bluetooth"
        isUsbType(type) -> "USB"
        type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> "P2 / Cabo"
        else -> "Integrado"
    }
}

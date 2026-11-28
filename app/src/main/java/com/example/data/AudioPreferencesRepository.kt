package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AudioSettingsState
import com.example.model.AutoReactMode
import com.example.model.HeadphoneEqProfile
import com.example.model.ReactLayoutMode
import com.example.model.SplitRatioPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "react_studio_preferences"
)

data class StoredTrialRecord(
    val startedAtMs: Long?,
    val integritySig: String?,
    val lastSeenMs: Long?
)

class AudioPreferencesRepository(private val context: Context) {

    private object Keys {
        val MIC_GAIN = floatPreferencesKey("mic_gain")
        val NOISE_SUPPRESSION = booleanPreferencesKey("noise_suppression")
        val ECHO_CANCELLATION = booleanPreferencesKey("echo_cancellation")
        val AUTO_GAIN_CONTROL = booleanPreferencesKey("auto_gain_control")
        val AUTO_REACT_ENABLED = booleanPreferencesKey("auto_react_enabled")
        val AUTO_REACT_MODE = stringPreferencesKey("auto_react_mode")
        val VOICE_THRESHOLD_DB = floatPreferencesKey("voice_threshold_db")
        val DUCK_VOLUME_RATIO = floatPreferencesKey("duck_volume_ratio")
        val AUTO_RESUME_DELAY_SEC = floatPreferencesKey("auto_resume_delay_sec")
        val HEADPHONE_MONITORING = booleanPreferencesKey("headphone_monitoring")
        val MONITOR_VOLUME = floatPreferencesKey("monitor_volume")
        val VIDEO_VOLUME = floatPreferencesKey("video_volume")
        val MIX_BALANCE = floatPreferencesKey("mix_balance")
        val EQ_PROFILE = stringPreferencesKey("eq_profile")
        val SPLIT_RATIO = stringPreferencesKey("split_ratio")
        val LAYOUT_MODE = stringPreferencesKey("react_layout_mode")
        val TRIAL_STARTED_AT_MS = longPreferencesKey("trial_started_at_ms")
        val TRIAL_INTEGRITY_SIG = stringPreferencesKey("trial_integrity_sig")
        val TRIAL_LAST_SEEN_MS = longPreferencesKey("trial_last_seen_ms")
    }

    val audioSettingsFlow: Flow<AudioSettingsState> = context.studioDataStore.data.map { prefs ->
        val modeName = prefs[Keys.AUTO_REACT_MODE]
        val eqName = prefs[Keys.EQ_PROFILE]
        AudioSettingsState(
            micGain = prefs[Keys.MIC_GAIN] ?: 1.0f,
            noiseSuppression = prefs[Keys.NOISE_SUPPRESSION] ?: true,
            echoCancellation = prefs[Keys.ECHO_CANCELLATION] ?: true,
            autoGainControl = prefs[Keys.AUTO_GAIN_CONTROL] ?: true,
            autoReactEnabled = prefs[Keys.AUTO_REACT_ENABLED] ?: true,
            autoReactMode = AutoReactMode.entries.firstOrNull { it.name == modeName }
                ?: AutoReactMode.DUCK_VIDEO,
            voiceThresholdDb = prefs[Keys.VOICE_THRESHOLD_DB] ?: -26f,
            duckVolumeRatio = prefs[Keys.DUCK_VOLUME_RATIO] ?: 0.22f,
            autoResumeDelaySec = prefs[Keys.AUTO_RESUME_DELAY_SEC] ?: 1.8f,
            headphoneMonitoringEnabled = prefs[Keys.HEADPHONE_MONITORING] ?: false,
            monitorVolume = prefs[Keys.MONITOR_VOLUME] ?: 0.65f,
            videoVolume = prefs[Keys.VIDEO_VOLUME] ?: 0.85f,
            mixBalance = prefs[Keys.MIX_BALANCE] ?: 0.5f,
            eqProfile = HeadphoneEqProfile.entries.firstOrNull { it.name == eqName }
                ?: HeadphoneEqProfile.CLEAR_VOICE
        )
    }

    val splitRatioFlow: Flow<SplitRatioPreset> = context.studioDataStore.data.map { prefs ->
        val splitName = prefs[Keys.SPLIT_RATIO]
        SplitRatioPreset.entries.firstOrNull { it.name == splitName } ?: SplitRatioPreset.BALANCED
    }

    val layoutModeFlow: Flow<ReactLayoutMode> = context.studioDataStore.data.map { prefs ->
        val modeName = prefs[Keys.LAYOUT_MODE]
        ReactLayoutMode.entries.firstOrNull { it.name == modeName } ?: ReactLayoutMode.SPLIT_SCREEN
    }

    suspend fun readTrialRecord(): StoredTrialRecord {
        val prefs = context.studioDataStore.data.first()
        return StoredTrialRecord(
            startedAtMs = prefs[Keys.TRIAL_STARTED_AT_MS],
            integritySig = prefs[Keys.TRIAL_INTEGRITY_SIG],
            lastSeenMs = prefs[Keys.TRIAL_LAST_SEEN_MS]
        )
    }

    suspend fun saveTrialRecord(startedAtMs: Long, integritySig: String, lastSeenMs: Long) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.TRIAL_STARTED_AT_MS] = startedAtMs
            prefs[Keys.TRIAL_INTEGRITY_SIG] = integritySig
            prefs[Keys.TRIAL_LAST_SEEN_MS] = lastSeenMs
        }
    }

    suspend fun saveAudioSettings(settings: AudioSettingsState) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.MIC_GAIN] = settings.micGain
            prefs[Keys.NOISE_SUPPRESSION] = settings.noiseSuppression
            prefs[Keys.ECHO_CANCELLATION] = settings.echoCancellation
            prefs[Keys.AUTO_GAIN_CONTROL] = settings.autoGainControl
            prefs[Keys.AUTO_REACT_ENABLED] = settings.autoReactEnabled
            prefs[Keys.AUTO_REACT_MODE] = settings.autoReactMode.name
            prefs[Keys.VOICE_THRESHOLD_DB] = settings.voiceThresholdDb
            prefs[Keys.DUCK_VOLUME_RATIO] = settings.duckVolumeRatio
            prefs[Keys.AUTO_RESUME_DELAY_SEC] = settings.autoResumeDelaySec
            prefs[Keys.HEADPHONE_MONITORING] = settings.headphoneMonitoringEnabled
            prefs[Keys.MONITOR_VOLUME] = settings.monitorVolume
            prefs[Keys.VIDEO_VOLUME] = settings.videoVolume
            prefs[Keys.MIX_BALANCE] = settings.mixBalance
            prefs[Keys.EQ_PROFILE] = settings.eqProfile.name
        }
    }

    suspend fun saveSplitRatio(preset: SplitRatioPreset) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.SPLIT_RATIO] = preset.name
        }
    }

    suspend fun saveLayoutMode(mode: ReactLayoutMode) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.LAYOUT_MODE] = mode.name
        }
    }
}

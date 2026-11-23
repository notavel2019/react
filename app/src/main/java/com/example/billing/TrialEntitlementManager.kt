package com.example.billing

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.AudioPreferencesRepository
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class TrialSnapshot(
    val trialStartedAtMs: Long,
    val trialEndsAtMs: Long,
    val remainingMs: Long,
    val isTrialActive: Boolean,
    val isClockTampered: Boolean = false
)

/**
 * Manages the 4-day (96-hour) full-access trial window with install-time anchoring,
 * SHA-256 integrity verification, and monotonic clock-rollback detection.
 */
class TrialEntitlementManager(
    private val context: Context,
    private val prefsRepo: AudioPreferencesRepository
) {
    companion object {
        const val TRIAL_DURATION_DAYS = 4
        val TRIAL_DURATION_MS: Long = TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())
        private const val CLOCK_ROLLBACK_TOLERANCE_MS = 10L * 60L * 1000L // 10 minutes
    }

    suspend fun evaluateTrial(): TrialSnapshot {
        val nowMs = System.currentTimeMillis()
        val installTimeMs = readFirstInstallTimeMs().takeIf { it in 1_600_000_000_000L..nowMs } ?: nowMs

        val stored = prefsRepo.readTrialRecord()
        val expectedSig = stored.startedAtMs?.let { computeIntegrityTag(it) }
        val isStoredValid = stored.startedAtMs != null &&
            stored.startedAtMs > 1_600_000_000_000L &&
            stored.integritySig == expectedSig

        val effectiveStartMs = if (isStoredValid) {
            minOf(stored.startedAtMs!!, installTimeMs)
        } else {
            installTimeMs
        }

        // Detect significant backward system clock manipulation
        val lastSeenMs = stored.lastSeenMs ?: effectiveStartMs
        val isClockTampered = nowMs + CLOCK_ROLLBACK_TOLERANCE_MS < lastSeenMs
        val monotonicNowMs = maxOf(nowMs, lastSeenMs)

        // Persist anchored start timestamp + monotonic watermark
        if (!isStoredValid || stored.startedAtMs != effectiveStartMs || monotonicNowMs > lastSeenMs) {
            prefsRepo.saveTrialRecord(
                startedAtMs = effectiveStartMs,
                integritySig = computeIntegrityTag(effectiveStartMs),
                lastSeenMs = monotonicNowMs
            )
        }

        val endsAtMs = effectiveStartMs + TRIAL_DURATION_MS
        val remainingMs = if (isClockTampered) {
            0L
        } else {
            (endsAtMs - monotonicNowMs).coerceAtLeast(0L)
        }

        return TrialSnapshot(
            trialStartedAtMs = effectiveStartMs,
            trialEndsAtMs = endsAtMs,
            remainingMs = remainingMs,
            isTrialActive = remainingMs > 0L && !isClockTampered,
            isClockTampered = isClockTampered
        )
    }

    private fun readFirstInstallTimeMs(): Long {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
    }

    private fun computeIntegrityTag(startedAtMs: Long): String {
        val payload = "${context.packageName}|trial_4d|$startedAtMs"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}

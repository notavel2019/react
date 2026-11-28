package com.example.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.util.Locale

const val OWNER_ADMIN_EMAIL = "lojadolar@gmail.com"
const val ACCESS_CONTROLS_COLLECTION = "access_controls"

private val EMAIL_VALIDATION_REGEX = Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")

enum class AccessOverrideStatus(val firestoreValue: String) {
    ALLOWED("ALLOWED"),
    BLOCKED("BLOCKED");

    companion object {
        fun fromWire(value: String?): AccessOverrideStatus? = when (value) {
            "ALLOWED" -> ALLOWED
            "BLOCKED" -> BLOCKED
            else -> null
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AccessControlEntry(
    @DocumentId val id: String = "",
    val emailLower: String = "",
    val status: String = AccessOverrideStatus.ALLOWED.firestoreValue,
    val note: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: AccessOverrideStatus?
        get() = AccessOverrideStatus.fromWire(status)
}

fun normalizeEmail(raw: String): String = raw.trim().lowercase(Locale.US)

fun isValidEmailFormat(normalizedEmail: String): Boolean =
    normalizedEmail.length in 5..120 && EMAIL_VALIDATION_REGEX.matches(normalizedEmail)

fun isOwnerAdminEmail(email: String?): Boolean =
    email != null && normalizeEmail(email) == OWNER_ADMIN_EMAIL

/**
 * Deterministic 64-character lowercase hex SHA-256 document ID for an email address.
 * Matches ^[a-zA-Z0-9_\-]+$ (1..128 chars), avoids PII in Firestore URL paths, and guarantees
 * a single canonical rule document per Google account email.
 */
fun emailToDocId(normalizedEmail: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalizedEmail.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

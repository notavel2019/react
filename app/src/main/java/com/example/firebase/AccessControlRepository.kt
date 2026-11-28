package com.example.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AccessControlRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val collectionRef
        get() = db.collection(ACCESS_CONTROLS_COLLECTION)

    /**
     * Real-time listener for the signed-in user's own access override record.
     * Uses an emailLower equality query so Firestore security rules allow regular users
     * to read ONLY their own verified email record without exposing any other user's data.
     */
    fun observeMyAccessControl(email: String): Flow<AccessControlEntry?> {
        val emailLower = normalizeEmail(email)
        return collectionRef
            .whereEqualTo("emailLower", emailLower)
            .limit(1)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.firstOrNull()?.toObject(
                    AccessControlEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                handleFirestoreError(cause, OperationType.LIST, ACCESS_CONTROLS_COLLECTION)
            }
    }

    /**
     * Admin-only real-time listener for all access control overrides.
     * Call ONLY when isOwnerAdminEmail(currentUser.email) is true.
     */
    fun observeAllAccessControls(): Flow<List<AccessControlEntry>> {
        return collectionRef
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        AccessControlEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                handleFirestoreError(cause, OperationType.LIST, ACCESS_CONTROLS_COLLECTION)
            }
    }

    /**
     * Admin-only create or update of a complimentary grant (ALLOWED) or suspension (BLOCKED).
     */
    suspend fun upsertAccessControl(
        rawEmail: String,
        status: AccessOverrideStatus,
        rawNote: String
    ): Result<String> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Faça login com sua conta Google primeiro."))
        if (!isOwnerAdminEmail(currentUser.email)) {
            return Result.failure(SecurityException("Apenas a conta administradora pode gerenciar acessos."))
        }

        val emailLower = normalizeEmail(rawEmail)
        if (!isValidEmailFormat(emailLower)) {
            return Result.failure(IllegalArgumentException("Informe um e-mail válido (ex.: nome@gmail.com)."))
        }
        if (emailLower == OWNER_ADMIN_EMAIL && status == AccessOverrideStatus.BLOCKED) {
            return Result.failure(IllegalArgumentException("Não é possível bloquear a conta administradora principal."))
        }

        val cleanNote = rawNote.trim().take(140)
        val docId = emailToDocId(emailLower)
        val docPath = "$ACCESS_CONTROLS_COLLECTION/$docId"
        val docRef = collectionRef.document(docId)

        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mapOf<String, Any>(
                    "status" to status.firestoreValue,
                    "note" to cleanNote,
                    "updatedByUid" to currentUser.uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf<String, Any>(
                    "emailLower" to emailLower,
                    "status" to status.firestoreValue,
                    "note" to cleanNote,
                    "updatedByUid" to currentUser.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(emailLower)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching {
                handleFirestoreError(e, OperationType.WRITE, docPath)
            }
            Result.failure(e)
        }
    }

    /**
     * Admin-only deletion of an override record (returns the user to normal 4-day trial / Google Play flow).
     */
    suspend fun deleteAccessControl(entryId: String): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Faça login com sua conta Google primeiro."))
        if (!isOwnerAdminEmail(currentUser.email)) {
            return Result.failure(SecurityException("Apenas a conta administradora pode remover regras."))
        }
        val cleanId = entryId.trim()
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Registro inválido."))
        }
        val docPath = "$ACCESS_CONTROLS_COLLECTION/$cleanId"
        return try {
            collectionRef.document(cleanId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching {
                handleFirestoreError(e, OperationType.DELETE, docPath)
            }
            Result.failure(e)
        }
    }
}

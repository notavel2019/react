package com.example.firebase

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome()
    data object Cancelled : GoogleSignInOutcome()
    data object NoSavedAccount : GoogleSignInOutcome()
    data class Error(val message: String) : GoogleSignInOutcome()
}

/**
 * Google Sign-In via Jetpack Credential Manager + Firebase Auth.
 * Strictly separates GetGoogleIdOption (silent auto sign-in) and GetSignInWithGoogleOption
 * (interactive button) into single-option GetCredentialRequest calls.
 */
object GoogleAuthHelper {

    private const val TAG = "Auth"

    suspend fun attemptAutoSignIn(activity: Activity): GoogleSignInOutcome {
        val webClientId = activity.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(activity)
        val autoOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(autoOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            signInWithCredentialResult(result)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            GoogleSignInOutcome.NoSavedAccount
        } catch (_: GetCredentialCancellationException) {
            Log.w(TAG, "Auto Google Sign-In cancelled by user")
            GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Auto Google Sign-In unavailable", e)
            GoogleSignInOutcome.NoSavedAccount
        } catch (e: Exception) {
            Log.w(TAG, "Auto Google Sign-In failed", e)
            GoogleSignInOutcome.NoSavedAccount
        }
    }

    suspend fun onGoogleSignInClicked(activity: Activity): GoogleSignInOutcome {
        val webClientId = activity.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            signInWithCredentialResult(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled by user", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google accounts available on device", e)
            GoogleSignInOutcome.Error(
                "Nenhuma conta Google encontrada neste aparelho. Adicione uma conta Google nas Configurações do Android."
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.type} - ${e.message}", e)
            GoogleSignInOutcome.Error(
                "Não foi possível concluir o login com o Google. Verifique sua conexão e tente novamente."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            GoogleSignInOutcome.Error(
                "Erro inesperado ao entrar com o Google: ${e.localizedMessage ?: "tente novamente"}"
            )
        }
    }

    private suspend fun signInWithCredentialResult(result: GetCredentialResponse): GoogleSignInOutcome {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(
                googleIdTokenCredential.idToken,
                null
            )
            val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
            return if (user != null) {
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Error("Sessão Google indisponível após autenticação.")
            }
        }
        return GoogleSignInOutcome.Error("Tipo de credencial Google não suportado.")
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear CredentialManager state during signOut", e)
            }
            Firebase.auth.signOut()
        }
    }
}

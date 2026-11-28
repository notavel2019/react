package com.example.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?
): Nothing {
    val user = Firebase.auth.currentUser
    val providerArray = JSONArray().apply {
        user?.providerData?.forEach { p ->
            put(
                JSONObject().apply {
                    put("providerId", p.providerId)
                    put("displayName", p.displayName)
                    put("email", p.email)
                    put("photoUrl", p.photoUrl?.toString())
                }
            )
        }
    }
    val errInfo = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.value)
        put("path", path)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", user?.uid)
                put("email", user?.email)
                put("emailVerified", user?.isEmailVerified)
                put("isAnonymous", user?.isAnonymous)
                put("tenantId", user?.tenantId)
                put("providerInfo", providerArray)
            }
        )
    }
    Log.e("FirestoreError", errInfo.toString(), error)
    throw IllegalStateException(errInfo.toString(), error)
}

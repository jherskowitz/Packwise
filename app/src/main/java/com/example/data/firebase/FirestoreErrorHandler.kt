package com.example.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

enum class OperationType {
  CREATE,
  UPDATE,
  DELETE,
  LIST,
  GET,
  WRITE,
}

data class AuthUserInfo(
  val uid: String? = null,
  val email: String? = null,
  val isEmailVerified: Boolean? = null,
  val displayName: String? = null,
  val photoUrl: String? = null,
  val providerId: String? = null,
)

data class FirestoreErrorInfo(
  val error: String,
  val operationType: OperationType,
  val path: String,
  val authInfo: AuthUserInfo,
)

fun handleFirestoreError(
  exception: Exception,
  operationType: OperationType,
  path: String,
  auth: FirebaseAuth,
) {
  val currentUser = auth.currentUser
  val authInfo = AuthUserInfo(
    uid = currentUser?.uid,
    email = currentUser?.email,
    isEmailVerified = currentUser?.isEmailVerified,
    displayName = currentUser?.displayName,
    photoUrl = currentUser?.photoUrl?.toString(),
    providerId = currentUser?.providerData?.firstOrNull()?.providerId,
  )
  val errInfo = FirestoreErrorInfo(
    error = exception.message ?: exception.toString(),
    operationType = operationType,
    path = path,
    authInfo = authInfo,
  )
  val payload = runCatching {
    JSONObject().apply {
      put("error", errInfo.error)
      put("operationType", errInfo.operationType.name)
      put("path", errInfo.path)
      put(
        "authInfo",
        JSONObject().apply {
          put("uid", authInfo.uid)
          put("email", authInfo.email)
          put("isEmailVerified", authInfo.isEmailVerified)
          put("displayName", authInfo.displayName)
          put("photoUrl", authInfo.photoUrl)
          put("providerId", authInfo.providerId)
        }
      )
    }.toString()
  }.getOrElse { errInfo.toString() }
  Log.e("FirestoreError", "Firestore Error: $payload", exception)
}

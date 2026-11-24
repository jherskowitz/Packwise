package com.example.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "PackWiseAuth"

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
) {
  val serverClientId = context.getString(R.string.default_web_client_id)
  if (serverClientId.isBlank()) {
    onAuthError("Missing Google web client ID configuration.")
    return
  }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId)
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(
        context = context,
        request = request,
      )
      val credential = result.credential
      if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        Firebase.auth.signInWithCredential(firebaseCred).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type returned from Google Sign-In.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(TAG, "Google Sign-In was cancelled by user or play-services configuration.", e)
      onAuthCancelled()
    } catch (e: GetCredentialException) {
      Log.e(TAG, "Google Sign-In failed: ${e.errorMessage}", e)
      onAuthError(e.errorMessage?.toString() ?: e.localizedMessage ?: "Google Sign-In failed.")
    } catch (e: Exception) {
      Log.e(TAG, "Firebase credential sign-in failed", e)
      onAuthError(e.localizedMessage ?: "Authentication failed.")
    }
  }
}

fun signOutUser(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onComplete: () -> Unit = {},
) {
  scope.launch {
    withContext(NonCancellable) {
      runCatching { Firebase.auth.signOut() }
      runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }
    onComplete()
  }
}

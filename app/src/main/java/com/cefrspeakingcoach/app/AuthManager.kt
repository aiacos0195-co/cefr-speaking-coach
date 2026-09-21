package com.cefrspeakingcoach.app

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

data class SignedInUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    fun currentUser(): SignedInUser? = auth.currentUser?.toSignedInUser()

    fun isSignedIn(): Boolean = auth.currentUser != null

    suspend fun signInWithGoogle(): SignedInUser {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            context = context,
            request = request
        )

        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                    ?: throw IllegalStateException("Google sign-in succeeded but Firebase user is null.")

                return user.toSignedInUser()
            } catch (e: GoogleIdTokenParsingException) {
                Log.e("AuthManager", "Google ID token parse failed", e)
                throw RuntimeException("No se pudo leer la cuenta de Google.", e)
            }
        } else {
            throw RuntimeException("No se recibió una credencial válida de Google.")
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun FirebaseUser.toSignedInUser(): SignedInUser {
        return SignedInUser(
            uid = uid,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl?.toString()
        )
    }
}

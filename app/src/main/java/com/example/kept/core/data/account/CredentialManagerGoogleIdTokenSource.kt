package com.example.kept.core.data.account

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

/**
 * The Google account sheet through Credential Manager (#193). `GetSignInWithGoogleOption` is the
 * option for a "Sign in with Google" button: it always shows the sheet, also on a device where
 * no account has used KEPT before. [serverClientId] is the OAuth web client Supabase checks the
 * token's audience against.
 */
class CredentialManagerGoogleIdTokenSource(private val serverClientId: String) : GoogleIdTokenSource {

    override suspend fun idToken(activityContext: Context, hashedNonce: String): IdTokenResult {
        val option = GetSignInWithGoogleOption.Builder(serverClientId).setNonce(hashedNonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                IdTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                IdTokenResult.Failed(IllegalStateException("Unexpected credential ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            whenCancelled(online = isOnline(activityContext))
        } catch (e: GetCredentialException) {
            // No Google account, no matching OAuth client, Play services missing, no network.
            // Offline, Play services reports "[28404] Failed to retrieve an ID token" (seen on
            // kept_api35), so the device's connection decides whether it reads as offline.
            whenFailed(online = isOnline(activityContext), e)
        } catch (e: GoogleIdTokenParsingException) {
            IdTokenResult.Failed(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            IdTokenResult.Failed(e)
        }
    }

    companion object {
        /** A Credential Manager failure while offline is a connection failure, whatever its type. */
        fun whenFailed(online: Boolean, cause: Throwable): IdTokenResult =
            IdTokenResult.Failed(if (online) cause else IOException("No internet while signing in", cause))

        /**
         * Play services reports a network error inside its account sheet as a cancellation (seen on
         * `kept_api35` in airplane mode: "Network error", then "Account reauth failed", then a
         * cancel). Offline, a cancel is therefore read as a failure, so the person sees the
         * connection line rather than "Sign-in cancelled."
         */
        fun whenCancelled(online: Boolean): IdTokenResult =
            if (online) IdTokenResult.Cancelled else IdTokenResult.Failed(IOException("No internet while the Google sheet was open"))

        private fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}

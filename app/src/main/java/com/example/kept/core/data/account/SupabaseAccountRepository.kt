package com.example.kept.core.data.account

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The one place the Supabase client is built (#193). Only the Auth plugin is installed: there is
 * no table client in the app, so nothing built on this can read or write a row (#193, item 7).
 * A blank URL or key gives no client, and sign-in then fails with the usual error.
 */
object SupabaseClients {
    fun create(url: String, key: String, engine: HttpClientEngine, auth: AuthConfig.() -> Unit = {}): SupabaseClient? {
        if (url.isBlank() || key.isBlank()) return null
        return createSupabaseClient(url, key) {
            httpEngine = engine
            install(Auth) { auth() }
        }
    }
}

/**
 * [AccountRepository] on Supabase Auth: the Google ID token from [tokens] is exchanged for a
 * Supabase session (`signInWith(IDToken)`), which the Auth plugin stores and restores on the next
 * start by itself. [client] is null when the build has no Supabase key.
 */
class SupabaseAccountRepository(
    private val client: SupabaseClient?,
    private val tokens: GoogleIdTokenSource,
    scope: CoroutineScope,
    private val nonce: () -> Nonce = { Nonce.random() },
) : AccountRepository {

    override val account: StateFlow<AccountState> = client?.auth?.sessionStatus?.let { status ->
        status.map(::accountOf).stateIn(scope, SharingStarted.Eagerly, accountOf(status.value))
    } ?: MutableStateFlow<AccountState>(AccountState.SignedOut).asStateFlow()

    override suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val r = attempt(activityContext)
        // Logged with its cause so a failure on a device can be told apart in logcat (#193).
        if (r is SignInResult.Failed) Log.w(TAG, "Sign in with Google failed (offline=${r.offline})", r.cause)
        return r
    }

    private suspend fun attempt(activityContext: Context): SignInResult {
        val c = client ?: return SignInResult.Failed(IllegalStateException("No Supabase key in this build"))
        val n = nonce()
        return when (val t = tokens.idToken(activityContext, n.hashed)) {
            IdTokenResult.Cancelled -> SignInResult.Cancelled
            is IdTokenResult.Failed -> SignInResult.Failed(t.cause)
            is IdTokenResult.Token -> try {
                c.auth.signInWith(IDToken) {
                    idToken = t.idToken
                    provider = Google
                    nonce = n.raw
                }
                SignInResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SignInResult.Failed(e)
            }
        }
    }

    override suspend fun signOut() {
        client?.auth?.let { auth ->
            try {
                auth.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline or a server error: the session still ends on the phone (#194, item 3).
                Log.w(TAG, "Sign out could not reach the server; clearing the session on the phone", e)
                auth.clearSession()
            }
        }
        try {
            tokens.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Google could not forget the account", e)
        }
    }

    companion object {
        private const val TAG = "KeptAccount"

        /**
         * A refresh that failed (offline at start with an expired token) keeps the stored session,
         * so it reads as [AccountState.Restoring], not signed out.
         */
        fun accountOf(s: SessionStatus): AccountState = when (s) {
            is SessionStatus.Authenticated -> {
                val user = s.session.user
                AccountState.SignedIn(user?.email, user?.userMetadata?.let(::displayName))
            }
            is SessionStatus.NotAuthenticated -> AccountState.SignedOut
            else -> AccountState.Restoring
        }

        /** Google's profile name as Supabase stores it in the user's metadata. */
        private fun displayName(meta: JsonObject): String? =
            listOf("full_name", "name").firstNotNullOfOrNull { (meta[it] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
    }
}

package com.example.kept.core.data.account

import android.content.Context
import com.example.kept.core.domain.DeleteUi
import com.example.kept.core.domain.SignInUi
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Who is signed in (#193). An account is optional: [SignedOut] is the normal state and every
 * screen works the same in it. [Restoring] lasts only while a stored session is read on start,
 * so a signed-in user never sees "Sign in with Google" flash up.
 */
sealed interface AccountState {
    data object Restoring : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val email: String?, val displayName: String?) : AccountState
}

/** How one sign-in attempt ended. The app stays usable, and signed out, on anything but [Success]. */
sealed interface SignInResult {
    data object Success : SignInResult
    /** The person closed the account sheet. */
    data object Cancelled : SignInResult
    /**
     * No network, no account on the device, a missing key, or the server said no. [offline] is
     * true only when the cause is a connection failure (an `IOException` anywhere in its chain).
     */
    data class Failed(val cause: Throwable? = null, val offline: Boolean = isConnectionFailure(cause)) : SignInResult

    companion object {
        fun isConnectionFailure(cause: Throwable?): Boolean = generateSequence(cause) { it.cause }.take(8).any { it is IOException }
    }
}

/** What a finished attempt leaves on screen: nothing after success, else one short line. */
fun SignInResult.toUi(): SignInUi = when (this) {
    SignInResult.Success -> SignInUi.Idle
    SignInResult.Cancelled -> SignInUi.failed(cancelled = true)
    is SignInResult.Failed -> SignInUi.failed(cancelled = false, offline = offline)
}

/** How one delete attempt ended (#195). On [Failed] the user is still signed in and nothing changed. */
sealed interface DeleteResult {
    data object Success : DeleteResult
    /** [offline] as in [SignInResult.Failed]: true only for a connection failure. */
    data class Failed(val cause: Throwable? = null, val offline: Boolean = SignInResult.isConnectionFailure(cause)) : DeleteResult
}

/** What a finished delete leaves on screen: the green line after success, else the red one. */
fun DeleteResult.toUi(): DeleteUi = when (this) {
    DeleteResult.Success -> DeleteUi.Deleted
    is DeleteResult.Failed -> DeleteUi.failed(offline)
}

/**
 * The account layer. Signing in touches nothing but the auth session: no habit, check-in, goal,
 * photo or setting is read or sent (#193, item 7). Signing out (#194) touches only the session too.
 * Deleting the account (#195) makes one server call, the `delete_my_account()` RPC, and then ends
 * the session on the phone; it never touches habit data either.
 */
interface AccountRepository {
    val account: StateFlow<AccountState>

    /** Runs the provider's account sheet from [activityContext], then opens a session. */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult

    /**
     * Ends the session (#194). Always ends [AccountState.SignedOut] on the phone, also offline or
     * when the server fails, and never throws: a user must never be stuck signed in. Google is
     * told to forget the choice, so the next sign-in shows the account chooser.
     */
    suspend fun signOut()

    /**
     * Deletes the signed-in user's account on the server (#195), then, only if that succeeded,
     * ends the session on the phone and tells Google to forget the choice, as [signOut] does.
     * On failure the session and the Google choice are kept, so the user can try again. Never
     * throws (except cancellation).
     */
    suspend fun deleteAccount(): DeleteResult
}

/** What the account sheet gave back. */
sealed interface IdTokenResult {
    data class Token(val idToken: String) : IdTokenResult
    data object Cancelled : IdTokenResult
    data class Failed(val cause: Throwable? = null) : IdTokenResult
}

/**
 * Gets a Google ID token for [hashedNonce] (the SHA-256 hex of the raw nonce the server is given).
 * On the device this is Credential Manager; tests fake it.
 */
fun interface GoogleIdTokenSource {
    suspend fun idToken(activityContext: Context, hashedNonce: String): IdTokenResult

    /** Forgets the account last chosen (#194), so the next [idToken] shows the chooser. */
    suspend fun clear() {}
}

/** The nonce pair for an ID-token sign-in: Google signs the hash, Supabase checks the raw value against it. */
data class Nonce(val raw: String, val hashed: String) {
    companion object {
        fun random(random: SecureRandom = SecureRandom()): Nonce {
            val bytes = ByteArray(32).also(random::nextBytes)
            return of(bytes.joinToString("") { "%02x".format(it) })
        }

        fun of(raw: String): Nonce =
            Nonce(raw, MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) })
    }
}

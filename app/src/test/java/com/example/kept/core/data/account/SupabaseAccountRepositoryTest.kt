package com.example.kept.core.data.account

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock

/**
 * Sign in with Google against the real Supabase client (#193), with Credential Manager faked and
 * the network replaced by a Ktor [MockEngine] that records every request. Covers the token
 * exchange (item 3), a stored session restoring on start (item 4), every failure path (item 6) and
 * that nothing but auth calls leave the phone (item 7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SupabaseAccountRepositoryTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun stop() = scope.cancel()

    private class Sent(val method: String, val path: String, val query: String, val body: String)

    private val sent = mutableListOf<Sent>()

    private val sessionJson = """
        {"access_token":"access-1","token_type":"bearer","expires_in":3600,"refresh_token":"refresh-1",
         "user":{"id":"7d3c","aud":"authenticated","email":"sam@example.com",
                 "user_metadata":{"full_name":"Sam Rivera","email":"sam@example.com"}}}
    """.trimIndent()

    private fun engine(fail: Throwable? = null, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine { req ->
        sent += Sent(req.method.value, req.url.encodedPath, req.url.encodedQuery, String(req.body.toByteArray()))
        if (fail != null) throw fail
        if (req.url.encodedPath == "/auth/v1/logout" && status == HttpStatusCode.OK) return@MockEngine respond("", HttpStatusCode.NoContent)
        if (status != HttpStatusCode.OK) respondError(status, """{"error_code":"bad_jwt","msg":"Bad ID token"}""", headersOf(HttpHeaders.ContentType, "application/json"))
        else respond(sessionJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun stored(): UserSession = Json { ignoreUnknownKeys = true }.decodeFromString(UserSession.serializer(), sessionJson)

    /** A token source that counts how often Google was told to forget the account (#194, item 4). */
    private class Forgetting : GoogleIdTokenSource {
        var cleared = 0
        override suspend fun idToken(activityContext: android.content.Context, hashedNonce: String): IdTokenResult = IdTokenResult.Token("google-id-token")
        override suspend fun clear() { cleared++ }
    }

    private fun repo(
        tokens: GoogleIdTokenSource = GoogleIdTokenSource { _, _ -> IdTokenResult.Token("google-id-token") },
        engine: MockEngine = engine(),
        sessions: MemorySessionManager = MemorySessionManager(),
        key: String = "publishable-key",
    ) = SupabaseAccountRepository(
        SupabaseClients.create("https://kyoxnvrgrydqyftpyovp.supabase.co", key, engine) {
            sessionManager = sessions
            codeVerifierCache = MemoryCodeVerifierCache()
            enableLifecycleCallbacks = false
        },
        tokens,
        scope,
        nonce = { Nonce.of("raw-nonce") },
    )

    /** The state flow follows the session a moment later; waits for it to read signed out. */
    private fun awaitSignedOut(r: AccountRepository) = runBlocking {
        assertEquals(AccountState.SignedOut, withTimeoutOrNull(5_000) { r.account.first { it == AccountState.SignedOut } } ?: r.account.value)
    }

    private fun settled(r: AccountRepository): AccountState = runBlocking {
        withTimeout(5_000) { r.account.first { it != AccountState.Restoring } }
    }

    @Test fun the_google_token_and_raw_nonce_are_exchanged_and_the_user_is_signed_in() = runBlocking {
        var askedNonce: String? = null
        val r = repo(tokens = { _, hashed -> askedNonce = hashed; IdTokenResult.Token("google-id-token") })
        assertEquals(AccountState.SignedOut, settled(r))

        assertEquals(SignInResult.Success, r.signInWithGoogle(ctx))

        // Google is given the SHA-256 of the nonce; Supabase gets the raw value to check it with.
        assertEquals(Nonce.of("raw-nonce").hashed, askedNonce)
        assertEquals(64, askedNonce!!.length)
        val exchange = sent.single()
        assertEquals("POST", exchange.method)
        assertEquals("/auth/v1/token", exchange.path)
        assertEquals("grant_type=id_token", exchange.query)
        val body = Json.parseToJsonElement(exchange.body).toString()
        assertTrue(body, body.contains("\"id_token\":\"google-id-token\""))
        assertTrue(body, body.contains("\"nonce\":\"raw-nonce\""))
        assertTrue(body, body.contains("\"provider\":\"google\""))
        assertEquals(AccountState.SignedIn("sam@example.com", "Sam Rivera"), r.account.first { it is AccountState.SignedIn })
    }

    @Test fun a_stored_session_restores_to_signed_in_without_the_account_sheet() = runBlocking {
        val stored = Json { ignoreUnknownKeys = true }.decodeFromString(UserSession.serializer(), sessionJson)
        val sessions = MemorySessionManager(stored)
        var sheetShown = 0
        val r = repo(tokens = { _, _ -> sheetShown++; IdTokenResult.Cancelled }, sessions = sessions)
        assertEquals(AccountState.SignedIn("sam@example.com", "Sam Rivera"), settled(r))
        assertEquals(0, sheetShown)
        assertTrue("a fresh stored session needs no request", sent.all { it.path.startsWith("/auth/v1/") })
    }

    @Test fun cancelling_the_sheet_is_cancelled_and_nothing_is_sent() = runBlocking {
        val r = repo(tokens = { _, _ -> IdTokenResult.Cancelled })
        assertEquals(SignInResult.Cancelled, r.signInWithGoogle(ctx))
        assertEquals(AccountState.SignedOut, settled(r))
        assertTrue(sent.isEmpty())
    }

    @Test fun no_google_account_on_the_device_fails_and_stays_signed_out() = runBlocking {
        val r = repo(tokens = { _, _ -> IdTokenResult.Failed(IllegalStateException("no credential")) })
        assertTrue(r.signInWithGoogle(ctx) is SignInResult.Failed)
        assertEquals(AccountState.SignedOut, settled(r))
        assertTrue(sent.isEmpty())
    }

    @Test fun no_network_fails_and_stays_signed_out() = runBlocking {
        val r = repo(engine = engine(fail = IOException("Unable to resolve host")))
        val result = r.signInWithGoogle(ctx)
        assertTrue("$result is an offline failure", result is SignInResult.Failed && result.offline)
        assertEquals(AccountState.SignedOut, settled(r))
    }

    @Test fun supabase_refusing_the_token_fails_and_stays_signed_out() = runBlocking {
        val r = repo(engine = engine(status = HttpStatusCode.BadRequest))
        val result = r.signInWithGoogle(ctx)
        // Not a connection problem, so the screen must not say "check your connection".
        assertTrue("$result is a non-offline failure", result is SignInResult.Failed && !result.offline)
        assertEquals(AccountState.SignedOut, settled(r))
    }

    @Test fun a_build_without_a_key_is_signed_out_and_sign_in_fails_without_the_sheet() = runBlocking {
        var sheetShown = 0
        val r = repo(tokens = { _, _ -> sheetShown++; IdTokenResult.Token("t") }, key = "")
        assertEquals(AccountState.SignedOut, r.account.value)
        assertTrue(r.signInWithGoogle(ctx) is SignInResult.Failed)
        assertEquals(0, sheetShown)
        assertTrue(sent.isEmpty())
    }

    /** Item 7: sign-in and restore make auth calls only, and no body carries habit data. */
    @Test fun only_auth_calls_leave_the_phone_and_none_carries_habit_data() = runBlocking {
        val stored = Json { ignoreUnknownKeys = true }.decodeFromString(UserSession.serializer(), sessionJson)
        settled(repo(sessions = MemorySessionManager(stored)))
        repo().signInWithGoogle(ctx)
        assertTrue(sent.isNotEmpty())
        for (s in sent) {
            assertTrue("${s.path} is not an auth call", s.path.startsWith("/auth/v1/"))
            for (word in listOf("habit", "check", "entry", "goal", "photo", "streak", "exception", "lock", "settings")) {
                assertTrue("${s.path} sends '$word': ${s.body}", !s.body.contains(word, ignoreCase = true))
            }
        }
    }

    /**
     * Item 7: every row of the habit database and every stored setting are the same after signing
     * in. (DataStore keeps one file per process, so the settings are compared as read back rather
     * than as the file's bytes, which a Robolectric suite may have placed in another test's folder.)
     */
    @Test fun signing_in_leaves_habits_and_preferences_untouched() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
        try {
            val prefs = KeptPreferences(ctx)
            prefs.clearAll()
            val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, TimeSource(Clock.systemUTC()))
            habits.addHabitWithinLimit("Read", "book", ProofType.MANUAL, 10, "pages")
            prefs.updateSettings { it.copy(lockFromMinute = 8 * 60, analyticsEnabled = false) }
            fun tables() = listOf("habits", "habit_entries", "day_records", "allowed_apps", "weekly_goals", "weekly_goal_entries", "gallery", "lock_breaks", "protection_gaps").associateWith { t ->
                db.openHelper.readableDatabase.query("SELECT * FROM $t").use { c ->
                    buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it).orEmpty() }) }
                }
            }
            val tablesBefore = tables()
            val settingsBefore = prefs.currentSettings()

            assertEquals(SignInResult.Success, repo().signInWithGoogle(ctx))

            assertEquals(tablesBefore, tables())
            assertEquals(settingsBefore, prefs.currentSettings())
            assertEquals(listOf("Read"), habits.observeHabits().first().map { it.title })
        } finally {
            db.close()
        }
    }

    @Test fun the_display_name_falls_back_to_name_and_a_failed_refresh_is_not_signed_out() {
        val session = Json { ignoreUnknownKeys = true }.decodeFromString(
            UserSession.serializer(),
            sessionJson.replace("\"full_name\":\"Sam Rivera\"", "\"name\":\"Sam R\""),
        )
        assertEquals(
            AccountState.SignedIn("sam@example.com", "Sam R"),
            SupabaseAccountRepository.accountOf(io.github.jan.supabase.auth.status.SessionStatus.Authenticated(session)),
        )
        assertEquals(
            AccountState.Restoring,
            SupabaseAccountRepository.accountOf(io.github.jan.supabase.auth.status.SessionStatus.RefreshFailure(io.github.jan.supabase.auth.status.RefreshFailureCause.NetworkError(IOException("offline")))),
        )
    }

    /** Offline, Play services closes its sheet as a cancel; that reads as an offline failure, not "cancelled". */
    @Test fun a_cancel_while_offline_is_an_offline_failure() {
        assertEquals(IdTokenResult.Cancelled, CredentialManagerGoogleIdTokenSource.whenCancelled(online = true))
        val offline = CredentialManagerGoogleIdTokenSource.whenCancelled(online = false)
        assertTrue(offline is IdTokenResult.Failed)
        val r = runBlocking { repo(tokens = { _, _ -> offline }).signInWithGoogle(ctx) }
        assertTrue("$r is an offline failure", r is SignInResult.Failed && r.offline)
    }

    /** Offline, Play services fails with "[28404] Failed to retrieve an ID token"; that reads as offline too. */
    @Test fun a_credential_failure_while_offline_is_an_offline_failure() = runBlocking {
        val cause = IllegalStateException("[28404] Failed to retrieve an ID token")
        val offline = CredentialManagerGoogleIdTokenSource.whenFailed(online = false, cause)
        val r = repo(tokens = { _, _ -> offline }).signInWithGoogle(ctx)
        assertTrue("$r is an offline failure", r is SignInResult.Failed && r.offline)
        val online = CredentialManagerGoogleIdTokenSource.whenFailed(online = true, cause)
        val r2 = repo(tokens = { _, _ -> online }).signInWithGoogle(ctx)
        assertTrue("$r2 is not offline", r2 is SignInResult.Failed && !r2.offline)
    }

    /** Owner-reported (#193): a rejected key or token said "check your connection". Only IO failures do now. */
    @Test fun only_connection_failures_are_offline() {
        assertTrue(SignInResult.Failed(IOException("reset")).offline)
        assertTrue(SignInResult.Failed(RuntimeException("wrapped", IOException("timeout"))).offline)
        assertTrue(!SignInResult.Failed(IllegalStateException("No Supabase key")).offline)
        assertTrue(!SignInResult.Failed(null).offline)
    }

    /** A 401 for a wrong publishable key, as a release build with a placeholder key got on the emulator. */
    @Test fun a_rejected_publishable_key_is_a_non_offline_failure() = runBlocking {
        val r = repo(engine = engine(status = HttpStatusCode.Unauthorized)).signInWithGoogle(ctx)
        assertTrue("$r", r is SignInResult.Failed && !r.offline)
    }

    // Sign out (#194) ---------------------------------------------------------------------------

    /** Items 3 and 4: sign out calls /auth/v1/logout, clears the stored session, and Google forgets the choice. */
    @Test fun signing_out_ends_the_session_clears_it_and_google_forgets_the_account() = runBlocking {
        val sessions = MemorySessionManager(stored())
        val tokens = Forgetting()
        val r = repo(tokens = tokens, sessions = sessions)
        assertTrue(settled(r) is AccountState.SignedIn)

        r.signOut()

        awaitSignedOut(r)
        assertEquals(null, sessions.loadSession())
        assertEquals(1, tokens.cleared)
        val logout = sent.single()
        assertEquals("POST", logout.method)
        assertEquals("/auth/v1/logout", logout.path)
    }

    /** Item 3: offline, sign out still clears the session on the phone and raises nothing. */
    @Test fun signing_out_offline_still_signs_out_on_the_phone() = runBlocking {
        val sessions = MemorySessionManager(stored())
        val tokens = Forgetting()
        val r = repo(tokens = tokens, engine = engine(fail = IOException("Unable to resolve host")), sessions = sessions)
        assertTrue(settled(r) is AccountState.SignedIn)

        r.signOut()

        awaitSignedOut(r)
        assertEquals(null, sessions.loadSession())
        assertEquals(1, tokens.cleared)
    }

    /** Item 3: a server error on logout does not keep the user signed in either. */
    @Test fun signing_out_when_the_server_fails_still_signs_out_on_the_phone() = runBlocking {
        val sessions = MemorySessionManager(stored())
        val r = repo(engine = engine(status = HttpStatusCode.InternalServerError), sessions = sessions)
        assertTrue(settled(r) is AccountState.SignedIn)

        r.signOut()

        awaitSignedOut(r)
        assertEquals(null, sessions.loadSession())
    }

    /** Item 4: Google failing to forget the account does not keep the user signed in. */
    @Test fun google_failing_to_forget_still_signs_out() = runBlocking {
        val sessions = MemorySessionManager(stored())
        val tokens = object : GoogleIdTokenSource {
            override suspend fun idToken(activityContext: android.content.Context, hashedNonce: String): IdTokenResult = IdTokenResult.Cancelled
            override suspend fun clear() = throw IllegalStateException("Play services gone")
        }
        val r = repo(tokens = tokens, sessions = sessions)
        assertTrue(settled(r) is AccountState.SignedIn)
        r.signOut()
        awaitSignedOut(r)
        assertEquals(null, sessions.loadSession())
    }

    /** Item 6: a fresh start over the same storage after a sign out is signed out, with no sheet. */
    @Test fun after_signing_out_a_restart_stays_signed_out() = runBlocking {
        val sessions = MemorySessionManager(stored())
        val first = repo(sessions = sessions)
        assertTrue(settled(first) is AccountState.SignedIn)
        first.signOut()
        var sheetShown = 0
        val restarted = repo(tokens = { _, _ -> sheetShown++; IdTokenResult.Cancelled }, sessions = sessions)
        assertEquals(AccountState.SignedOut, settled(restarted))
        assertEquals(0, sheetShown)
    }

    /** Item 7: after signing out, signing in again exchanges a new token and is signed in. */
    @Test fun after_signing_out_signing_in_again_works() = runBlocking {
        val r = repo(sessions = MemorySessionManager(stored()))
        assertTrue(settled(r) is AccountState.SignedIn)
        r.signOut()
        awaitSignedOut(r)
        assertEquals(SignInResult.Success, r.signInWithGoogle(ctx))
        assertEquals(AccountState.SignedIn("sam@example.com", "Sam Rivera"), r.account.first { it is AccountState.SignedIn })
    }

    /** Item 5: sign out leaves every habit row and setting as it was, and makes auth calls only. */
    @Test fun signing_out_leaves_habits_and_preferences_untouched() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
        try {
            val prefs = KeptPreferences(ctx)
            prefs.clearAll()
            val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, TimeSource(Clock.systemUTC()))
            habits.addHabitWithinLimit("Read", "book", ProofType.MANUAL, 10, "pages")
            prefs.updateSettings { it.copy(lockFromMinute = 8 * 60, analyticsEnabled = false) }
            fun tables() = listOf("habits", "habit_entries", "day_records", "allowed_apps", "weekly_goals", "weekly_goal_entries", "gallery", "lock_breaks", "protection_gaps").associateWith { t ->
                db.openHelper.readableDatabase.query("SELECT * FROM $t").use { c ->
                    buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it).orEmpty() }) }
                }
            }
            val tablesBefore = tables()
            val settingsBefore = prefs.currentSettings()
            val r = repo(sessions = MemorySessionManager(stored()))
            assertTrue(settled(r) is AccountState.SignedIn)

            r.signOut()

            awaitSignedOut(r)
            assertEquals(tablesBefore, tables())
            assertEquals(settingsBefore, prefs.currentSettings())
            assertEquals(listOf("Read"), habits.observeHabits().first().map { it.title })
            assertTrue(sent.isNotEmpty())
            for (s in sent) assertTrue("${s.path} is not an auth call", s.path.startsWith("/auth/v1/"))
        } finally {
            db.close()
        }
    }

    /** Without a key there is no session to end; sign out still asks Google to forget, and stays signed out. */
    @Test fun signing_out_without_a_key_is_harmless() = runBlocking {
        val tokens = Forgetting()
        val r = repo(tokens = tokens, key = "")
        r.signOut()
        assertEquals(AccountState.SignedOut, r.account.value)
        assertEquals(1, tokens.cleared)
        assertTrue(sent.isEmpty())
    }
}

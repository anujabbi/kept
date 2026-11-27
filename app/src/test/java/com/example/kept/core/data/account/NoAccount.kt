package com.example.kept.core.data.account

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The account layer for tests that do not sign in: always signed out, and sign-in fails (#193). */
object NoAccount : AccountRepository {
    override val account: StateFlow<AccountState> = MutableStateFlow(AccountState.SignedOut)
    override suspend fun signInWithGoogle(activityContext: Context): SignInResult = SignInResult.Failed()
}

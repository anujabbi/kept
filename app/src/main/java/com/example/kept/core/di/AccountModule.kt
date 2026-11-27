package com.example.kept.core.di

import com.example.kept.BuildConfig
import com.example.kept.core.data.account.AccountRepository
import com.example.kept.core.data.account.CredentialManagerGoogleIdTokenSource
import com.example.kept.core.data.account.SupabaseAccountRepository
import com.example.kept.core.data.account.SupabaseClients
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** The optional account (#193): one Supabase client, auth only, built from the BuildConfig values. */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {
    @Provides @Singleton
    fun accountRepository(@ApplicationContext ctx: Context, @ApplicationScope scope: CoroutineScope): AccountRepository = SupabaseAccountRepository(
        client = SupabaseClients.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, Android.create()),
        tokens = CredentialManagerGoogleIdTokenSource(ctx, BuildConfig.GOOGLE_WEB_CLIENT_ID),
        scope = scope,
    )
}

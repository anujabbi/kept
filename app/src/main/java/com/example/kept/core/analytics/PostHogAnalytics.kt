package com.example.kept.core.analytics

import com.posthog.PostHog
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Analytics] on top of the PostHog Android SDK.
 *
 * The SDK is set up in `KeptApplication`; every static call below is safe before that happens (it
 * is dropped by the SDK's own "not enabled" check), which is what lets instrumentation tests run
 * against the real graph without a project token.
 */
@Singleton
class PostHogAnalytics @Inject constructor() : GatedAnalytics() {
    override fun sendCapture(event: String, properties: Map<String, Any>) =
        PostHog.capture(event, properties = properties.ifEmpty { null })

    override fun sendScreen(name: String, properties: Map<String, Any>) =
        PostHog.screen(name, properties = properties.ifEmpty { null })

    override fun sendRegister(key: String, value: Any) = PostHog.register(key, value)

    override fun sendOptOut() = PostHog.optOut()

    override fun sendOptIn() = PostHog.optIn()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds @Singleton abstract fun analytics(impl: PostHogAnalytics): Analytics
}

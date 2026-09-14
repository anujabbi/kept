package com.example.kept

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.kept.core.analytics.AnalyticsInitializer
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KeptApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var analyticsInitializer: AnalyticsInitializer

    override fun onCreate() {
        super.onCreate()
        setUpPostHog()
        // Runs even when the SDK was not set up: the stored opt-out and the super properties are
        // the wrapper's own state, and every capture behind them is dropped harmlessly.
        analyticsInitializer.start()
    }

    private fun setUpPostHog() {
        val apiKey = BuildConfig.POSTHOG_PROJECT_TOKEN
        val host = BuildConfig.POSTHOG_HOST
        if (apiKey.isBlank()) {
            require(!BuildConfig.DEBUG) {
                "POSTHOG_PROJECT_TOKEN variable required by PostHog is missing or un-configured, this causes events to be silently missed. This error stops appearing once POSTHOG_PROJECT_TOKEN is configured"
            }
            return
        }
        if (host.isBlank()) {
            require(!BuildConfig.DEBUG) {
                "POSTHOG_HOST variable required by PostHog is missing or un-configured, this causes events to be silently missed. This error stops appearing once POSTHOG_HOST is configured"
            }
            return
        }

        val config = PostHogAndroidConfig(apiKey = apiKey, host = host).apply {
            errorTrackingConfig.autoCapture = true
            // KEPT is a lock screen over other people's apps and a camera proof flow. Recording any
            // of that would be a betrayal of what the app is for, so replay is off explicitly
            // rather than by default (issue #10).
            sessionReplay = false
            // Surveys are authored in the PostHog dashboard; the app only has to be willing to show
            // one. Popover surveys appear on their own for users matching the display conditions.
            surveys = true
            // Screen views are captured by hand, per navigation destination, so that Lock, Break
            // and the celebration are named too. Autocapture would report two activity names.
            captureScreenViews = false
            debug = BuildConfig.DEBUG
        }
        PostHogAndroid.setup(this, config)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

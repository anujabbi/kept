package com.example.kept

import android.app.Application
import android.util.Log
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
        // A missing token is a warning, never a crash. The PostHog wrapper drops every capture
        // when the SDK was never set up, so the only consequence is silence in the dashboard — and
        // a `require` here meant a fresh clone with no `.env` could not launch a debug build at
        // all, which is a far worse failure than un-reported events.
        if (apiKey.isBlank()) {
            Log.w(TAG, "POSTHOG_PROJECT_TOKEN is not configured; analytics are disabled for this build")
            return
        }
        if (host.isBlank()) {
            Log.w(TAG, "POSTHOG_HOST is not configured; analytics are disabled for this build")
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

    private companion object {
        const val TAG = "KeptApplication"
    }
}

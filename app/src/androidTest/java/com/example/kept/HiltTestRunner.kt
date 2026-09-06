package com.example.kept

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun callApplicationOnCreate(app: Application) {
        // HiltTestApplication does not implement Configuration.Provider; the manifest disables the
        // default initializer, so initialize WorkManager here before any test code runs.
        runCatching { WorkManager.initialize(app, Configuration.Builder().build()) }
        super.callApplicationOnCreate(app)
    }
}

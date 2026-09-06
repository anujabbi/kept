package com.example.kept

import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import kotlinx.coroutines.runBlocking

/** Wipes persistent state so each test starts from a fresh install. */
fun resetAppState(db: KeptDatabase, prefs: KeptPreferences) = runBlocking {
    db.clearAllTables()
    prefs.clearAll()
}

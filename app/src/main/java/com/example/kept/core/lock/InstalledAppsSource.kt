package com.example.kept.core.lock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String) {
    val isSystemLikely: Boolean get() = packageName.startsWith("com.android.") || packageName.startsWith("com.google.android.")
}

/** Launchable apps on the device. Only apps with a launcher icon can ever be locked. */
@Singleton
class InstalledAppsSource @Inject constructor(@ApplicationContext private val ctx: Context) {

    @Volatile private var launchableCache: Set<String>? = null
    @Volatile private var cacheAt = 0L

    fun launchablePackages(): Set<String> {
        val now = System.currentTimeMillis()
        launchableCache?.let { if (now - cacheAt < 5 * 60_000) return it }
        val set = query().map { it.packageName }.toSet()
        launchableCache = set
        cacheAt = now
        return set
    }

    fun invalidate() { launchableCache = null }

    suspend fun listLaunchable(): List<InstalledApp> = withContext(Dispatchers.IO) {
        query().sortedBy { it.label.lowercase() }
    }

    fun icon(pkg: String): Drawable? = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()

    fun label(pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

    private fun query(): List<InstalledApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        return infos
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName != ctx.packageName }
    }
}

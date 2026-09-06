package com.example.kept.core.lock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import com.example.kept.core.domain.Allowlist
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the device-specific members of the hard allowlist: default dialer, SMS, home, camera,
 * maps, clock, contacts, current keyboard, emergency role holder. Cached for one minute.
 */
@Singleton
class AllowlistResolver @Inject constructor(@ApplicationContext private val ctx: Context) {

    @Volatile private var cached: Set<String> = Allowlist.STATIC
    @Volatile private var cachedAt = 0L

    fun hardAllowlist(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 60_000 && cached.size > Allowlist.STATIC.size) return cached
        cached = Allowlist.build(resolve())
        cachedAt = now
        return cached
    }

    private fun resolve(): List<String> {
        val pm = ctx.packageManager
        val out = mutableListOf<String>()
        fun add(pkg: String?) { if (!pkg.isNullOrBlank()) out += pkg }
        fun resolveAll(intent: Intent) {
            runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let(::add)
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { add(it.activityInfo.packageName) }
            }
        }

        resolveAll(Intent(Intent.ACTION_DIAL))
        resolveAll(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        resolveAll(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        resolveAll(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        resolveAll(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        resolveAll(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=hospital")))
        resolveAll(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        resolveAll(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")))
        runCatching { add(Telephony.Sms.getDefaultSmsPackage(ctx)) }
        runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let(::add)
        }
        // Every installed keyboard, so typing a search never triggers a lock.
        runCatching {
            pm.queryIntentServices(Intent("android.view.InputMethod"), 0).forEach { add(it.serviceInfo.packageName) }
        }
        add(ctx.packageName)
        return out
    }
}

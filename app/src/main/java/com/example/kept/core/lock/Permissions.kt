package com.example.kept.core.lock

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PermissionKind { USAGE_ACCESS, OVERLAY, NOTIFICATIONS, BATTERY, CAMERA }

@Singleton
class Permissions @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun usageAccessGranted(): Boolean {
        val appOps = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_DEFAULT && ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
    }

    fun overlayGranted(): Boolean = Settings.canDrawOverlays(ctx)

    /** Both permissions the lock cannot work without. */
    fun lockPermissionsGranted(): Boolean = usageAccessGranted() && overlayGranted()

    fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryExempt(): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun granted(kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.USAGE_ACCESS -> usageAccessGranted()
        PermissionKind.OVERLAY -> overlayGranted()
        PermissionKind.NOTIFICATIONS -> notificationsGranted()
        PermissionKind.BATTERY -> batteryExempt()
        PermissionKind.CAMERA -> cameraGranted()
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayIntent(): Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryIntent(): Intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

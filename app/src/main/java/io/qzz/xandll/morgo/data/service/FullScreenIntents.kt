package io.qzz.xandll.morgo.data.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Ab Android 14 darf der Vollbild-Wecker extra freigegeben werden. */
object FullScreenIntents {
    fun needsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return needsPermission(Build.VERSION.SDK_INT, manager.canUseFullScreenIntent())
    }

    /** Ab Android 14 (API 34) ist das Vollbild eine eigene Freigabe. */
    fun needsPermission(sdkInt: Int, canUseFullScreenIntent: Boolean): Boolean {
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return !canUseFullScreenIntent
    }

    fun settingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:${context.packageName}"))
    }
}

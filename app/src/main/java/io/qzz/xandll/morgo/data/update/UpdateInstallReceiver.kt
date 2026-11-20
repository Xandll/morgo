package io.qzz.xandll.morgo.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Öffnet die Systembestätigung, falls Android sie noch verlangt. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        } ?: return
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(confirm)
    }
}

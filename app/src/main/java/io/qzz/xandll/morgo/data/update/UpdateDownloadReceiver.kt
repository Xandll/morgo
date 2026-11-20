package io.qzz.xandll.morgo.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UpdateDownloadReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: UpdateInstaller

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                installer.finishDownload(id)
            } finally {
                pending.finish()
            }
        }
    }
}

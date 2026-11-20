package io.qzz.xandll.morgo.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.qzz.xandll.morgo.data.worker.UpdateCheckWorker
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val DAILY = "morgo_update_daily"
    private const val NOW = "morgo_update_now"

    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(DAILY)
            work.cancelUniqueWork(NOW)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val daily = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
        val now = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(constraints)
            .build()
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, now)
    }
}

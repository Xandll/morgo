package io.qzz.xandll.morgo.data.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object AlarmRefresh {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AlarmSchedulerWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "alarm_reschedule",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

package io.qzz.xandll.morgo.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.qzz.xandll.morgo.data.update.UpdateCoordinator

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: UpdateCoordinator
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        coordinator.check(manual = false)
        return Result.success()
    }
}

package com.risediary.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val deliveryCoordinator: ReminderDeliveryCoordinator,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val type = ReminderType.fromStoredValue(inputData.getString(KEY_REMINDER_TYPE))
            ?: return Result.failure()
        return try {
            deliveryCoordinator.deliver(type)
            scheduler.rescheduleAfterFallback(type)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_REMINDER_TYPE = "reminder_type"
    }
}

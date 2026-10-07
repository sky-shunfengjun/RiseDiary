package com.risediary.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import com.risediary.app.data.DataMaintenanceBusyException

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
        val payload = inputData.getString(KEY_PLAN)
        val plan = payload?.let { runCatching { ReminderPlanCodec.decode(it) }.getOrNull() }
        if (payload != null && (plan == null || plan.type != type)) return Result.failure()
        return runReminderWork(
            deliver = { deliveryCoordinator.deliver(type, plan) },
            reschedule = { scheduler.rescheduleAfterFallback(type, plan?.id) }
        )
    }
    companion object {
        const val KEY_REMINDER_TYPE = "reminder_type"
        const val KEY_PLAN = "reminder_plan"
    }
}

internal suspend fun runReminderWork(
    deliver: suspend () -> Unit,
    reschedule: suspend () -> Unit
): ListenableWorker.Result = try {
    deliver()
    reschedule()
    ListenableWorker.Result.success()
} catch (_: DataMaintenanceBusyException) {
    ListenableWorker.Result.retry()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ListenableWorker.Result.retry()
}

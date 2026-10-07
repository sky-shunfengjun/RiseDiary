package com.risediary.app.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.repository.FlightRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen

@Singleton
class ReminderScheduler internal constructor(
    private val preferences: UserPreferences,
    private val flightRepository: FlightRepository,
    private val clock: Clock,
    private val platform: ReminderSchedulePlatform,
    private val plans: ReminderPlanRepository
) {
    @Inject internal constructor(@ApplicationContext context: Context, preferences: UserPreferences,
        flightRepository: FlightRepository, clock: Clock, plans: ReminderPlanStore) :
        this(preferences, flightRepository, clock, AndroidReminderSchedulePlatform(context), plans)

    constructor(context: Context, preferences: UserPreferences, flightRepository: FlightRepository,
        clock: Clock) : this(preferences, flightRepository, clock, AndroidReminderSchedulePlatform(context),
        ReminderPlanStore(context))

    suspend fun observeConfiguration() {
        preferences.strictReminderConfiguration
            .retryWhen { failure, _ ->
                if (failure is CancellationException) false else { delay(RETRY_MILLIS); true }
            }
            .distinctUntilChanged()
            .combine(preferences.maintenanceGate.state) { configuration, state -> configuration to state }
            .collectLatest { (_, state) ->
                if (state == DataMaintenanceGate.State.IDLE) {
                    var retryMillis = RETRY_MILLIS
                    while (true) {
                        try {
                            // Re-read after acquiring the permit: a restore may have changed it.
                            syncAll()
                            break
                        } catch (_: DataMaintenanceBusyException) {
                            break // The next IDLE transition will retry.
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // File IO, decoding or Android scheduling failures must not crash startup.
                            delay(retryMillis)
                            retryMillis = (retryMillis * 2).coerceAtMost(MAX_RETRY_MILLIS)
                        }
                    }
                }
            }
    }

    suspend fun syncAll(recalculate: Boolean = false) = preferences.maintenanceGate.write {
        val configuration = preferences.getReminderConfiguration()
        ReminderType.entries.forEach { sync(it, configuration, recalculate = recalculate) }
    }

    suspend fun sync(type: ReminderType) = preferences.maintenanceGate.write {
        sync(type, preferences.getReminderConfiguration())
    }

    suspend fun onReminderEnabledChanged(type: ReminderType, enabled: Boolean) =
        preferences.maintenanceGate.write {
            if (enabled) {
                sync(type)
            } else {
                cancel(type)
                if (!preferences.getReminderConfiguration().hasEnabledReminders) cancelBackgroundTest()
            }
        }

    suspend fun rescheduleAfterFallback(type: ReminderType, executingPlanId: String? = null) =
        preferences.maintenanceGate.write {
            sync(type, preferences.getReminderConfiguration(), executingPlanId = executingPlanId)
        }

    private suspend fun sync(type: ReminderType, configuration: ReminderConfiguration,
        recalculate: Boolean = false, executingPlanId: String? = null) {
        if (!configuration.isEnabled(type)) {
            cancel(type)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = Instant.now(clock).atZone(zone)
        val old = plans.load(type)
        val invalid = old != null && !old.matches(type, configuration, zone)
        if (recalculate || invalid) platform.cancelSchedule(type)

        val pending = old?.takeIf { !recalculate && !invalid && !it.consumed }
        val plan = pending ?: ReminderPlan(UUID.randomUUID().toString(), type,
            nextTarget(type, configuration, now).toInstant().toEpochMilli(), zone.id,
            configuration.planKey(type)).also {
            // An incomplete save must never create an anonymous alarm or fallback.
            plans.save(it)
        }
        val target = Instant.ofEpochMilli(plan.targetMillis).atZone(zone)
        val fallbackDelay = Duration.between(now, ReminderFallbackPolicy.target(target))
            .toMillis().coerceAtLeast(1L)
        val workPolicy = if (executingPlanId == plan.id) {
            // Clock rollback can make the running fallback early. Queue another attempt
            // after it completes rather than KEEP swallowing its own replacement.
            ExistingWorkPolicy.APPEND_OR_REPLACE
        } else ExistingWorkPolicy.KEEP
        platform.schedule(type, plan.targetMillis, fallbackDelay, workPolicy, plan)
    }

    private suspend fun nextTarget(type: ReminderType, configuration: ReminderConfiguration,
        now: ZonedDateTime): ZonedDateTime = when (type) {
        ReminderType.DAILY -> ReminderScheduleCalculator.nextDaily(now, configuration.time(type))
        ReminderType.MONTHLY_LENGTH -> ReminderScheduleCalculator.nextMonthly(now,
            configuration.monthlyLengthDay, configuration.time(type))
        ReminderType.INACTIVE -> {
            val runtime = preferences.getReminderRuntimeState()
            val latestRecordDate = flightRepository.getRecent(1).firstOrNull()?.let {
                Instant.ofEpochMilli(it.startTime).atZone(now.zone).toLocalDate()
            }
            val anchorDate = latestRecordDate ?: runtime.inactiveEnabledEpochDay.takeIf { it >= 0L }
                ?.let(LocalDate::ofEpochDay) ?: now.toLocalDate().also {
                    preferences.ensureInactiveReminderAnchor(it.toEpochDay())
                }
            val lastSentDate = runtime.inactiveLastSentEpochDay.takeIf { it >= 0L }?.let(LocalDate::ofEpochDay)
            ReminderScheduleCalculator.nextInactive(now, anchorDate, lastSentDate,
                configuration.inactiveDays, configuration.time(type))
        }
    }

    private suspend fun cancel(type: ReminderType) {
        platform.cancel(type)
        plans.clear(type)
    }
    fun exactAlarmsAllowed(): Boolean = platform.exactAlarmsAllowed()
    fun scheduleBackgroundTest(): Boolean = platform.scheduleBackgroundTest()
    fun cancelBackgroundTest() = platform.cancelBackgroundTest()

    suspend fun onFlightDataChanged() = preferences.maintenanceGate.write {
        cancel(ReminderType.DAILY)
        cancel(ReminderType.INACTIVE)
        resetInactiveAnchorForEmptyHistory()
        val configuration = preferences.getReminderConfiguration()
        sync(ReminderType.DAILY, configuration)
        sync(ReminderType.INACTIVE, configuration)
    }

    suspend fun onLengthDataChanged() = preferences.maintenanceGate.write {
        cancel(ReminderType.MONTHLY_LENGTH)
        sync(ReminderType.MONTHLY_LENGTH, preferences.getReminderConfiguration())
    }

    suspend fun onAllDataChanged() = preferences.maintenanceGate.write {
        ReminderType.entries.forEach { cancel(it) }
        cancelBackgroundTest()
        resetInactiveAnchorForEmptyHistory()
        syncAll()
    }

    private suspend fun resetInactiveAnchorForEmptyHistory() {
        val configuration = preferences.getReminderConfiguration()
        if (configuration.inactiveEnabled && flightRepository.getRecent(1).isEmpty()) {
            preferences.resetInactiveReminderAnchor(Instant.now(clock)
                .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())
        }
    }

    private companion object {
        const val RETRY_MILLIS = 1_000L
        const val MAX_RETRY_MILLIS = 30_000L
    }
}
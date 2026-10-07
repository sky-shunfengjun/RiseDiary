package com.risediary.app.reminder

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId

@Serializable
internal data class ReminderPlan(
    val id: String,
    val type: ReminderType,
    val targetMillis: Long,
    val zoneId: String,
    val configurationKey: String,
    val consumed: Boolean = false
)

internal interface ReminderPlanRepository {
    suspend fun load(type: ReminderType): ReminderPlan?
    suspend fun save(plan: ReminderPlan)
    suspend fun clear(type: ReminderType)
}

internal fun ReminderConfiguration.planKey(type: ReminderType): String = buildString {
    append(type.storedValue)
    append('|')
    append(time(type))
    when (type) {
        ReminderType.INACTIVE -> { append('|'); append(inactiveDays) }
        ReminderType.MONTHLY_LENGTH -> { append('|'); append(monthlyLengthDay) }
        ReminderType.DAILY -> Unit
    }
}

internal fun ReminderPlan.matches(type: ReminderType, configuration: ReminderConfiguration,
    zone: ZoneId): Boolean = this.type == type && zoneId == zone.id &&
    configurationKey == configuration.planKey(type)

internal fun ReminderPlan.sameOccurrence(other: ReminderPlan): Boolean =
    id == other.id && type == other.type && targetMillis == other.targetMillis &&
        zoneId == other.zoneId && configurationKey == other.configurationKey

/** Used unchanged by the persisted ledger, AlarmManager extras and WorkManager input. */
internal object ReminderPlanCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun encode(plan: ReminderPlan): String {
        validate(plan)
        return json.encodeToString(plan)
    }
    fun decode(payload: String): ReminderPlan {
        require(payload.length <= 2_048) { "Invalid reminder plan" }
        return json.decodeFromString<ReminderPlan>(payload).also(::validate)
    }
    private fun validate(plan: ReminderPlan) {
        require(plan.id.isNotBlank() && plan.id.length <= 128)
        require(plan.targetMillis > 0L)
        require(plan.zoneId.length <= 64)
        ZoneId.of(plan.zoneId)
        require(plan.configurationKey.isNotBlank() && plan.configurationKey.length <= 128)
    }
}

internal fun reminderWorkName(type: ReminderType, plan: ReminderPlan?): String =
    plan?.let { "${type.uniqueWorkName}_${it.id}" } ?: type.uniqueWorkName

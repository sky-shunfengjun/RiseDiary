package com.risediary.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.risediary.app.reminder.ReminderConfiguration
import com.risediary.app.reminder.ReminderRuntimeState
import com.risediary.app.reminder.ReminderType
import com.risediary.app.reminder.normalizeInactiveDays
import com.risediary.app.reminder.normalizeReminderTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class BackgroundLockMode(val storedValue: String) {
    ALWAYS("always"),
    EXCEPT_WHILE_TIMER_ACTIVE("except_while_timer_active");

    companion object {
        fun fromStoredValue(value: String?): BackgroundLockMode =
            entries.firstOrNull { it.storedValue == value } ?: ALWAYS
    }
}

enum class DefaultVolumeMode(val storedValue: String) {
    MILLILITERS("milliliters"),
    SPURTS("spurts");

    companion object {
        fun fromStoredValue(value: String?): DefaultVolumeMode =
            entries.firstOrNull { it.storedValue == value } ?: MILLILITERS
    }
}

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // --- Individual flows ---

    val username: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USERNAME] ?: "机长"
    }

    val mlPerSpurt: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_ML_PER_SPURT] ?: 2.0f
    }

    val defaultVolumeMode: Flow<DefaultVolumeMode> = context.dataStore.data.map { prefs ->
        DefaultVolumeMode.fromStoredValue(prefs[KEY_DEFAULT_VOLUME_MODE])
    }

    val dailyReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DAILY_REMINDER_ENABLED] ?: false
    }

    val dailyReminderTime: Flow<String> = context.dataStore.data.map { prefs ->
        normalizeReminderTime(
            prefs[KEY_DAILY_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
        )
    }

    val inactiveReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_INACTIVE_REMINDER_ENABLED] ?: false
    }

    val inactiveReminderDays: Flow<Int> = context.dataStore.data.map { prefs ->
        normalizeInactiveDays(
            prefs[KEY_INACTIVE_REMINDER_DAYS] ?: ReminderConfiguration.DEFAULT_INACTIVE_DAYS
        )
    }

    val inactiveReminderTime: Flow<String> = context.dataStore.data.map { prefs ->
        normalizeReminderTime(
            prefs[KEY_INACTIVE_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
        )
    }

    val monthlyLengthReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MONTHLY_LENGTH_REMINDER_ENABLED] ?: false
    }

    val monthlyLengthReminderDay: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_MONTHLY_LENGTH_REMINDER_DAY]
            ?: ReminderConfiguration.DEFAULT_MONTHLY_DAY).coerceIn(1, 28)
    }

    val monthlyLengthReminderTime: Flow<String> = context.dataStore.data.map { prefs ->
        normalizeReminderTime(
            prefs[KEY_MONTHLY_LENGTH_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
        )
    }

    val reminderSound: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REMINDER_SOUND] ?: true
    }

    val reminderVibration: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REMINDER_VIBRATION] ?: true
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_ENABLED] ?: false
    }

    val appLockPin: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_PIN] ?: ""
    }

    val appLockAttempts: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_ATTEMPTS] ?: 0
    }

    val appLockoutUntil: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCKOUT_UNTIL] ?: 0L
    }

    val biometricUnlockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_UNLOCK_ENABLED] ?: false
    }

    val backgroundAutoLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_AUTO_LOCK_ENABLED] ?: false
    }

    val backgroundLockMode: Flow<BackgroundLockMode> = context.dataStore.data.map { prefs ->
        BackgroundLockMode.fromStoredValue(prefs[KEY_BACKGROUND_LOCK_MODE])
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }

    val homeCardOrder: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOME_CARD_ORDER] ?: "[]"
    }

    val homeCardVisibility: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOME_CARD_VISIBILITY] ?: "{}"
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val defaultTagsInitialized: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TAGS_INITIALIZED] ?: false
    }

    val reminderConfiguration: Flow<ReminderConfiguration> =
        context.dataStore.data.map(::toReminderConfiguration)

    // --- Setters ---

    suspend fun setUsername(value: String) {
        context.dataStore.edit { it[KEY_USERNAME] = value }
    }

    suspend fun setMlPerSpurt(value: Float) {
        context.dataStore.edit { it[KEY_ML_PER_SPURT] = value }
    }

    suspend fun setDefaultVolumeMode(mode: DefaultVolumeMode) {
        context.dataStore.edit { it[KEY_DEFAULT_VOLUME_MODE] = mode.storedValue }
    }

    suspend fun setDailyReminder(enabled: Boolean, time: String? = null) {
        context.dataStore.edit {
            it[KEY_DAILY_REMINDER_ENABLED] = enabled
            if (time != null) {
                it[KEY_DAILY_REMINDER_TIME] = normalizeReminderTime(time)
            }
            if (!enabled) it.remove(KEY_DAILY_LAST_SENT_EPOCH_DAY)
        }
    }

    suspend fun setInactiveReminder(
        enabled: Boolean,
        days: Int? = null,
        time: String? = null
    ) {
        context.dataStore.edit { prefs ->
            val wasEnabled = prefs[KEY_INACTIVE_REMINDER_ENABLED] ?: false
            prefs[KEY_INACTIVE_REMINDER_ENABLED] = enabled
            if (days != null) {
                prefs[KEY_INACTIVE_REMINDER_DAYS] = normalizeInactiveDays(days)
            }
            if (time != null) {
                prefs[KEY_INACTIVE_REMINDER_TIME] = normalizeReminderTime(time)
            }
            if (enabled && !wasEnabled) {
                prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] =
                    LocalDate.now(ZoneId.systemDefault()).toEpochDay()
                prefs.remove(KEY_INACTIVE_LAST_SENT_EPOCH_DAY)
            } else if (!enabled) {
                prefs.remove(KEY_INACTIVE_ENABLED_EPOCH_DAY)
                prefs.remove(KEY_INACTIVE_LAST_SENT_EPOCH_DAY)
            }
        }
    }

    suspend fun setRecommendedReminders(enabled: Boolean, time: String) {
        val normalizedTime = normalizeReminderTime(time)
        val enabledEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        context.dataStore.edit { prefs ->
            val inactiveWasEnabled = prefs[KEY_INACTIVE_REMINDER_ENABLED] ?: false
            prefs[KEY_DAILY_REMINDER_ENABLED] = enabled
            prefs[KEY_DAILY_REMINDER_TIME] = normalizedTime
            prefs[KEY_INACTIVE_REMINDER_ENABLED] = enabled
            prefs[KEY_INACTIVE_REMINDER_DAYS] = 7
            prefs[KEY_INACTIVE_REMINDER_TIME] = normalizedTime
            prefs[KEY_MONTHLY_LENGTH_REMINDER_ENABLED] = false
            prefs.remove(KEY_MONTHLY_LAST_SENT)

            if (!enabled) {
                prefs.remove(KEY_DAILY_LAST_SENT_EPOCH_DAY)
                prefs.remove(KEY_INACTIVE_ENABLED_EPOCH_DAY)
                prefs.remove(KEY_INACTIVE_LAST_SENT_EPOCH_DAY)
            } else if (!inactiveWasEnabled) {
                prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] = enabledEpochDay
                prefs.remove(KEY_INACTIVE_LAST_SENT_EPOCH_DAY)
            }
        }
    }

    suspend fun setMonthlyLengthReminder(
        enabled: Boolean,
        day: Int? = null,
        time: String? = null
    ) {
        context.dataStore.edit {
            it[KEY_MONTHLY_LENGTH_REMINDER_ENABLED] = enabled
            if (day != null) it[KEY_MONTHLY_LENGTH_REMINDER_DAY] = day.coerceIn(1, 28)
            if (time != null) {
                it[KEY_MONTHLY_LENGTH_REMINDER_TIME] = normalizeReminderTime(time)
            }
            if (!enabled) it.remove(KEY_MONTHLY_LAST_SENT)
        }
    }

    suspend fun setReminderTime(type: ReminderType, time: String) {
        val normalized = normalizeReminderTime(time)
        context.dataStore.edit { prefs ->
            when (type) {
                ReminderType.DAILY ->
                    prefs[KEY_DAILY_REMINDER_TIME] = normalized
                ReminderType.INACTIVE ->
                    prefs[KEY_INACTIVE_REMINDER_TIME] = normalized
                ReminderType.MONTHLY_LENGTH ->
                    prefs[KEY_MONTHLY_LENGTH_REMINDER_TIME] = normalized
            }
        }
    }

    suspend fun setInactiveReminderDays(days: Int) {
        context.dataStore.edit {
            it[KEY_INACTIVE_REMINDER_DAYS] = normalizeInactiveDays(days)
        }
    }

    suspend fun setMonthlyLengthReminderDay(day: Int) {
        context.dataStore.edit {
            it[KEY_MONTHLY_LENGTH_REMINDER_DAY] = day.coerceIn(1, 28)
        }
    }

    suspend fun setReminderSound(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMINDER_SOUND] = enabled }
    }

    suspend fun setReminderVibration(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMINDER_VIBRATION] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null) {
        context.dataStore.edit {
            it[KEY_APP_LOCK_ENABLED] = enabled
            if (pin != null) it[KEY_APP_LOCK_PIN] = pin
            if (!enabled) {
                it[KEY_APP_LOCK_PIN] = ""
                it[KEY_APP_LOCK_ATTEMPTS] = 0
                it[KEY_APP_LOCKOUT_UNTIL] = 0L
                it[KEY_BIOMETRIC_UNLOCK_ENABLED] = false
            }
        }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val lockReady =
                prefs[KEY_APP_LOCK_ENABLED] == true &&
                    !prefs[KEY_APP_LOCK_PIN].isNullOrEmpty()
            prefs[KEY_BIOMETRIC_UNLOCK_ENABLED] = enabled && lockReady
        }
    }

    suspend fun setBackgroundAutoLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BACKGROUND_AUTO_LOCK_ENABLED] = enabled }
    }

    suspend fun setBackgroundLockMode(mode: BackgroundLockMode) {
        context.dataStore.edit { it[KEY_BACKGROUND_LOCK_MODE] = mode.storedValue }
    }

    suspend fun setOnboardingLockDefaults() {
        context.dataStore.edit {
            it[KEY_BACKGROUND_AUTO_LOCK_ENABLED] = true
            it[KEY_BACKGROUND_LOCK_MODE] =
                BackgroundLockMode.EXCEPT_WHILE_TIMER_ACTIVE.storedValue
        }
    }

    suspend fun setAppLockFailureState(attempts: Int, lockoutUntil: Long) {
        context.dataStore.edit {
            it[KEY_APP_LOCK_ATTEMPTS] = attempts.coerceAtLeast(0)
            it[KEY_APP_LOCKOUT_UNTIL] = lockoutUntil.coerceAtLeast(0L)
        }
    }

    suspend fun clearAppLockFailures() {
        setAppLockFailureState(attempts = 0, lockoutUntil = 0L)
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setHomeCardOrder(orderJson: String) {
        context.dataStore.edit { it[KEY_HOME_CARD_ORDER] = orderJson }
    }

    suspend fun setHomeCardVisibility(visibilityJson: String) {
        context.dataStore.edit { it[KEY_HOME_CARD_VISIBILITY] = visibilityJson }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun finishOnboarding(firstRun: Boolean, reminderTime: String?) {
        val normalizedTime = reminderTime?.let(::normalizeReminderTime)
        context.dataStore.edit {
            if (normalizedTime != null) {
                it[KEY_DAILY_REMINDER_TIME] = normalizedTime
                it[KEY_INACTIVE_REMINDER_TIME] = normalizedTime
            }
            if (firstRun) it[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun markDefaultTagsInitialized() {
        context.dataStore.edit { it[KEY_DEFAULT_TAGS_INITIALIZED] = true }
    }

    suspend fun getReminderRuntimeState(): ReminderRuntimeState {
        val prefs = context.dataStore.data.first()
        return ReminderRuntimeState(
            inactiveEnabledEpochDay =
                prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] ?: ReminderRuntimeState.UNSET_EPOCH_DAY,
            dailyLastSentEpochDay =
                prefs[KEY_DAILY_LAST_SENT_EPOCH_DAY] ?: ReminderRuntimeState.UNSET_EPOCH_DAY,
            inactiveLastSentEpochDay =
                prefs[KEY_INACTIVE_LAST_SENT_EPOCH_DAY] ?: ReminderRuntimeState.UNSET_EPOCH_DAY,
            monthlyLastSent = prefs[KEY_MONTHLY_LAST_SENT] ?: ""
        )
    }

    suspend fun ensureInactiveReminderAnchor(epochDay: Long) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] == null) {
                prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] = epochDay
            }
        }
    }

    suspend fun resetInactiveReminderAnchor(epochDay: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] = epochDay
            prefs.remove(KEY_INACTIVE_LAST_SENT_EPOCH_DAY)
        }
    }

    suspend fun markDailyReminderSent(epochDay: Long) {
        context.dataStore.edit { it[KEY_DAILY_LAST_SENT_EPOCH_DAY] = epochDay }
    }

    suspend fun markInactiveReminderSent(epochDay: Long) {
        context.dataStore.edit { it[KEY_INACTIVE_LAST_SENT_EPOCH_DAY] = epochDay }
    }

    suspend fun markMonthlyReminderSent(yearMonth: String) {
        context.dataStore.edit { it[KEY_MONTHLY_LAST_SENT] = yearMonth }
    }

    private fun toReminderConfiguration(prefs: Preferences): ReminderConfiguration =
        ReminderConfiguration(
            dailyEnabled = prefs[KEY_DAILY_REMINDER_ENABLED] ?: false,
            dailyTime = normalizeReminderTime(
                prefs[KEY_DAILY_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
            ),
            inactiveEnabled = prefs[KEY_INACTIVE_REMINDER_ENABLED] ?: false,
            inactiveDays = normalizeInactiveDays(
                prefs[KEY_INACTIVE_REMINDER_DAYS]
                    ?: ReminderConfiguration.DEFAULT_INACTIVE_DAYS
            ),
            inactiveTime = normalizeReminderTime(
                prefs[KEY_INACTIVE_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
            ),
            monthlyLengthEnabled =
                prefs[KEY_MONTHLY_LENGTH_REMINDER_ENABLED] ?: false,
            monthlyLengthDay = (
                prefs[KEY_MONTHLY_LENGTH_REMINDER_DAY]
                    ?: ReminderConfiguration.DEFAULT_MONTHLY_DAY
                ).coerceIn(1, 28),
            monthlyLengthTime = normalizeReminderTime(
                prefs[KEY_MONTHLY_LENGTH_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
            )
        )

    // --- Preference Keys ---

    companion object {
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_ML_PER_SPURT = floatPreferencesKey("ml_per_spurt")
        private val KEY_DEFAULT_VOLUME_MODE = stringPreferencesKey("default_volume_mode")
        private val KEY_DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        private val KEY_DAILY_REMINDER_TIME = stringPreferencesKey("daily_reminder_time")
        private val KEY_INACTIVE_REMINDER_ENABLED = booleanPreferencesKey("inactive_reminder_enabled")
        private val KEY_INACTIVE_REMINDER_DAYS = intPreferencesKey("inactive_reminder_days")
        private val KEY_INACTIVE_REMINDER_TIME = stringPreferencesKey("inactive_reminder_time")
        private val KEY_MONTHLY_LENGTH_REMINDER_ENABLED = booleanPreferencesKey("monthly_length_reminder_enabled")
        private val KEY_MONTHLY_LENGTH_REMINDER_DAY = intPreferencesKey("monthly_length_reminder_day")
        private val KEY_MONTHLY_LENGTH_REMINDER_TIME =
            stringPreferencesKey("monthly_length_reminder_time")
        private val KEY_REMINDER_SOUND = booleanPreferencesKey("reminder_sound")
        private val KEY_REMINDER_VIBRATION = booleanPreferencesKey("reminder_vibration")
        private val KEY_INACTIVE_ENABLED_EPOCH_DAY =
            longPreferencesKey("inactive_reminder_enabled_epoch_day")
        private val KEY_DAILY_LAST_SENT_EPOCH_DAY =
            longPreferencesKey("daily_reminder_last_sent_epoch_day")
        private val KEY_INACTIVE_LAST_SENT_EPOCH_DAY =
            longPreferencesKey("inactive_reminder_last_sent_epoch_day")
        private val KEY_MONTHLY_LAST_SENT =
            stringPreferencesKey("monthly_length_reminder_last_sent")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        private val KEY_APP_LOCK_ATTEMPTS = intPreferencesKey("app_lock_attempts")
        private val KEY_APP_LOCKOUT_UNTIL = longPreferencesKey("app_lockout_until")
        private val KEY_BIOMETRIC_UNLOCK_ENABLED =
            booleanPreferencesKey("biometric_unlock_enabled")
        private val KEY_BACKGROUND_AUTO_LOCK_ENABLED =
            booleanPreferencesKey("background_auto_lock_enabled")
        private val KEY_BACKGROUND_LOCK_MODE = stringPreferencesKey("background_lock_mode")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_HOME_CARD_ORDER = stringPreferencesKey("home_card_order")
        private val KEY_HOME_CARD_VISIBILITY = stringPreferencesKey("home_card_visibility")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_DEFAULT_TAGS_INITIALIZED =
            booleanPreferencesKey("default_tags_initialized")
    }
}

package com.risediary.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import com.risediary.app.reminder.ReminderConfiguration
import com.risediary.app.reminder.ReminderRuntimeState
import com.risediary.app.reminder.ReminderType
import com.risediary.app.reminder.normalizeInactiveDays
import com.risediary.app.reminder.normalizeReminderTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings DataStore with a real corruption handler: a corrupt file is replaced
 * by empty preferences and both reads and writes keep working afterwards.
 * The file path matches the default `preferencesDataStoreFile("settings")`
 * location so existing data keeps loading after this change.
 */
private fun settingsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = {
            File(context.filesDir, "datastore/settings.preferences_pb").apply { parentFile?.mkdirs() }
        }
    )

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

    private val dataStore: DataStore<Preferences> = settingsDataStore(context)

    /**
     * Wraps the raw DataStore flow. File corruption is repaired transparently by
     * the DataStore corruption handler; this catch only guards against transient
     * IO failures by falling back to defaults for display.
     */
    private val safeData: Flow<Preferences> = dataStore.data.catch { error: Throwable ->
        if (error is IOException) {
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    // --- Individual flows ---

    val username: Flow<String> = safeData.map { prefs ->
        UsernamePolicy.normalize(prefs[KEY_USERNAME].orEmpty())
    }

    val mlPerSpurt: Flow<Float> = safeData.map { prefs ->
        prefs[KEY_ML_PER_SPURT] ?: 2.0f
    }

    val defaultVolumeMode: Flow<DefaultVolumeMode> = safeData.map { prefs ->
        DefaultVolumeMode.fromStoredValue(prefs[KEY_DEFAULT_VOLUME_MODE])
    }

    val dailyReminderEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_DAILY_REMINDER_ENABLED] ?: false
    }

    val dailyReminderTime: Flow<String> = safeData.map { prefs ->
        normalizeReminderTime(
            prefs[KEY_DAILY_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
        )
    }

    val inactiveReminderEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_INACTIVE_REMINDER_ENABLED] ?: false
    }

    val inactiveReminderDays: Flow<Int> = safeData.map { prefs ->
        normalizeInactiveDays(
            prefs[KEY_INACTIVE_REMINDER_DAYS] ?: ReminderConfiguration.DEFAULT_INACTIVE_DAYS
        )
    }

    val inactiveReminderTime: Flow<String> = safeData.map { prefs ->
        normalizeReminderTime(
            prefs[KEY_INACTIVE_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
        )
    }

    val monthlyLengthReminderEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_MONTHLY_LENGTH_REMINDER_ENABLED] ?: false
    }

    val monthlyLengthReminderDay: Flow<Int> = safeData.map { prefs ->
        (prefs[KEY_MONTHLY_LENGTH_REMINDER_DAY]
            ?: ReminderConfiguration.DEFAULT_MONTHLY_DAY).coerceIn(1, 28)
    }

    val monthlyLengthReminderTime: Flow<String> = safeData.map { prefs ->
        normalizeReminderTime(
            prefs[KEY_MONTHLY_LENGTH_REMINDER_TIME] ?: ReminderConfiguration.DEFAULT_TIME
        )
    }

    val reminderSound: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_REMINDER_SOUND] ?: true
    }

    val reminderVibration: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_REMINDER_VIBRATION] ?: true
    }

    val appLockEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_APP_LOCK_ENABLED] ?: false
    }

    val appLockPin: Flow<String> = safeData.map { prefs ->
        prefs[KEY_APP_LOCK_PIN] ?: ""
    }

    val appLockAttempts: Flow<Int> = safeData.map { prefs ->
        prefs[KEY_APP_LOCK_ATTEMPTS] ?: 0
    }

    val appLockoutUntil: Flow<Long> = safeData.map { prefs ->
        prefs[KEY_APP_LOCKOUT_UNTIL] ?: 0L
    }

    val biometricUnlockEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_BIOMETRIC_UNLOCK_ENABLED] ?: false
    }

    val backgroundAutoLockEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_BACKGROUND_AUTO_LOCK_ENABLED] ?: false
    }

    val backgroundLockMode: Flow<BackgroundLockMode> = safeData.map { prefs ->
        BackgroundLockMode.fromStoredValue(prefs[KEY_BACKGROUND_LOCK_MODE])
    }

    val themeMode: Flow<String> = safeData.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }

    val homeCardOrder: Flow<String> = safeData.map { prefs ->
        prefs[KEY_HOME_CARD_ORDER] ?: "[]"
    }

    val homeCardVisibility: Flow<String> = safeData.map { prefs ->
        prefs[KEY_HOME_CARD_VISIBILITY] ?: "{}"
    }

    val onboardingCompleted: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val defaultTagsInitialized: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_DEFAULT_TAGS_INITIALIZED] ?: false
    }

    val reminderConfiguration: Flow<ReminderConfiguration> =
        safeData.map(::toReminderConfiguration)

    // --- Setters ---

    suspend fun setUsername(value: String) {
        dataStore.edit { it[KEY_USERNAME] = UsernamePolicy.normalize(value) }
    }

    suspend fun setMlPerSpurt(value: Float) {
        dataStore.edit { it[KEY_ML_PER_SPURT] = value }
    }

    suspend fun setDefaultVolumeMode(mode: DefaultVolumeMode) {
        dataStore.edit { it[KEY_DEFAULT_VOLUME_MODE] = mode.storedValue }
    }

    suspend fun setDailyReminder(enabled: Boolean, time: String? = null) {
        dataStore.edit {
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
        dataStore.edit { prefs ->
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
        dataStore.edit { prefs ->
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
        dataStore.edit {
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
        dataStore.edit { prefs ->
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
        dataStore.edit {
            it[KEY_INACTIVE_REMINDER_DAYS] = normalizeInactiveDays(days)
        }
    }

    suspend fun setMonthlyLengthReminderDay(day: Int) {
        dataStore.edit {
            it[KEY_MONTHLY_LENGTH_REMINDER_DAY] = day.coerceIn(1, 28)
        }
    }

    suspend fun setReminderSound(enabled: Boolean) {
        dataStore.edit { it[KEY_REMINDER_SOUND] = enabled }
    }

    suspend fun setReminderVibration(enabled: Boolean) {
        dataStore.edit { it[KEY_REMINDER_VIBRATION] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null) {
        dataStore.edit {
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
        dataStore.edit { prefs ->
            val lockReady =
                prefs[KEY_APP_LOCK_ENABLED] == true &&
                    !prefs[KEY_APP_LOCK_PIN].isNullOrEmpty()
            prefs[KEY_BIOMETRIC_UNLOCK_ENABLED] = enabled && lockReady
        }
    }

    suspend fun setBackgroundAutoLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKGROUND_AUTO_LOCK_ENABLED] = enabled }
    }

    suspend fun setBackgroundLockMode(mode: BackgroundLockMode) {
        dataStore.edit { it[KEY_BACKGROUND_LOCK_MODE] = mode.storedValue }
    }

    suspend fun setOnboardingLockDefaults() {
        dataStore.edit {
            it[KEY_BACKGROUND_AUTO_LOCK_ENABLED] = true
            it[KEY_BACKGROUND_LOCK_MODE] =
                BackgroundLockMode.EXCEPT_WHILE_TIMER_ACTIVE.storedValue
        }
    }

    suspend fun setAppLockFailureState(attempts: Int, lockoutUntil: Long) {
        dataStore.edit {
            it[KEY_APP_LOCK_ATTEMPTS] = attempts.coerceAtLeast(0)
            it[KEY_APP_LOCKOUT_UNTIL] = lockoutUntil.coerceAtLeast(0L)
        }
    }

    suspend fun clearAppLockFailures() {
        setAppLockFailureState(attempts = 0, lockoutUntil = 0L)
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setHomeCardOrder(orderJson: String) {
        dataStore.edit { it[KEY_HOME_CARD_ORDER] = orderJson }
    }

    suspend fun setHomeCardVisibility(visibilityJson: String) {
        dataStore.edit { it[KEY_HOME_CARD_VISIBILITY] = visibilityJson }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun finishOnboarding(firstRun: Boolean, reminderTime: String?) {
        val normalizedTime = reminderTime?.let(::normalizeReminderTime)
        dataStore.edit {
            if (normalizedTime != null) {
                it[KEY_DAILY_REMINDER_TIME] = normalizedTime
                it[KEY_INACTIVE_REMINDER_TIME] = normalizedTime
            }
            if (firstRun) it[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun markDefaultTagsInitialized() {
        dataStore.edit { it[KEY_DEFAULT_TAGS_INITIALIZED] = true }
    }

    /**
     * Restores a full settings snapshot in a single atomic DataStore write so a
     * failure mid-restore cannot leave a half-old/half-new mix.
     */
    suspend fun applySettingsSnapshot(settings: com.risediary.app.data.backup.SettingsSnapshot) {
        dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = UsernamePolicy.normalize(settings.username)
            prefs[KEY_ML_PER_SPURT] = settings.mlPerSpurt
            prefs[KEY_DEFAULT_VOLUME_MODE] = settings.defaultVolumeMode.storedValue
            prefs[KEY_DAILY_REMINDER_ENABLED] = settings.dailyReminderEnabled
            prefs[KEY_DAILY_REMINDER_TIME] = normalizeReminderTime(settings.dailyReminderTime)
            prefs[KEY_INACTIVE_REMINDER_ENABLED] = settings.inactiveReminderEnabled
            prefs[KEY_INACTIVE_REMINDER_DAYS] = normalizeInactiveDays(settings.inactiveReminderDays)
            prefs[KEY_INACTIVE_REMINDER_TIME] =
                normalizeReminderTime(settings.inactiveReminderTime)
            prefs[KEY_MONTHLY_LENGTH_REMINDER_ENABLED] =
                settings.monthlyLengthReminderEnabled
            prefs[KEY_MONTHLY_LENGTH_REMINDER_DAY] =
                settings.monthlyLengthReminderDay.coerceIn(1, 28)
            prefs[KEY_MONTHLY_LENGTH_REMINDER_TIME] =
                normalizeReminderTime(settings.monthlyLengthReminderTime)
            prefs[KEY_REMINDER_SOUND] = settings.reminderSound
            prefs[KEY_REMINDER_VIBRATION] = settings.reminderVibration
            prefs[KEY_BACKGROUND_AUTO_LOCK_ENABLED] = settings.backgroundAutoLockEnabled
            prefs[KEY_BACKGROUND_LOCK_MODE] = settings.backgroundLockMode.storedValue
            prefs[KEY_THEME_MODE] = settings.themeMode
            prefs[KEY_HOME_CARD_ORDER] = settings.homeCardOrder
            prefs[KEY_HOME_CARD_VISIBILITY] = settings.homeCardVisibility
            prefs[KEY_ONBOARDING_COMPLETED] = settings.onboardingCompleted
            // Reminder runtime state is device-local: clear stale sent-marks from
            // the previous device so reminders are not suppressed after restore,
            // and re-anchor the inactive reminder window.
            prefs[KEY_DAILY_LAST_SENT_EPOCH_DAY] = ReminderRuntimeState.UNSET_EPOCH_DAY
            prefs[KEY_INACTIVE_LAST_SENT_EPOCH_DAY] = ReminderRuntimeState.UNSET_EPOCH_DAY
            prefs[KEY_MONTHLY_LAST_SENT] = ""
            if (settings.inactiveReminderEnabled) {
                prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] = LocalDate.now().toEpochDay()
            } else {
                prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] = ReminderRuntimeState.UNSET_EPOCH_DAY
            }
        }
    }

    suspend fun getReminderRuntimeState(): ReminderRuntimeState {
        val prefs = safeData.first()
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
        dataStore.edit { prefs ->
            if (prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] == null) {
                prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] = epochDay
            }
        }
    }

    suspend fun resetInactiveReminderAnchor(epochDay: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_INACTIVE_ENABLED_EPOCH_DAY] = epochDay
            prefs.remove(KEY_INACTIVE_LAST_SENT_EPOCH_DAY)
        }
    }

    suspend fun markDailyReminderSent(epochDay: Long) {
        dataStore.edit { it[KEY_DAILY_LAST_SENT_EPOCH_DAY] = epochDay }
    }

    suspend fun markInactiveReminderSent(epochDay: Long) {
        dataStore.edit { it[KEY_INACTIVE_LAST_SENT_EPOCH_DAY] = epochDay }
    }

    suspend fun markMonthlyReminderSent(yearMonth: String) {
        dataStore.edit { it[KEY_MONTHLY_LAST_SENT] = yearMonth }
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

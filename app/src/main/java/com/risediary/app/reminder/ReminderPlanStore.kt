package com.risediary.app.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Scheduling metadata only; neither personal records nor backup fields live here. */
@Singleton
internal class ReminderPlanStore internal constructor(private val store: DataStore<Preferences>) : ReminderPlanRepository {
    @Inject constructor(@ApplicationContext context: Context) : this(planDataStore(context))

    override suspend fun load(type: ReminderType): ReminderPlan? {
        val payload = store.data.first()[key(type)] ?: return null
        return try {
            ReminderPlanCodec.decode(payload).also { require(it.type == type) }
        } catch (invalid: IllegalArgumentException) {
            // A failed read must not silently create a replacement plan.
            throw IOException("Cannot read reminder plan", invalid)
        }
    }

    override suspend fun save(plan: ReminderPlan) {
        val payload = ReminderPlanCodec.encode(plan)
        store.edit { it[key(plan.type)] = payload }
    }

    override suspend fun clear(type: ReminderType) {
        store.edit { it.remove(key(type)) }
    }

    private fun key(type: ReminderType) = stringPreferencesKey("plan_${type.storedValue}")

    private companion object {
        // Directly constructed Android test fixtures and Hilt share one instance per file.
        val stores = mutableMapOf<String, DataStore<Preferences>>()
        fun planDataStore(context: Context): DataStore<Preferences> = synchronized(stores) {
            val file = File(context.applicationContext.filesDir, "datastore/reminder_plans.preferences_pb")
            stores.getOrPut(file.absolutePath) {
                PreferenceDataStoreFactory.create(produceFile = {
                    file.apply { parentFile?.mkdirs() }
                })
            }
        }
    }
}

package com.xc.air3upgrader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

data class Interval(val days: Int, val hours: Int, val minutes: Int)

class DataStoreManager(private val context: Context) {

    // Create the DataStore
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val UPGRADE_CHECK_INTERVAL_DAYS = intPreferencesKey("upgrade_check_interval_days")
        val UPGRADE_CHECK_INTERVAL_HOURS = intPreferencesKey("upgrade_check_interval_hours")
        val UPGRADE_CHECK_INTERVAL_MINUTES = intPreferencesKey("upgrade_check_interval_minutes")
        val LAST_CHECK_TIME = longPreferencesKey("last_check_time") // New key
    }

    // Save the selected model
    suspend fun saveSelectedModel(selectedModel: String?) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MODEL] = selectedModel ?: ""
        }
    }

    // Get the selected model
    fun getSelectedModel(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[SELECTED_MODEL]
        }
    }
    fun isDeviceModelSupported(selectedModel: String?, allowedModels: List<String>): Boolean {
        return allowedModels.contains(selectedModel)
    }
    suspend fun saveUpgradeCheckInterval(interval: Interval) {
        context.dataStore.edit { preferences ->
            preferences[UPGRADE_CHECK_INTERVAL_DAYS] = interval.days
            preferences[UPGRADE_CHECK_INTERVAL_HOURS] = interval.hours
            preferences[UPGRADE_CHECK_INTERVAL_MINUTES] = interval.minutes
        }
    }

    fun getUpgradeCheckInterval(): Flow<Interval> {
        return context.dataStore.data.map { preferences ->
            val days = preferences[UPGRADE_CHECK_INTERVAL_DAYS] ?: 0
            val hours = preferences[UPGRADE_CHECK_INTERVAL_HOURS] ?: 0
            val minutes = preferences[UPGRADE_CHECK_INTERVAL_MINUTES] ?: 0
            Interval(days, hours, minutes)
        }
    }
    suspend fun saveLastCheckTime(lastCheckTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CHECK_TIME] = lastCheckTime
        }
    }
    fun getLastCheckTime(): Flow<Long?> {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_CHECK_TIME]
        }
    }
}
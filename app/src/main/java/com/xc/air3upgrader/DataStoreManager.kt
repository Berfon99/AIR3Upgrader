package com.xc.air3upgrader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class DataStoreManager(private val context: Context) {

    // At the top level of your kotlin file:
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val UPGRADE_CHECK_INTERVAL_DAYS = intPreferencesKey("upgrade_check_interval_days")
        val UPGRADE_CHECK_INTERVAL_HOURS = intPreferencesKey("upgrade_check_interval_hours")
        val UPGRADE_CHECK_INTERVAL_MINUTES = intPreferencesKey("upgrade_check_interval_minutes")
        val LAST_CHECK_TIME = longPreferencesKey("last_check_time")
        val IS_UPGRADE_CHECK_ENABLED = booleanPreferencesKey("is_upgrade_check_enabled")
        val SHOULD_LAUNCH_ON_REBOOT = booleanPreferencesKey("should_launch_on_reboot") // <--- New key
    }

    suspend fun saveSelectedModel(model: String) {
        Timber.d("DataStoreManager: saveSelectedModel called")
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MODEL] = model
        }
    }

    fun getSelectedModel(): Flow<String?> {
        Timber.d("DataStoreManager: getSelectedModel called")
        return context.dataStore.data.map { preferences ->
            preferences[SELECTED_MODEL]
        }
    }

    suspend fun saveUpgradeCheckInterval(interval: Interval) {
        Timber.d("DataStoreManager: saveUpgradeCheckInterval called - interval: $interval")
        context.dataStore.edit { preferences ->
            preferences[UPGRADE_CHECK_INTERVAL_DAYS] = interval.days
            preferences[UPGRADE_CHECK_INTERVAL_HOURS] = interval.hours
            preferences[UPGRADE_CHECK_INTERVAL_MINUTES] = interval.minutes
        }
    }

    fun getUpgradeCheckInterval(): Flow<Interval> {
        Timber.d("DataStoreManager: getUpgradeCheckInterval called")
        return context.dataStore.data.map { preferences ->
            val days = preferences[UPGRADE_CHECK_INTERVAL_DAYS] ?: 0
            val hours = preferences[UPGRADE_CHECK_INTERVAL_HOURS] ?: 0
            val minutes = preferences[UPGRADE_CHECK_INTERVAL_MINUTES] ?: 0
            val interval = Interval(days, hours, minutes)
            Timber.d("DataStoreManager: getUpgradeCheckInterval - interval: $interval")
            interval
        }
    }

    suspend fun saveLastCheckTime(time: Long) {
        Timber.d("DataStoreManager: saveLastCheckTime called - lastCheckTime: $time")
        context.dataStore.edit { preferences ->
            preferences[LAST_CHECK_TIME] = time
        }
    }

    fun getLastCheckTime(): Flow<Long?> {
        Timber.d("DataStoreManager: getLastCheckTime called")
        return context.dataStore.data.map { preferences ->
            preferences[LAST_CHECK_TIME]
        }
    }
    suspend fun saveIsUpgradeCheckEnabled(isEnabled: Boolean) { // <--- Add this function
        Timber.d("DataStoreManager: saveIsUpgradeCheckEnabled called - isEnabled: $isEnabled")
        context.dataStore.edit { preferences ->
            preferences[IS_UPGRADE_CHECK_ENABLED] = isEnabled
        }
    }

    fun getIsUpgradeCheckEnabled(): Flow<Boolean> { // <--- Add this function
        Timber.d("DataStoreManager: getIsUpgradeCheckEnabled called")
        return context.dataStore.data.map { preferences ->
            preferences[IS_UPGRADE_CHECK_ENABLED] ?: false
        }
    }

    fun isDeviceModelSupported(model: String, allowedModels: List<String>): Boolean {
        Timber.d("DataStoreManager: isDeviceModelSupported called")
        return allowedModels.contains(model)
    }
    suspend fun saveShouldLaunchOnReboot(shouldLaunch: Boolean) {
        Timber.d("DataStoreManager: saveShouldLaunchOnReboot called - shouldLaunch: $shouldLaunch")
        context.dataStore.edit { preferences ->
            preferences[SHOULD_LAUNCH_ON_REBOOT] = shouldLaunch
        }
    }

    fun getShouldLaunchOnReboot(): Flow<Boolean> {
        Timber.d("DataStoreManager: getShouldLaunchOnReboot called")
        return context.dataStore.data.map { preferences ->
            preferences[SHOULD_LAUNCH_ON_REBOOT] ?: false
        }
    }
}
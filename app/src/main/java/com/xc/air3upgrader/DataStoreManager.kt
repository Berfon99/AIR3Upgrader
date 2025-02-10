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

        // Define PreferencesKeys as an object
        object PreferencesKeys {
            val SELECTED_MODEL = stringPreferencesKey("selected_model")
            val UPGRADE_CHECK_INTERVAL_DAYS = intPreferencesKey("upgrade_check_interval_days")
            val UPGRADE_CHECK_INTERVAL_HOURS = intPreferencesKey("upgrade_check_interval_hours")
            val UPGRADE_CHECK_INTERVAL_MINUTES = intPreferencesKey("upgrade_check_interval_minutes")
            val LAST_CHECK_TIME = longPreferencesKey("last_check_time")
            val UNHIDDEN_LAUNCH_ON_REBOOT = booleanPreferencesKey("unhidden_launch_on_reboot")
            val AUTOMATIC_UPGRADE_REMINDER = booleanPreferencesKey("automatic_upgrade_reminder")
            val IS_MANUAL_LAUNCH = booleanPreferencesKey("is_manual_launch")
            val WIFI_ONLY = booleanPreferencesKey("wifi_only") // New key for Wi-Fi only setting
        }
    }

    fun isDeviceModelSupported(model: String, allowedModels: List<String>): Boolean {
        Timber.d("DataStoreManager: isDeviceModelSupported called")
        return allowedModels.contains(model)
    }

    suspend fun saveSelectedModel(model: String) {
        Timber.d("DataStoreManager: saveSelectedModel called")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL] = model
        }
    }

    fun getSelectedModel(): Flow<String?> {
        Timber.d("DataStoreManager: getSelectedModel called")
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL]
        }
    }

    suspend fun saveUpgradeCheckInterval(interval: Interval) {
        Timber.d("DataStoreManager: saveUpgradeCheckInterval called - interval: $interval")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_DAYS] = interval.days
            preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_HOURS] = interval.hours
            preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_MINUTES] = interval.minutes
        }
    }

    fun getUpgradeCheckInterval(): Flow<Interval> {
        Timber.d("DataStoreManager: getUpgradeCheckInterval called")
        return context.dataStore.data.map { preferences ->
            val days = preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_DAYS] ?: 0
            val hours = preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_HOURS] ?: 0
            val minutes = preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_MINUTES] ?: 0
            val interval = Interval(days, hours, minutes)
            Timber.d("DataStoreManager: getUpgradeCheckInterval - interval: $interval")
            interval
        }
    }

    suspend fun saveLastCheckTime(time: Long) {
        Timber.d("DataStoreManager: saveLastCheckTime called - lastCheckTime: $time")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CHECK_TIME] = time
        }
    }

    fun getLastCheckTime(): Flow<Long?> {
        Timber.d("DataStoreManager: getLastCheckTime called")
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_CHECK_TIME]
        }
    }

    suspend fun saveUnhiddenLaunchOnReboot(unhiddenLaunch: Boolean) {
        Timber.d("DataStoreManager: saveUnhiddenLaunchOnReboot called - unhiddenLaunch: $unhiddenLaunch")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNHIDDEN_LAUNCH_ON_REBOOT] = unhiddenLaunch
        }
    }

    fun getUnhiddenLaunchOnReboot(): Flow<Boolean> {
        Timber.d("DataStoreManager: getUnhiddenLaunchOnReboot called")
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UNHIDDEN_LAUNCH_ON_REBOOT] ?: false // Default to false
        }
    }

    suspend fun saveAutomaticUpgradeReminder(isEnabled: Boolean) {
        Timber.d("DataStoreManager: saveAutomaticUpgradeReminder called - isEnabled: $isEnabled")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOMATIC_UPGRADE_REMINDER] = isEnabled
        }
    }

    fun getAutomaticUpgradeReminder(): Flow<Boolean> {
        Timber.d("DataStoreManager: getAutomaticUpgradeReminder called")
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTOMATIC_UPGRADE_REMINDER] ?: false // Default to false
        }
    }

    suspend fun saveIsManualLaunch(isManualLaunch: Boolean) {
        Timber.d("DataStoreManager: saveIsManualLaunch called - isManualLaunch: $isManualLaunch")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_MANUAL_LAUNCH] = isManualLaunch
        }
    }

    fun getIsManualLaunch(): Flow<Boolean> {
        Timber.d("DataStoreManager: getIsManualLaunch called")
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IS_MANUAL_LAUNCH] ?: false // Default to false
        }
    }

    suspend fun removeLastCheckTime() {
        Timber.d("DataStoreManager: removeLastCheckTime called")
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LAST_CHECK_TIME)
        }
    }

    suspend fun saveWifiOnly(isWifiOnly: Boolean) {
        Timber.d("DataStoreManager: saveWifiOnly called - isWifiOnly: $isWifiOnly")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] = isWifiOnly
        }
    }

    fun getWifiOnly(): Flow<Boolean> {
        Timber.d("DataStoreManager: getWifiOnly called")
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] ?: false // Default to false
        }
    }
}
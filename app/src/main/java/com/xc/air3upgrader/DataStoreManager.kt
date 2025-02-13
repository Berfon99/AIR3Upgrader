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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class DataStoreManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

        object PreferencesKeys {
            val SELECTED_MODEL = stringPreferencesKey("selected_model")
            val UPGRADE_CHECK_INTERVAL_DAYS = intPreferencesKey("upgrade_check_interval_days")
            val UPGRADE_CHECK_INTERVAL_HOURS = intPreferencesKey("upgrade_check_interval_hours")
            val UPGRADE_CHECK_INTERVAL_MINUTES = intPreferencesKey("upgrade_check_interval_minutes")
            val LAST_CHECK_TIME = longPreferencesKey("last_check_time")
            val UNHIDDEN_LAUNCH_ON_REBOOT = booleanPreferencesKey("unhidden_launch_on_reboot")
            val AUTOMATIC_UPGRADE_REMINDER = booleanPreferencesKey("automatic_upgrade_reminder")
            val IS_MANUAL_LAUNCH = booleanPreferencesKey("is_manual_launch")
            val WIFI_ONLY = booleanPreferencesKey("wifi_only")
            val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        }
    }

    fun isDeviceModelSupported(model: String, allowedModels: List<String>): Boolean {
        return allowedModels.contains(model)
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL] = model
        }
    }

    fun getSelectedModel(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL]
        }
    }

    suspend fun saveUpgradeCheckInterval(interval: Interval) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_DAYS] = interval.days
            preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_HOURS] = interval.hours
            preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_MINUTES] = interval.minutes
        }
    }

    fun getUpgradeCheckInterval(): Flow<Interval> {
        return context.dataStore.data.map { preferences ->
            val days = preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_DAYS] ?: 0
            val hours = preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_HOURS] ?: 0
            val minutes = preferences[PreferencesKeys.UPGRADE_CHECK_INTERVAL_MINUTES] ?: 0
            Interval(days, hours, minutes)
        }
    }

    suspend fun saveLastCheckTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CHECK_TIME] = time
        }
    }

    fun getLastCheckTime(): Flow<Long?> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_CHECK_TIME]
        }
    }

    suspend fun saveUnhiddenLaunchOnReboot(unhiddenLaunch: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNHIDDEN_LAUNCH_ON_REBOOT] = unhiddenLaunch
        }
    }

    fun getUnhiddenLaunchOnReboot(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UNHIDDEN_LAUNCH_ON_REBOOT] ?: false
        }
    }

    suspend fun saveAutomaticUpgradeReminder(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOMATIC_UPGRADE_REMINDER] = isEnabled
        }
    }

    fun getAutomaticUpgradeReminder(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTOMATIC_UPGRADE_REMINDER] ?: false
        }
    }

    suspend fun saveIsManualLaunch(isManualLaunch: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_MANUAL_LAUNCH] = isManualLaunch
        }
    }

    fun getIsManualLaunch(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IS_MANUAL_LAUNCH] ?: false
        }
    }

    suspend fun removeLastCheckTime() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LAST_CHECK_TIME)
        }
    }

    suspend fun saveWifiOnly(isWifiOnly: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] = isWifiOnly
        }
    }

    fun getWifiOnly(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] ?: false
        }
    }

    suspend fun saveIsFirstLaunch(isFirstLaunch: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH] = isFirstLaunch
        }
    }

    fun getIsFirstLaunch(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH] ?: true
        }
    }
    fun initializeDataStore() {
        runBlocking {
            val isFirstLaunch = getIsFirstLaunch().firstOrNull() ?: true
            if (isFirstLaunch) {
                saveIsManualLaunch(true)
                saveUnhiddenLaunchOnReboot(false)
                saveIsFirstLaunch(false)
            }
        }
    }
}
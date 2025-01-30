package com.xc.air3upgrader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreManager(private val context: Context) {

    // Create the DataStore instance
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
    }

    // Save the selected model (now accepts a nullable String)
    suspend fun saveSelectedModel(model: String?) {
        context.dataStore.edit { preferences ->
            if (model != null) {
                preferences[SELECTED_MODEL] = model
            } else {
                preferences.remove(SELECTED_MODEL)
            }
        }
    }

    // Get the selected model
    fun getSelectedModel(): Flow<String?> {
        return context.dataStore.data
            .catch { e ->
                // Handle the exception
                e.printStackTrace()
            }
            .map { preferences ->
                preferences[SELECTED_MODEL]
            }
    }

    // Check if the device model is supported
    fun isDeviceModelSupported(deviceModel: String, allowedModels: List<String>): Boolean {
        return allowedModels.contains(deviceModel)
    }
}
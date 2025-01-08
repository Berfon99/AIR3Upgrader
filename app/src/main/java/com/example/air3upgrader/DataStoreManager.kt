package com.example.air3upgrader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreManager(private val context: Context) {

    // Create the DataStore instance
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
    }

    // Save the selected model
    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MODEL] = model
        }
    }

    // Get the selected model
    fun getSelectedModel(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[SELECTED_MODEL]
        }
    }
}
package com.captstoyev.cameratimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val INTERVAL_SECONDS = longPreferencesKey("interval_seconds")
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val IS_BACKGROUND_ENABLED = booleanPreferencesKey("is_background_enabled")
        val IS_SOUND_ENABLED = booleanPreferencesKey("is_sound_enabled")
        val IS_FRONT_CAMERA = booleanPreferencesKey("is_front_camera")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            intervalSeconds = preferences[PreferencesKeys.INTERVAL_SECONDS] ?: 10L,
            folderUri = preferences[PreferencesKeys.FOLDER_URI] ?: "",
            isBackgroundEnabled = preferences[PreferencesKeys.IS_BACKGROUND_ENABLED] ?: false,
            isSoundEnabled = preferences[PreferencesKeys.IS_SOUND_ENABLED] ?: false,
            isFrontCamera = preferences[PreferencesKeys.IS_FRONT_CAMERA] ?: false
        )
    }

    suspend fun updateInterval(seconds: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INTERVAL_SECONDS] = seconds
        }
    }

    suspend fun updateFolderUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOLDER_URI] = uri
        }
    }

    suspend fun updateBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_BACKGROUND_ENABLED] = enabled
        }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_SOUND_ENABLED] = enabled
        }
    }

    suspend fun updateFrontCamera(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FRONT_CAMERA] = enabled
        }
    }
}

data class UserSettings(
    val intervalSeconds: Long,
    val folderUri: String,
    val isBackgroundEnabled: Boolean,
    val isSoundEnabled: Boolean,
    val isFrontCamera: Boolean
)

package com.ralledger.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_preferences")

class AppLockPreferences(context: Context) {
    private val store = context.applicationContext.appLockDataStore

    val enabled: Flow<Boolean> = store.data.map { preferences ->
        decodeAppLockEnabled(preferences[ENABLED_KEY])
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { preferences -> preferences[ENABLED_KEY] = enabled }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("app_lock_enabled")
    }
}

internal fun decodeAppLockEnabled(value: Boolean?): Boolean = value == true

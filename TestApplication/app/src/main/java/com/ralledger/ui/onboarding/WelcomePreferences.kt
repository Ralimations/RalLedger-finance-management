package com.ralledger.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.welcomeDataStore by preferencesDataStore(name = "welcome_preferences")

class WelcomePreferences(
    context: Context,
    private val existingInstall: Boolean
) {
    private val store = context.applicationContext.welcomeDataStore

    val isComplete: Flow<Boolean> = store.data.map { preferences ->
        resolveWelcomeCompletion(preferences[COMPLETED_KEY], existingInstall)
    }

    suspend fun markComplete() {
        store.edit { preferences -> preferences[COMPLETED_KEY] = true }
    }

    suspend fun markCompleteForExistingInstall() {
        if (existingInstall) markComplete()
    }

    private companion object {
        val COMPLETED_KEY = booleanPreferencesKey("welcome_completed")
    }
}

internal fun resolveWelcomeCompletion(storedValue: Boolean?, existingInstall: Boolean): Boolean =
    storedValue ?: existingInstall

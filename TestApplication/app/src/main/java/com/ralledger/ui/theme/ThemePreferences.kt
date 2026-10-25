package com.ralledger.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "appearance_preferences")

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

enum class ThemePalette(val label: String) {
    OCEAN("Ocean"), INDIGO("Indigo"), SAGE("Sage"), CORAL("Coral"), AMBER("Amber")
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.OCEAN
)

class ThemePreferences(context: Context) {
    private val store = context.applicationContext.themeDataStore

    val settings: Flow<ThemeSettings> = store.data.map { preferences ->
        ThemeSettings(
            mode = preferences[MODE_KEY]?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: ThemeMode.SYSTEM,
            palette = preferences[PALETTE_KEY]?.let { value -> ThemePalette.entries.firstOrNull { it.name == value } }
                ?: ThemePalette.OCEAN
        )
    }

    suspend fun setMode(mode: ThemeMode) {
        store.edit { it[MODE_KEY] = mode.name }
    }

    suspend fun setPalette(palette: ThemePalette) {
        store.edit { it[PALETTE_KEY] = palette.name }
    }

    suspend fun restore(settings: ThemeSettings) {
        store.edit { preferences ->
            preferences[MODE_KEY] = settings.mode.name
            preferences[PALETTE_KEY] = settings.palette.name
        }
    }

    private companion object {
        val MODE_KEY = stringPreferencesKey("theme_mode")
        val PALETTE_KEY = stringPreferencesKey("theme_palette")
    }
}

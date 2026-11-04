package com.gamora.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gamoraDataStore by preferencesDataStore(
    name = "gamora_settings"
)

enum class GamoraTheme(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system")
}

object AppearancePreferences {

    private val THEME_KEY = stringPreferencesKey("app_theme")

    fun themeFlow(context: Context): Flow<GamoraTheme> {
        return context.gamoraDataStore.data.map { preferences ->
            when (preferences[THEME_KEY]) {
                "dark" -> GamoraTheme.DARK
                "system" -> GamoraTheme.SYSTEM
                else -> GamoraTheme.LIGHT
            }
        }
    }

    suspend fun setTheme(
        context: Context,
        theme: GamoraTheme
    ) {
        context.gamoraDataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.value
        }
    }
}
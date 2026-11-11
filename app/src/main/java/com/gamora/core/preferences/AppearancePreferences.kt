package com.gamora.core.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gamoraDataStore by preferencesDataStore(
    name = "gamora_settings"
)

enum class GamoraTheme(val value: String, val nightMode: Int) {
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES),
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    companion object {
        fun from(value: String?): GamoraTheme =
            entries.firstOrNull { it.value == value } ?: LIGHT
    }
}

object AppearancePreferences {

    private val THEME_KEY = stringPreferencesKey("app_theme")

    fun themeFlow(context: Context): Flow<GamoraTheme> {
        return context.gamoraDataStore.data.map { preferences ->
            GamoraTheme.from(preferences[THEME_KEY])
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

/** Preferencias de seguridad que se guardan solo en este dispositivo. */
object SeguridadPreferences {

    private val BLOQUEO_KEY = booleanPreferencesKey("bloqueo_biometrico")

    fun bloqueoFlow(context: Context): Flow<Boolean> =
        context.gamoraDataStore.data.map { it[BLOQUEO_KEY] ?: false }

    suspend fun setBloqueo(context: Context, activo: Boolean) {
        context.gamoraDataStore.edit { it[BLOQUEO_KEY] = activo }
    }
}

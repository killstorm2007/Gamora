package com.gamora.data

import com.gamora.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Se lanza cuando faltan las credenciales de Supabase en local.properties.
 */
class SupabaseNotConfiguredException : IllegalStateException(
    "Configura SUPABASE_URL y SUPABASE_ANON_KEY en local.properties"
)

/**
 * Cliente único de Supabase para toda la app.
 *
 * Las credenciales se leen de [BuildConfig] y nunca se escriben en el código:
 * vienen de local.properties (ignorado por git) o de variables de entorno.
 */
object SupabaseConfig {

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
                BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        if (!isConfigured) {
            throw SupabaseNotConfiguredException()
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}

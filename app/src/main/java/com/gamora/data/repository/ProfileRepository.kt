package com.gamora.data.repository

import com.gamora.data.SupabaseConfig
import com.gamora.data.mapDataError
import com.gamora.data.model.ProfileRow
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Perfil del usuario autenticado, guardado en `public.profiles`.
 *
 * El registro se crea automáticamente con un trigger de la base de datos
 * a partir de los metadatos del registro.
 */
object ProfileRepository {

    private const val TABLE = "profiles"

    suspend fun load(): Result<ProfileRow?> = runCatching {
        val userId = AuthRepository.requireUserId()

        SupabaseConfig.client
            .postgrest
            .from(TABLE)
            .select {
                filter { eq("id", userId) }
                maybeSingle()
            }
            .decodeSingleOrNull<ProfileRow>()
    }.mapDataError()

    suspend fun update(profile: ProfileRow): Result<Unit> = runCatching {
        val userId = AuthRepository.requireUserId()

        SupabaseConfig.client
            .postgrest
            .from(TABLE)
            .update(
                body = buildJsonObject {
                    put("full_name", profile.fullName)
                    put("paternal_surname", profile.paternalSurname)
                    put("maternal_surname", profile.maternalSurname)
                    put("phone", profile.phone)
                    put("currency", profile.currency)
                }
            ) {
                filter { eq("id", userId) }
            }

        Unit
    }.mapDataError()
}

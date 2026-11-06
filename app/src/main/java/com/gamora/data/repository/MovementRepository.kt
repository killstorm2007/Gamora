package com.gamora.data.repository

import com.gamora.data.SupabaseConfig
import com.gamora.data.mapDataError
import com.gamora.data.model.MovementRow
import com.gamora.data.model.NewMovement
import com.gamora.feature.transactions.GamoraMovement
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Movimientos del usuario autenticado, guardados en `public.movements`.
 *
 * Mantiene una copia en memoria ([movements]) para que las pantallas se
 * actualicen solas cuando cambian los datos en Supabase.
 */
object MovementRepository {

    private const val TABLE = "movements"

    private val _movements = MutableStateFlow<List<GamoraMovement>>(emptyList())
    val movements: StateFlow<List<GamoraMovement>> = _movements.asStateFlow()

    suspend fun refresh(): Result<Unit> = runCatching {
        _movements.value = fetch(AuthRepository.requireUserId())
    }.mapDataError()

    suspend fun add(
        description: String,
        category: String,
        amountCents: Long,
        isIncome: Boolean,
        occurredAt: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val userId = AuthRepository.requireUserId()

        SupabaseConfig.client
            .postgrest
            .from(TABLE)
            .insert(
                NewMovement(
                    userId = userId,
                    description = description,
                    category = category,
                    amountCents = amountCents,
                    isIncome = isIncome,
                    occurredAt = Instant.ofEpochMilli(occurredAt).toString()
                )
            )

        _movements.value = fetch(userId)
    }.mapDataError()

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val userId = AuthRepository.requireUserId()

        SupabaseConfig.client
            .postgrest
            .from(TABLE)
            .delete {
                filter {
                    eq("user_id", userId)
                    eq("id", id)
                }
            }

        _movements.value = fetch(userId)
    }.mapDataError()

    /**
     * Vacía la copia local al cerrar sesión para no filtrar datos
     * entre usuarios del mismo dispositivo.
     */
    fun clear() {
        _movements.value = emptyList()
    }

    private suspend fun fetch(userId: String): List<GamoraMovement> =
        SupabaseConfig.client
            .postgrest
            .from(TABLE)
            .select {
                filter { eq("user_id", userId) }
                order("occurred_at", Order.DESCENDING)
            }
            .decodeList<MovementRow>()
            .map(MovementRow::toDomain)
}

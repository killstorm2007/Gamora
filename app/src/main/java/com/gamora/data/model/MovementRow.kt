package com.gamora.data.model

import com.gamora.feature.transactions.GamoraMovement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Fila de la tabla `public.movements`.
 */
@Serializable
data class MovementRow(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val description: String = "",
    val category: String = "",
    @SerialName("amount_cents") val amountCents: Long = 0L,
    @SerialName("is_income") val isIncome: Boolean = false,
    @SerialName("occurred_at") val occurredAt: String? = null
) {

    fun toDomain(): GamoraMovement = GamoraMovement(
        id = id,
        description = description,
        category = category,
        amountCents = amountCents,
        isIncome = isIncome,
        timestamp = parseTimestamp(occurredAt)
    )
}

/**
 * Cuerpo para insertar un movimiento nuevo.
 */
@Serializable
data class NewMovement(
    @SerialName("user_id") val userId: String,
    val description: String,
    val category: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("is_income") val isIncome: Boolean,
    @SerialName("occurred_at") val occurredAt: String
)

/**
 * Convierte una fecha ISO 8601 de Postgres (timestamptz) a milisegundos.
 */
internal fun parseTimestamp(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()

    return runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.recoverCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())
}

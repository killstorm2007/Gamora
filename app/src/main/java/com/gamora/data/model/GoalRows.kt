package com.gamora.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Meta de ahorro tal como la consumen las pantallas.
 */
data class SavingsGoal(
    val id: String,
    val name: String,
    val targetCents: Long,
    val savedCents: Long = 0L,
    val deadlineMillis: Long? = null
)

/**
 * Fila de la tabla `public.goals`.
 */
@Serializable
data class GoalRow(
    val id: String,
    val name: String = "",
    @SerialName("target_cents") val targetCents: Long = 0L,
    @SerialName("saved_cents") val savedCents: Long = 0L,
    val deadline: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {

    fun toDomain(): SavingsGoal = SavingsGoal(
        id = id,
        name = name,
        targetCents = targetCents,
        savedCents = savedCents,
        deadlineMillis = deadline?.let(::parseDeadlineDate)
    )
}

/**
 * Cuerpo para insertar una meta nueva.
 */
@Serializable
data class NewGoal(
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("target_cents") val targetCents: Long,
    val deadline: String? = null
)

/**
 * Cuerpo para insertar una aportación. El trigger de la base de datos
 * actualiza `goals.saved_cents` automáticamente.
 */
@Serializable
data class NewGoalContribution(
    @SerialName("goal_id") val goalId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("amount_cents") val amountCents: Long,
    val note: String? = null
)

/**
 * Postgres devuelve las columnas `date` como `yyyy-MM-dd`.
 */
internal fun parseDeadlineDate(value: String): Long? = runCatching {
    LocalDate.parse(value)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

/**
 * Convierte milisegundos a `yyyy-MM-dd` para guardar la fecha límite.
 */
internal fun formatDeadlineDate(millis: Long): String = Instant
    .ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .toString()

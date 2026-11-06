package com.gamora.data.repository

import com.gamora.data.SupabaseConfig
import com.gamora.data.mapDataError
import com.gamora.data.model.GoalRow
import com.gamora.data.model.NewGoal
import com.gamora.data.model.NewGoalContribution
import com.gamora.data.model.SavingsGoal
import com.gamora.data.model.formatDeadlineDate
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Metas de ahorro del usuario autenticado, guardadas en `public.goals`.
 *
 * Las aportaciones se registran en `public.goal_contributions`; un trigger
 * de la base de datos suma el importe a `goals.saved_cents`, de modo que el
 * saldo siempre queda consistente aunque haya varios dispositivos.
 */
object GoalsRepository {

    private const val GOALS_TABLE = "goals"
    private const val CONTRIBUTIONS_TABLE = "goal_contributions"

    private val _goals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    val goals: StateFlow<List<SavingsGoal>> = _goals.asStateFlow()

    suspend fun refresh(): Result<Unit> = runCatching {
        _goals.value = fetch(AuthRepository.requireUserId())
    }.mapDataError()

    suspend fun create(
        name: String,
        targetCents: Long,
        deadlineMillis: Long?
    ): Result<Unit> = runCatching {
        val userId = AuthRepository.requireUserId()

        SupabaseConfig.client
            .postgrest
            .from(GOALS_TABLE)
            .insert(
                NewGoal(
                    userId = userId,
                    name = name,
                    targetCents = targetCents,
                    deadline = deadlineMillis?.let(::formatDeadlineDate)
                )
            )

        _goals.value = fetch(userId)
    }.mapDataError()

    suspend fun contribute(
        goalId: String,
        amountCents: Long
    ): Result<Unit> = runCatching {
        val userId = AuthRepository.requireUserId()

        SupabaseConfig.client
            .postgrest
            .from(CONTRIBUTIONS_TABLE)
            .insert(
                NewGoalContribution(
                    goalId = goalId,
                    userId = userId,
                    amountCents = amountCents
                )
            )

        _goals.value = fetch(userId)
    }.mapDataError()

    suspend fun delete(goalId: String): Result<Unit> = runCatching {
        val userId = AuthRepository.requireUserId()

        SupabaseConfig.client
            .postgrest
            .from(GOALS_TABLE)
            .delete {
                filter {
                    eq("user_id", userId)
                    eq("id", goalId)
                }
            }

        _goals.value = fetch(userId)
    }.mapDataError()

    fun clear() {
        _goals.value = emptyList()
    }

    private suspend fun fetch(userId: String): List<SavingsGoal> =
        SupabaseConfig.client
            .postgrest
            .from(GOALS_TABLE)
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<GoalRow>()
            .map(GoalRow::toDomain)
}

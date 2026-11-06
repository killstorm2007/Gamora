package com.gamora.data.repository

import com.gamora.data.DataError
import com.gamora.data.SupabaseConfig
import com.gamora.data.mapDataError
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Resultado de un registro.
 */
sealed interface SignUpOutcome {

    /** Supabase ya creó la sesión: se puede entrar a la app. */
    data object SignedIn : SignUpOutcome

    /** El proyecto exige confirmar el correo antes de abrir sesión. */
    data object EmailConfirmationRequired : SignUpOutcome
}

/**
 * Autenticación con correo y contraseña usando Supabase Auth.
 *
 * La sesión se guarda y refresca automáticamente por el SDK, así que
 * cualquier consulta posterior a Postgrest viaja con el JWT de la persona.
 */
object AuthRepository {

    private val auth: Auth
        get() = SupabaseConfig.client.auth

    val sessionStatus: StateFlow<SessionStatus>
        get() = auth.sessionStatus

    fun currentUser(): UserInfo? = runCatching {
        auth.currentUserOrNull()
    }.getOrNull()

    fun currentUserId(): String? = currentUser()?.id

    fun currentEmail(): String? = currentUser()?.email

    /**
     * Id del usuario autenticado o un [DataError] si la sesión ya no existe.
     */
    fun requireUserId(): String = currentUserId() ?: throw DataError(
        "Tu sesión expiró. Vuelve a iniciar sesión."
    )

    /**
     * Espera a que el SDK restaure la sesión guardada en el dispositivo.
     */
    suspend fun awaitSession() {
        runCatching { auth.awaitInitialization() }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }.mapDataError()

    suspend fun signUp(
        fullName: String,
        paternalSurname: String,
        maternalSurname: String,
        phone: String,
        email: String,
        password: String
    ): Result<SignUpOutcome> = runCatching {
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            this.data = buildJsonObject {
                put("full_name", fullName)
                put("paternal_surname", paternalSurname)
                put("maternal_surname", maternalSurname)
                put("phone", phone)
            }
        }

        // El SDK devuelve datos del usuario incluso cuando Supabase exige
        // confirmar el correo. Lo que importa es si ya hay sesión activa.
        if (auth.currentSessionOrNull() != null) {
            SignUpOutcome.SignedIn
        } else {
            SignUpOutcome.EmailConfirmationRequired
        }
    }.mapDataError()

    suspend fun signOut(): Result<Unit> = runCatching {
        auth.signOut()
    }.mapDataError()
}

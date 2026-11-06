package com.gamora.data

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException

/**
 * Error de datos con un mensaje listo para mostrar en la interfaz.
 */
class DataError(
    val userMessage: String,
    cause: Throwable? = null
) : Exception(userMessage, cause)

/**
 * Traduce las excepciones del SDK (auth, red, Postgrest) a un [DataError]
 * con un mensaje en español entendible para la persona usuaria.
 */
fun Throwable.toDataError(): DataError {
    val mensaje = when (this) {
        is DataError -> userMessage

        is SupabaseNotConfiguredException ->
            "Falta configurar la conexión con Supabase en local.properties."

        is AuthRestException -> when (error.lowercase()) {
            "invalid_credentials" ->
                "Correo o contraseña incorrectos."

            "email_not_confirmed" ->
                "Confirma tu correo electrónico antes de iniciar sesión."

            "user_already_exists", "email_exists", "phone_exists" ->
                "Ya existe una cuenta registrada con ese correo."

            "weak_password" ->
                "La contraseña es demasiado débil. Usa al menos 6 caracteres."

            "email_address_invalid" ->
                "El correo electrónico no es válido."

            "over_email_send_rate_limit", "over_request_rate_limit" ->
                "Demasiados intentos. Espera un momento y vuelve a intentar."

            "session_not_found", "refresh_token_not_found" ->
                "Tu sesión expiró. Vuelve a iniciar sesión."

            else -> errorDescription.ifBlank { "No se pudo completar la operación." }
        }

        is HttpRequestTimeoutException ->
            "La conexión tardó demasiado. Revisa tu red e intenta de nuevo."

        is HttpRequestException ->
            "No hay conexión con el servidor. Revisa tu red e intenta de nuevo."

        is RestException -> when (statusCode) {
            401 -> "Tu sesión expiró. Vuelve a iniciar sesión."
            403 -> "No tienes permiso para acceder a estos datos."
            404 -> "No se encontró la información solicitada."
            else -> "No se pudieron cargar los datos. Intenta de nuevo."
        }

        else -> message ?: "Ocurrió un error inesperado."
    }

    return DataError(mensaje, this)
}

/**
 * Envuelve cualquier fallo de un [Result] en un [DataError] traducido.
 */
fun <T> Result<T>.mapDataError(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { Result.failure(it.toDataError()) }
)

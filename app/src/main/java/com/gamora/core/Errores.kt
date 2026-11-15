package com.gamora.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import com.gamora.R
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

/** Error cuyo mensaje ya está escrito para la persona (se muestra tal cual). */
class ErrorParaUsuario(mensaje: String) : Exception(mensaje)

/** Qué tipo de problema fue, para elegir el mensaje. */
enum class TipoError { SIN_CONEXION, SESION, SATURADO, DEMASIADOS_INTENTOS, CREDENCIALES, CUENTA_NO_DISPONIBLE, CONTRASENA_DEBIL, VOLVER_A_ENTRAR, OTRO }

fun tipoDeError(error: Throwable?): TipoError = when (error) {
    is FirebaseNetworkException -> TipoError.SIN_CONEXION
    is FirebaseTooManyRequestsException -> TipoError.DEMASIADOS_INTENTOS
    is FirebaseAuthRecentLoginRequiredException -> TipoError.VOLVER_A_ENTRAR
    is FirebaseAuthUserCollisionException -> TipoError.OTRO
    is FirebaseAuthWeakPasswordException -> TipoError.CONTRASENA_DEBIL
    is FirebaseAuthInvalidUserException -> TipoError.CUENTA_NO_DISPONIBLE
    is FirebaseAuthInvalidCredentialsException -> TipoError.CREDENCIALES
    is FirebaseFirestoreException -> when (error.code) {
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> TipoError.SIN_CONEXION
        Code.PERMISSION_DENIED, Code.UNAUTHENTICATED -> TipoError.SESION
        Code.RESOURCE_EXHAUSTED -> TipoError.SATURADO
        else -> TipoError.OTRO
    }
    else -> TipoError.OTRO
}

/**
 * Mensaje entendible para la persona, en el idioma de la app. Los mensajes de Firebase vienen en inglés
 * técnico ("PERMISSION_DENIED: …"), así que nunca se muestran; el detalle queda en el log.
 */
fun mensajeDeError(error: Throwable?, @StringRes porDefecto: Int): String {
    if (error is ErrorParaUsuario) return error.message.orEmpty()
    if (error != null) Log.w("Gamora", "Error: ${error.javaClass.simpleName}", error)

    return when (tipoDeError(error)) {
        TipoError.SIN_CONEXION -> texto(R.string.error_sin_conexion)
        TipoError.SESION -> texto(R.string.error_sesion)
        TipoError.SATURADO -> texto(R.string.error_saturado)
        TipoError.DEMASIADOS_INTENTOS -> texto(R.string.error_demasiados_intentos)
        TipoError.CREDENCIALES -> texto(R.string.error_credenciales)
        TipoError.CUENTA_NO_DISPONIBLE -> texto(R.string.error_cuenta_no_disponible)
        TipoError.CONTRASENA_DEBIL -> texto(R.string.error_contrasena_debil)
        TipoError.VOLVER_A_ENTRAR -> texto(R.string.error_vuelve_a_entrar)
        TipoError.OTRO -> if (error is FirebaseAuthUserCollisionException) texto(R.string.auth_repository_este_correo_ya_esta)
        else texto(porDefecto)
    }
}

// Tiempo máximo que se espera la confirmación del servidor antes de dar la escritura por hecha.
private const val ESPERA_SERVIDOR_MS = 8_000L

/**
 * Para escrituras en Firestore con una pantalla esperando: Firestore guarda el cambio en el teléfono al
 * instante (también sin internet) y lo sube al reconectar, pero la confirmación del servidor puede no
 * llegar nunca sin red. Sin conexión se da por hecho enseguida; con conexión se espera a lo más 8 s.
 * [onSuccess] o [onError] se llaman una sola vez.
 */
fun Task<*>.alGuardar(@StringRes porDefecto: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
    var terminado = false
    val listo = {
        if (!terminado) {
            terminado = true
            onSuccess()
        }
    }

    addOnSuccessListener { listo() }
    addOnFailureListener { error ->
        if (!terminado) {
            terminado = true
            onError(mensajeDeError(error, porDefecto))
        } else {
            Log.w("Gamora", "Escritura rechazada después de darla por hecha", error)
        }
    }
    Handler(Looper.getMainLooper()).postDelayed(listo, if (Conexion.enLinea.value) ESPERA_SERVIDOR_MS else 0L)
}

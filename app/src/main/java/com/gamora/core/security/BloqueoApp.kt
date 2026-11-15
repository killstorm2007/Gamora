package com.gamora.core.security

import com.gamora.R
import com.gamora.core.texto
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// Huella o rostro, con el PIN/patrón del teléfono como alternativa.
private const val AUTENTICADORES = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** true si el teléfono tiene huella, rostro o bloqueo de pantalla configurado. */
fun puedeBloquear(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

enum class ResultadoDesbloqueo {
    EXITO,
    /** La persona tocó "Cancelar" o atrás: se cierra la app. */
    CANCELADO,
    /** El sistema lo interrumpió (pantalla apagada, otra app encima, demasiados intentos…): se vuelve a pedir. */
    INTERRUMPIDO
}

/** Pide huella, rostro o PIN. */
fun pedirDesbloqueo(activity: FragmentActivity, alTerminar: (ResultadoDesbloqueo) -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                alTerminar(ResultadoDesbloqueo.EXITO)

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) =
                alTerminar(
                    when (codigo) {
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> ResultadoDesbloqueo.CANCELADO
                        else -> ResultadoDesbloqueo.INTERRUMPIDO
                    }
                )
        }
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(texto(R.string.bloqueo_app_desbloquea_gamora))
            .setSubtitle(texto(R.string.bloqueo_app_usa_tu_huella_rostro))
            .setAllowedAuthenticators(AUTENTICADORES)
            .build()
    )
}

/**
 * Salida breve a otra app que Gamora abrió (elegir un archivo, compartir, escribir un correo, ajustes):
 * al volver no se pide la huella otra vez. Vale para una sola salida y solo si se vuelve en menos de
 * [SalidaBreve.MAXIMO_MS]; si la otra pantalla no tapa a Gamora por completo, se descarta al volver.
 */
object SalidaBreve {
    const val MAXIMO_MS = 5 * 60 * 1000L

    private var desde = 0L

    /** Llamar justo antes de abrir la otra app. */
    fun permitir() {
        desde = android.os.SystemClock.elapsedRealtime()
    }

    /** true si hay una salida permitida reciente; en cualquier caso la descarta. */
    fun consumir(): Boolean {
        val vigente = desde != 0L && android.os.SystemClock.elapsedRealtime() - desde < MAXIMO_MS
        desde = 0L
        return vigente
    }
}

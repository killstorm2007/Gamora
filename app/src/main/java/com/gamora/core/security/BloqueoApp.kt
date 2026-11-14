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

/**
 * Pide huella, rostro o PIN. [alTerminar] recibe true si la persona se autenticó
 * y false si canceló o falló.
 */
fun pedirDesbloqueo(activity: FragmentActivity, alTerminar: (Boolean) -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                alTerminar(true)

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) =
                alTerminar(false)
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

package com.gamora.core.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

/** Lo que Google entrega al elegir una cuenta. */
data class CuentaGoogle(
    val credencial: AuthCredential,
    val nombre: String,
    val apellidos: String
)

class GoogleNoDisponible(mensaje: String) : Exception(mensaje)

/**
 * Muestra el selector de cuentas de Google. Devuelve null si la persona lo cierra.
 * Requiere activar Google en Firebase Authentication y registrar la huella SHA-1 de la app;
 * con eso google-services.json trae el "default_web_client_id".
 */
suspend fun elegirCuentaGoogle(activity: Activity): CuentaGoogle? {
    val idRecurso = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    if (idRecurso == 0) {
        throw GoogleNoDisponible("El inicio con Google aún no está configurado en Firebase.")
    }

    val solicitud = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(activity.getString(idRecurso)).build())
        .build()

    val resultado = try {
        CredentialManager.create(activity).getCredential(activity, solicitud)
    } catch (_: GetCredentialCancellationException) {
        return null
    } catch (error: GetCredentialException) {
        throw GoogleNoDisponible("No se pudo conectar con Google. ${error.message.orEmpty()}".trim())
    }

    val credencial = resultado.credential
    if (credencial !is CustomCredential ||
        credencial.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        throw GoogleNoDisponible("Google no devolvió una cuenta válida.")
    }

    val google = GoogleIdTokenCredential.createFrom(credencial.data)

    return CuentaGoogle(
        credencial = GoogleAuthProvider.getCredential(google.idToken, null),
        nombre = google.givenName ?: google.displayName ?: "",
        apellidos = google.familyName.orEmpty()
    )
}

package com.gamora

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Versión de depuración: genera un token de depuración que hay que registrar en
 * Firebase Console › App Check › Apps › Administrar tokens de depuración.
 */
fun instalarAppCheck() {
    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}

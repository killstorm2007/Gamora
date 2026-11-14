package com.gamora.feature.legal

import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gamora.MainActivity
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.gamora.core.auth.ConsentData
import com.gamora.core.legal.requiereAceptar
import com.gamora.core.notifications.cancelarAvisos
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Si la persona aceptó una versión anterior del aviso y los términos, le pide aceptar la vigente.
 * No se puede omitir: o acepta (se guarda un consentimiento nuevo, con los mismos permisos de
 * analítica e IA que ya tenía) o cierra sesión.
 */
fun AppCompatActivity.pedirAceptarPoliticasSiHaceFalta(datos: ConsentData) {
    if (!requiereAceptar(datos.versionPoliticas) || isFinishing || isDestroyed) return

    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.politicas_actualizadas_titulo)
        .setMessage(R.string.politicas_actualizadas_mensaje)
        .setCancelable(false)
        .setPositiveButton(R.string.politicas_aceptar) { _, _ ->
            AuthRepository.actualizarConsentimiento(
                datos.copy(terminosAceptados = true, privacidadRevisada = true),
                onSuccess = {},
                // Se vuelve a pedir en el siguiente inicio.
                onError = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            )
        }
        .setNegativeButton(R.string.politicas_cerrar_sesion) { _, _ ->
            cancelarAvisos(this)
            AuthRepository.cerrarSesion()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        .setNeutralButton(R.string.politicas_leer) { _, _ -> mostrarDocumentos(datos) }
        .show()
}

/** Deja leer cualquiera de los dos documentos y vuelve a la pregunta al cerrarlo. */
private fun AppCompatActivity.mostrarDocumentos(datos: ConsentData) {
    val titulos = arrayOf(getString(R.string.register_terminos_y_condiciones), getString(R.string.mainactivity_politica_de_privacidad))
    val textos = intArrayOf(R.string.terminos_condiciones, R.string.politica_privacidad)

    MaterialAlertDialogBuilder(this)
        .setItems(titulos) { _, opcion ->
            val vista = layoutInflater.inflate(R.layout.dialog_documento_legal, null)
            vista.findViewById<TextView>(R.id.tvDocumento).setText(textos[opcion])
            MaterialAlertDialogBuilder(this)
                .setTitle(titulos[opcion])
                .setView(vista)
                .setCancelable(false)
                .setPositiveButton(R.string.main_cerrar) { _, _ -> pedirAceptarPoliticasSiHaceFalta(datos) }
                .show()
        }
        .setCancelable(false)
        .show()
}

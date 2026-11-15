package com.gamora.feature.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.gamora.core.notifications.cancelarAvisos
import com.gamora.core.security.SalidaBreve
import com.gamora.core.texto
import com.gamora.core.ui.IconoDialogo
import com.gamora.core.ui.dialogo
import com.google.android.material.button.MaterialButton

// Perfil › Idioma, documentos legales, ayuda y cerrar sesión.

internal fun ProfileFragment.configurarCerrarSesion(view: View) {

    val btnCerrarSesion = view.findViewById<MaterialButton>(
        R.id.btnCerrarSesion
    )

    btnCerrarSesion.setOnClickListener {

        dialogo(requireContext(), IconoDialogo.SALIR)
            .setTitle(texto(R.string.profile_cerrar_sesion))
            .setMessage(
                texto(R.string.profile_estas_seguro_de_que)
            )
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.profile_cerrar_sesion)) { _, _ ->

                cancelarAvisos(requireContext())
                AuthRepository.cerrarSesion()
                irAInicio()
            }
            .show()
    }
}

/** Idioma de la app (idiomas por app de AndroidX; en Android 13+ también desde Ajustes del teléfono). */
internal fun ProfileFragment.configurarIdioma(view: View) {
    view.findViewById<View>(R.id.btnIdioma).setOnClickListener {
        val etiquetas = arrayOf("", "es", "en")
        val actual = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
        dialogo(requireContext(), IconoDialogo.IDIOMA)
            .setTitle(R.string.profile_idioma_titulo)
            .setSingleChoiceItems(
                arrayOf(getString(R.string.profile_idioma_sistema), "Español", "English"),
                etiquetas.indexOf(actual).coerceAtLeast(0)
            ) { dialogo, opcion ->
                dialogo.dismiss()
                // La app se vuelve a dibujar en el idioma elegido; la sesión se conserva.
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(etiquetas[opcion]))
            }
            .setNegativeButton(R.string.import_review_cancelar, null)
            .show()
    }
}

internal fun ProfileFragment.configurarLegalesYAyuda(view: View) {

    fun mostrarDocumento(titulo: String, recursoTexto: Int) {
        val vista = layoutInflater.inflate(R.layout.dialog_documento_legal, null)
        vista.findViewById<TextView>(R.id.tvDocumento).text = getString(recursoTexto)

        dialogo(requireContext(), IconoDialogo.DOCUMENTO)
            .setTitle(titulo)
            .setView(vista)
            .setPositiveButton(texto(R.string.main_cerrar), null)
            .show()
    }

    view.findViewById<View>(R.id.btnDocumentosLegales).setOnClickListener {
        dialogo(requireContext(), IconoDialogo.DOCUMENTO)
            .setTitle(texto(R.string.profile_documentos_legales_2))
            .setItems(arrayOf(texto(R.string.register_terminos_y_condiciones), texto(R.string.mainactivity_politica_de_privacidad))) { _, opcion ->
                if (opcion == 0) {
                    mostrarDocumento(texto(R.string.register_terminos_y_condiciones), R.string.terminos_condiciones)
                } else {
                    mostrarDocumento(texto(R.string.mainactivity_politica_de_privacidad), R.string.politica_privacidad)
                }
            }
            .setNegativeButton(texto(R.string.main_cerrar), null)
            .show()
    }

    view.findViewById<View>(R.id.btnAyuda).setOnClickListener {
        val correoSoporte = getString(R.string.correo_soporte)

        dialogo(requireContext(), IconoDialogo.AYUDA)
            .setTitle(texto(R.string.profile_ayuda_y_soporte_2))
            .setMessage(
                texto(R.string.profile_registra_ingresos_y_gastos, correoSoporte)
            )
            .setNegativeButton(texto(R.string.main_cerrar), null)
            .setPositiveButton(texto(R.string.profile_escribir_a_soporte)) { _, _ ->
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$correoSoporte"))
                    .putExtra(Intent.EXTRA_SUBJECT, texto(R.string.profile_ayuda_con_gamora))

                try {
                    SalidaBreve.permitir()
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    mostrarToast(texto(R.string.profile_no_hay_una_app))
                }
            }
            .show()
    }
}

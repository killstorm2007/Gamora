package com.gamora.feature.profile

import android.Manifest
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.gamora.core.auth.ConsentData
import com.gamora.core.notifications.puedeNotificar
import com.gamora.core.preferences.SeguridadPreferences
import com.gamora.core.security.puedeBloquear
import com.gamora.core.texto
import com.gamora.core.ui.IconoDialogo
import com.gamora.core.ui.dialogo
import com.gamora.core.ui.dp
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Perfil › Bloqueo con huella, privacidad, IA y notificaciones (diálogos con interruptores).

internal fun ProfileFragment.configurarBloqueo(view: View) {
    view.findViewById<View>(R.id.btnBloqueo).setOnClickListener {
        viewLifecycleOwner.lifecycleScope.launch {
            val activo = SeguridadPreferences.bloqueoFlow(requireContext()).first()

            mostrarDialogoInterruptores(
                icono = IconoDialogo.CANDADO,
                titulo = texto(R.string.profile_bloqueo_de_la_app),
                texto(R.string.profile_gamora_pedira_tu_huella),
                listOf(texto(R.string.profile_activar_bloqueo) to activo)
            ) { valores, cerrar ->
                val nuevo = valores[0]

                if (nuevo && !puedeBloquear(requireContext())) {
                    mostrarToast(texto(R.string.profile_primero_configura_una_huella))
                    return@mostrarDialogoInterruptores
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    SeguridadPreferences.setBloqueo(requireContext(), nuevo)
                    cerrar()
                    mostrarToast(if (nuevo) texto(R.string.profile_bloqueo_activado) else texto(R.string.profile_bloqueo_desactivado))
                }
            }
        }
    }
}

/**
 * Diálogo con interruptores. [onGuardar] recibe los valores elegidos
 * y llama a cerrar() cuando se hayan guardado.
 */
internal fun ProfileFragment.mostrarDialogoInterruptores(
    icono: IconoDialogo?,
    titulo: String,
    mensaje: String,
    opciones: List<Pair<String, Boolean>>,
    botonExtra: Pair<String, () -> Unit>? = null,
    onGuardar: (valores: List<Boolean>, cerrar: () -> Unit) -> Unit
) {
    val contexto = requireContext()

    // Mismo margen lateral (24 dp) que el título y el mensaje del diálogo.
    val dp = resources.displayMetrics.density
    val contenedor = LinearLayout(contexto).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
    }

    val interruptores = opciones.map { (etiqueta, activo) ->
        MaterialSwitch(contexto).apply {
            text = etiqueta
            isChecked = activo
            textSize = 15f
            setTextColor(ContextCompat.getColor(contexto, R.color.gamora_text_primary))
            minHeight = (48 * dp).toInt()
            setPadding(0, 0, 0, 0)
            contenedor.addView(
                this,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    val dialogo = dialogo(contexto, icono)
        .setTitle(titulo)
        .setMessage(mensaje)
        .setView(contenedor)
        .setNegativeButton(texto(R.string.import_review_cancelar), null)
        .setPositiveButton(texto(R.string.import_review_guardar), null)
        .apply {
            botonExtra?.let { (etiqueta, accion) ->
                setNeutralButton(etiqueta) { _, _ -> accion() }
            }
        }
        .create()

    dialogo.setOnShowListener {
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            onGuardar(interruptores.map { it.isChecked }) { dialogo.dismiss() }
        }
    }

    dialogo.show()
}

internal fun ProfileFragment.configurarPrivacidadEIA(view: View) {

    fun editarConsentimiento(
        titulo: String,
        mensaje: String,
        etiqueta: String,
        valorActual: (ConsentData) -> Boolean,
        aplicar: (ConsentData, Boolean) -> ConsentData,
        botonExtra: Pair<String, () -> Unit>? = null
    ) {
        AuthRepository.obtenerConsentimiento(
            onSuccess = { actual ->
                if (!vistaActiva()) return@obtenerConsentimiento

                mostrarDialogoInterruptores(
                    icono = if (titulo == texto(R.string.profile_privacidad_y_seguridad_2)) IconoDialogo.CANDADO else IconoDialogo.IA,
                    titulo = titulo,
                    mensaje,
                    listOf(etiqueta to valorActual(actual)),
                    botonExtra
                ) { valores, cerrar ->

                    if (valores[0] == valorActual(actual)) {
                        cerrar()
                        return@mostrarDialogoInterruptores
                    }

                    AuthRepository.actualizarConsentimiento(
                        aplicar(actual, valores[0]),
                        onSuccess = {
                            cerrar()
                            mostrarToast(texto(R.string.profile_preferencia_guardada))
                        },
                        onError = ::mostrarToast
                    )
                }
            },
            onError = ::mostrarToast
        )
    }

    view.findViewById<View>(R.id.btnPrivacidad).setOnClickListener {
        editarConsentimiento(
            titulo = texto(R.string.profile_privacidad_y_seguridad_2),
            mensaje = texto(R.string.profile_decide_como_usa_gamora),
            etiqueta = texto(R.string.profile_permitir_analitica_de_uso),
            valorActual = { it.analiticaAutorizada },
            aplicar = { c, valor -> c.copy(analiticaAutorizada = valor) },
            botonExtra = texto(R.string.profile_cambiar_contrasena) to {
                val correo = AuthRepository.obtenerCorreo()

                if (correo == null) {
                    mostrarToast(texto(R.string.profile_no_hay_un_correo))
                } else {
                    AuthRepository.recuperarPassword(
                        correo,
                        onSuccess = {
                            mostrarToast(texto(R.string.profile_te_enviamos_un_correo, correo))
                        },
                        onError = ::mostrarToast
                    )
                }
            }
        )
    }

    view.findViewById<View>(R.id.btnPreferenciasIA).setOnClickListener {
        editarConsentimiento(
            titulo = texto(R.string.profile_inteligencia_artificial_2),
            mensaje = texto(R.string.profile_el_asistente_puede_usar),
            etiqueta = texto(R.string.profile_permitir_que_la_ia),
            valorActual = { it.iaAutorizada },
            aplicar = { c, valor -> c.copy(iaAutorizada = valor) }
        )
    }
}

internal fun ProfileFragment.configurarNotificaciones(view: View) {

    // clave en Firestore, etiqueta, valor por defecto
    val opciones = listOf(
        Triple("alertasPresupuesto", texto(R.string.profile_alertas_de_presupuesto), true),
        Triple("recordatoriosMetas", texto(R.string.profile_recordatorios_de_metas), true),
        Triple("resumenSemanal", texto(R.string.profile_resumen_semanal), false)
    )

    view.findViewById<View>(R.id.btnNotificaciones).setOnClickListener {

        AuthRepository.obtenerPreferencia(
            "notificaciones",
            onSuccess = { datos ->
                if (!vistaActiva()) return@obtenerPreferencia

                mostrarDialogoInterruptores(
                    icono = IconoDialogo.NOTIFICACIONES,
                    titulo = texto(R.string.profile_notificaciones_2),
                    texto(R.string.profile_elige_que_avisos_quieres),
                    opciones.map { (clave, etiqueta, porDefecto) ->
                        etiqueta to (datos?.get(clave) as? Boolean ?: porDefecto)
                    }
                ) { valores, cerrar ->

                    AuthRepository.guardarPreferencia(
                        "notificaciones",
                        opciones.map { it.first }.zip(valores).toMap(),
                        onSuccess = {
                            cerrar()
                            mostrarToast(texto(R.string.profile_notificaciones_guardadas))
                            if (valores.any { it } && !puedeNotificar(requireContext())) {
                                permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onError = ::mostrarToast
                    )
                }
            },
            onError = ::mostrarToast
        )
    }
}

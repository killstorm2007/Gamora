package com.gamora.feature.profile

import com.gamora.feature.transactions.GamoraTransactionStore
import com.gamora.feature.goals.GamoraGoalStore
import com.gamora.feature.budget.PresupuestoStore
import com.gamora.core.ui.dp
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.gamora.core.auth.GoogleNoDisponible
import com.gamora.core.auth.elegirCuentaGoogle
import com.gamora.core.nombre
import com.gamora.core.notifications.cancelarAvisos
import com.gamora.core.texto
import com.gamora.core.ui.IconoDialogo
import com.gamora.core.ui.contenidoDesplazable
import com.gamora.core.ui.dialogo
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.AuthCredential
import kotlinx.coroutines.launch

// Perfil › Información personal y eliminar la cuenta.

internal fun ProfileFragment.configurarInformacionPersonal(view: View) {

    val boton = view.findViewById<TextView>(
        R.id.btnInformacionPersonal
    )

    boton.setOnClickListener {

        AuthRepository.obtenerPerfil(
            onSuccess = { perfil ->
                if (!vistaActiva()) return@obtenerPerfil

                mostrarDialogoEditarPerfil(
                    perfil.nombre,
                    perfil.apellidoPaterno,
                    perfil.apellidoMaterno,
                    perfil.correo,
                    perfil.telefono
                )
            },
            onError = ::mostrarToast
        )
    }
}

internal fun ProfileFragment.mostrarDialogoEditarPerfil(
    nombreActual: String,
    paternoActual: String,
    maternoActual: String,
    correoActual: String,
    telefonoActual: String
) {

    val contexto = requireContext()

    val contenedor = LinearLayout(contexto).apply { orientation = LinearLayout.VERTICAL }

    fun crearCampo(etiqueta: String, valor: String, editable: Boolean = true) =
        campoEnDialogo(contenedor, etiqueta, valor, InputType.TYPE_CLASS_TEXT, editable)

    val campoNombre = crearCampo(texto(R.string.register_screen_nombre), nombreActual)
    val campoPaterno = crearCampo(
        texto(R.string.register_screen_apellido_paterno),
        paternoActual
    )
    val campoMaterno = crearCampo(
        texto(R.string.register_screen_apellido_materno),
        maternoActual
    )
    val campoCorreo = crearCampo(
        texto(R.string.login_screen_correo_electronico),
        correoActual,
        false
    )
    val campoTelefono = crearCampo(
        texto(R.string.profile_telefono),
        telefonoActual
    )

    dialogo(contexto, IconoDialogo.PERFIL)
        .setTitle(texto(R.string.profile_informacion_personal_2))
        .setView(contenidoDesplazable(contexto, texto(R.string.profile_manten_tus_datos_actualizados), contenedor))
        .setNegativeButton(texto(R.string.import_review_cancelar), null)
        .setPositiveButton(texto(R.string.import_review_guardar), null)
        .create()
        .also { dialogo ->

            dialogo.setOnShowListener {

                dialogo.getButton(
                    AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener {

                    val nombre = campoNombre.text
                        ?.toString()?.trim().orEmpty()

                    val paterno = campoPaterno.text
                        ?.toString()?.trim().orEmpty()

                    val materno = campoMaterno.text
                        ?.toString()?.trim().orEmpty()

                    val telefono = campoTelefono.text
                        ?.toString()?.trim().orEmpty()

                    AuthRepository.actualizarPerfil(
                        nombre = nombre,
                        apellidoPaterno = paterno,
                        apellidoMaterno = materno,
                        telefono = telefono,
                        onSuccess = {

                            if (!isAdded) return@actualizarPerfil

                            dialogo.dismiss()

                            Toast.makeText(
                                requireContext(),
                                texto(R.string.profile_informacion_actualizada_correctamente),
                                Toast.LENGTH_SHORT
                            ).show()

                            view?.let { cargarPerfil(it) }
                        },
                        onError = { mensaje ->

                            if (!isAdded) return@actualizarPerfil

                            Toast.makeText(
                                requireContext(),
                                mensaje,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }

            dialogo.show()
        }
}

internal fun ProfileFragment.configurarEliminarDatos(view: View) {

    view.findViewById<View>(R.id.btnEliminarDatos).setOnClickListener {

        val contexto = requireContext()

        val conGoogle = AuthRepository.esCuentaGoogle()

        val campoPassword = TextInputEditText(contexto).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val contenedor = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
            addView(
                TextInputLayout(contexto, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                    hint = texto(R.string.login_screen_contrasena)
                    endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                    addView(campoPassword)
                }
            )
            if (conGoogle) visibility = View.GONE
        }

        val dialogo = dialogo(contexto, IconoDialogo.ELIMINAR)
            .setTitle(texto(R.string.profile_eliminar_mi_cuenta_y))
            .setMessage(
                texto(R.string.profile_se_borraran_de_forma) +
                        if (conGoogle) texto(R.string.profile_confirma_con_tu_cuenta) else texto(R.string.profile_escribe_tu_contrasena_para)
            )
            .setView(contenedor)
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.goals_eliminar), null)
            .create()

        dialogo.setOnShowListener {
            // El botón ya es rojo (tema de acción peligrosa); el texto queda en blanco.
            val botonEliminar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)

            fun eliminarCon(credencial: AuthCredential) {
                AuthRepository.eliminarCuenta(
                    contexto,
                    credencial,
                    onSuccess = {
                        dialogo.dismiss()
                        cancelarAvisos(contexto)
                        mostrarToast(texto(R.string.profile_tu_cuenta_y_tus))
                        // Primero se dejan de escuchar los datos; luego se borra la copia del teléfono.
                        GamoraTransactionStore.detener()
                        GamoraGoalStore.detener()
                        PresupuestoStore.detener()
                        AuthRepository.borrarCacheLocal(contexto) { if (isAdded) irAInicio() }
                    },
                    onError = { mensaje ->
                        botonEliminar.isEnabled = true
                        mostrarToast(mensaje)
                    }
                )
            }

            botonEliminar.setOnClickListener {
                if (conGoogle) {
                    botonEliminar.isEnabled = false

                    viewLifecycleOwner.lifecycleScope.launch {
                        val cuenta = try {
                            elegirCuentaGoogle(requireActivity())
                        } catch (error: GoogleNoDisponible) {
                            mostrarToast(error.message.orEmpty())
                            null
                        }

                        if (cuenta == null) botonEliminar.isEnabled = true
                        else eliminarCon(cuenta.credencial)
                    }
                    return@setOnClickListener
                }

                val password = campoPassword.text?.toString().orEmpty()

                if (password.isEmpty()) {
                    campoPassword.error = texto(R.string.profile_escribe_tu_contrasena)
                    return@setOnClickListener
                }

                val credencial = AuthRepository.credencialConPassword(password) ?: run {
                    mostrarToast(texto(R.string.profile_no_hay_un_correo))
                    return@setOnClickListener
                }

                botonEliminar.isEnabled = false
                eliminarCon(credencial)
            }
        }

        dialogo.show()
    }
}

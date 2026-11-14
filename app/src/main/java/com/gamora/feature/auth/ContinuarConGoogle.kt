package com.gamora.feature.auth

import com.gamora.R
import com.gamora.core.texto
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gamora.core.auth.AuthRepository
import com.gamora.core.auth.ConsentData
import com.gamora.core.auth.CuentaGoogle
import com.gamora.core.auth.GoogleNoDisponible
import com.gamora.core.auth.elegirCuentaGoogle
import com.gamora.core.ui.dp
import com.gamora.feature.home.HomeActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Botón "Continuar con Google" de las pantallas de inicio de sesión y registro. */
fun AppCompatActivity.configurarBotonGoogle(boton: MaterialButton) {
    boton.setOnClickListener {
        boton.isEnabled = false

        lifecycleScope.launch {
            val cuenta = try {
                elegirCuentaGoogle(this@configurarBotonGoogle)
            } catch (error: GoogleNoDisponible) {
                mensaje(error.message.orEmpty())
                null
            }

            if (cuenta == null) {
                boton.isEnabled = true
                return@launch
            }

            AuthRepository.iniciarSesionConGoogle(
                cuenta.credencial,
                onSuccess = { perfilExiste ->
                    if (perfilExiste) irAInicio() else pedirConsentimientos(cuenta, boton)
                },
                onError = {
                    mensaje(it)
                    boton.isEnabled = true
                }
            )
        }
    }
}

/** Primera vez con Google: mismos consentimientos que el registro con correo. */
private fun AppCompatActivity.pedirConsentimientos(cuenta: CuentaGoogle, boton: MaterialButton) {
    val contenedor = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
    }

    fun casilla(texto: String) = MaterialCheckBox(this).apply {
        text = texto
        contenedor.addView(this)
    }

    val terminos = casilla(texto(R.string.continuar_con_google_acepto_los_terminos_y))
    val privacidad = casilla(texto(R.string.continuar_con_google_he_leido_la_politica))
    val analitica = casilla(texto(R.string.continuar_con_google_autorizo_la_analitica_de))
    val ia = casilla(texto(R.string.continuar_con_google_autorizo_que_el_asistente))

    val dialogo = MaterialAlertDialogBuilder(this)
        .setTitle(texto(R.string.continuar_con_google_bienvenido_a_gamora))
        .setMessage(texto(R.string.continuar_con_google_antes_de_crear_tu))
        .setView(contenedor)
        .setCancelable(false)
        .setNegativeButton(texto(R.string.import_review_cancelar)) { _, _ ->
            AuthRepository.cancelarRegistroGoogle()
            boton.isEnabled = true
        }
        .setPositiveButton(texto(R.string.mainactivity_crear_cuenta), null)
        .create()

    dialogo.setOnShowListener {
        val crear = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)

        crear.setOnClickListener {
            if (!terminos.isChecked || !privacidad.isChecked) {
                mensaje(texto(R.string.continuar_con_google_debes_aceptar_los_terminos))
                return@setOnClickListener
            }

            crear.isEnabled = false

            AuthRepository.completarRegistroGoogle(
                nombre = cuenta.nombre,
                apellidos = cuenta.apellidos,
                consentimientos = ConsentData(true, true, analitica.isChecked, ia.isChecked),
                onSuccess = {
                    dialogo.dismiss()
                    irAInicio()
                },
                onError = {
                    crear.isEnabled = true
                    mensaje(it)
                }
            )
        }
    }

    dialogo.show()
}

private fun AppCompatActivity.irAInicio() {
    startActivity(
        Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}

private fun AppCompatActivity.mensaje(texto: String) =
    Toast.makeText(this, texto, Toast.LENGTH_LONG).show()

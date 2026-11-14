
package com.gamora.feature.auth.login

import com.gamora.core.texto
import com.gamora.core.ui.enDialogo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gamora.R
import com.gamora.feature.auth.configurarBotonGoogle
import com.gamora.core.auth.AuthRepository
import com.gamora.feature.auth.register.RegisterScreen
import com.gamora.feature.home.HomeActivity

class LoginScreen : AppCompatActivity() {

    private lateinit var btnIniciar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_screen)

        val edtCorreo = findViewById<EditText>(R.id.edtCorreo)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)

        btnIniciar = findViewById(R.id.btnIniciar)
        configurarBotonGoogle(findViewById(R.id.btnGoogle))

        val txtRegistrarse = findViewById<TextView>(R.id.txtRegistrarse)

        val txtRecuperarPassword =
            findViewById<TextView>(R.id.txtRecuperarPassword)

        txtRecuperarPassword.setOnClickListener {
            recuperarPassword()
        }

        txtRegistrarse.setOnClickListener {
            startActivity(Intent(this, RegisterScreen::class.java))
        }

        btnIniciar.setOnClickListener {

            val correo = edtCorreo.text.toString().trim()
            val password = edtPassword.text.toString()

            when {
                correo.isBlank() -> {
                    mostrarMensaje(texto(R.string.login_introduce_tu_correo_electronico))
                }

                !Patterns.EMAIL_ADDRESS.matcher(correo).matches() -> {
                    mostrarMensaje(texto(R.string.login_introduce_un_correo_valido))
                }

                password.isBlank() -> {
                    mostrarMensaje(texto(R.string.login_introduce_tu_contrasena))
                }

                else -> iniciarSesion(correo, password)
            }
        }
    }

    private fun iniciarSesion(correo: String, password: String) {

        btnIniciar.isEnabled = false
        btnIniciar.text = texto(R.string.login_iniciando_sesion)

        AuthRepository.iniciarSesion(
            correo = correo,
            password = password,

            onSuccess = {
                runOnUiThread {
                    startActivity(
                        Intent(this, HomeActivity::class.java)
                    )
                    finish()
                }
            },

            onError = { error ->
                runOnUiThread {
                    btnIniciar.isEnabled = true
                    btnIniciar.text = texto(R.string.login_screen_iniciar_sesion)
                    mostrarMensaje(error)
                }
            }
        )
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun recuperarPassword() {

        val campoCorreo = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = texto(R.string.login_screen_correo_electronico)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(40, 30, 40, 30)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(texto(R.string.login_recuperar_contrasena))
            .setMessage(texto(R.string.login_introduce_el_correo_asociado))
            .setView(campoCorreo.enDialogo())
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.login_enviar)) { _, _ ->

                val correo = campoCorreo.text.toString().trim()

                if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                    mostrarMensaje(texto(R.string.login_introduce_un_correo_valido))
                    return@setPositiveButton
                }

                AuthRepository.recuperarPassword(
                    correo = correo,
                    onSuccess = {
                        mostrarMensaje(
                            texto(R.string.login_si_la_cuenta_existe)
                        )
                    },
                    onError = {
                        mostrarMensaje(
                            texto(R.string.login_no_se_pudo_procesar)
                        )
                    }
                )
            }
            .show()
    }
}

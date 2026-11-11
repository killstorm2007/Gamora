
package com.gamora.feature.auth.login

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
                    mostrarMensaje("Introduce tu correo electrónico.")
                }

                !Patterns.EMAIL_ADDRESS.matcher(correo).matches() -> {
                    mostrarMensaje("Introduce un correo válido.")
                }

                password.isBlank() -> {
                    mostrarMensaje("Introduce tu contraseña.")
                }

                else -> iniciarSesion(correo, password)
            }
        }
    }

    private fun iniciarSesion(correo: String, password: String) {

        btnIniciar.isEnabled = false
        btnIniciar.text = "Iniciando sesión..."

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
                    btnIniciar.text = "Iniciar sesión"
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
            hint = "Correo electrónico"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(40, 30, 40, 30)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Recuperar contraseña")
            .setMessage("Introduce el correo asociado a tu cuenta.")
            .setView(campoCorreo.enDialogo())
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar") { _, _ ->

                val correo = campoCorreo.text.toString().trim()

                if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                    mostrarMensaje("Introduce un correo válido.")
                    return@setPositiveButton
                }

                AuthRepository.recuperarPassword(
                    correo = correo,
                    onSuccess = {
                        mostrarMensaje(
                            "Si la cuenta existe, recibirás instrucciones en tu correo."
                        )
                    },
                    onError = {
                        mostrarMensaje(
                            "No se pudo procesar la solicitud. Inténtalo nuevamente."
                        )
                    }
                )
            }
            .show()
    }
}

package com.gamora.feature.auth.register

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gamora.R
import com.gamora.data.SupabaseConfig
import com.gamora.data.repository.AuthRepository
import com.gamora.data.repository.SignUpOutcome
import com.gamora.feature.auth.login.LoginScreen
import com.gamora.feature.home.HomeActivity
import kotlinx.coroutines.launch

class RegisterScreen : AppCompatActivity() {

    private var registrando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register_screen)

        val txtRegistrarse = findViewById<TextView>(R.id.txtRegistrarse)
        val btnRegistrarse = findViewById<Button>(R.id.btnRegistrarse)

        // Guarda las referencias de las vistas, NO su texto todavía
        val edtNombre = findViewById<EditText>(R.id.edtNombre)
        val edtApPat = findViewById<EditText>(R.id.edtApPat)
        val edtApMat = findViewById<EditText>(R.id.edtApMat)
        val edtCorreo = findViewById<EditText>(R.id.edtCorreo)
        val edtTelefono = findViewById<EditText>(R.id.edtTelefono)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtConfPassword = findViewById<EditText>(R.id.edtConfPassword)
        val cbTerminos = findViewById<CheckBox>(R.id.cbTerminos)

        btnRegistrarse.setOnClickListener {
            // Obtenemos el texto real en el momento exacto del clic
            val nombre = edtNombre.text.toString().trim()
            val apPat = edtApPat.text.toString().trim()
            val apMat = edtApMat.text.toString().trim()
            val correo = edtCorreo.text.toString().trim()
            val telefono = edtTelefono.text.toString().trim()
            val password = edtPassword.text.toString()
            val confPassword = edtConfPassword.text.toString()

            // 1. Validar que ningún campo esté vacío
            if (nombre.isEmpty() || apPat.isEmpty() || apMat.isEmpty() ||
                correo.isEmpty() || telefono.isEmpty() || password.isEmpty() ||
                confPassword.isEmpty()
            ) {

                Toast.makeText(
                    this,
                    "Por favor, rellena todos los campos",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // 2. Validar que las contraseñas coincidan
            else if (password != confPassword) {
                Toast.makeText(
                    this,
                    "Las contraseñas no coinciden",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // 3. Validar que acepte los términos y condiciones
            else if (!cbTerminos.isChecked) {
                Toast.makeText(
                    this,
                    "Debes aceptar los términos y condiciones",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // 4. Si todo está correcto, registrar en Supabase
            else {
                registrar(
                    nombre = nombre,
                    apellidoPaterno = apPat,
                    apellidoMaterno = apMat,
                    correo = correo,
                    telefono = telefono,
                    password = password,
                    boton = btnRegistrarse
                )
            }
        }

        txtRegistrarse.setOnClickListener {
            startActivity(Intent(this, LoginScreen::class.java))
        }

        cbTerminos.setOnClickListener {
            mostrarPoliticas()
        }
    }

    private fun registrar(
        nombre: String,
        apellidoPaterno: String,
        apellidoMaterno: String,
        correo: String,
        telefono: String,
        password: String,
        boton: Button
    ) {
        if (registrando) return

        if (!SupabaseConfig.isConfigured) {
            Toast.makeText(
                this,
                "Falta configurar SUPABASE_URL y SUPABASE_ANON_KEY en local.properties.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        registrando = true
        boton.isEnabled = false
        boton.text = "Registrando..."

        lifecycleScope.launch {
            AuthRepository.signUp(
                fullName = nombre,
                paternalSurname = apellidoPaterno,
                maternalSurname = apellidoMaterno,
                phone = telefono,
                email = correo,
                password = password
            ).onSuccess { resultado ->
                when (resultado) {
                    SignUpOutcome.SignedIn -> {
                        Toast.makeText(
                            this@RegisterScreen,
                            "Registrado correctamente, redirigiendo...",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(
                            Intent(this@RegisterScreen, HomeActivity::class.java)
                        )
                        finish()
                    }

                    SignUpOutcome.EmailConfirmationRequired -> {
                        Toast.makeText(
                            this@RegisterScreen,
                            "Te enviamos un correo para confirmar tu cuenta. " +
                                    "Confírmalo y después inicia sesión.",
                            Toast.LENGTH_LONG
                        ).show()

                        startActivity(
                            Intent(this@RegisterScreen, LoginScreen::class.java)
                        )
                        finish()
                    }
                }
            }.onFailure { error ->
                Toast.makeText(
                    this@RegisterScreen,
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }

            registrando = false
            boton.isEnabled = true
            boton.text = "Registrarse"
        }
    }

    private fun mostrarPoliticas() {
        mostrarDocumento(
            "Politica de Privacidad",
            R.string.terminos_condiciones
        )
    }

    private fun mostrarDocumento(
        titulo: String,
        recursoTexto: Int
    ) {
        val vista = LayoutInflater.from(this)
            .inflate(R.layout.dialog_documento_legal, null)

        val tvDocumento = vista.findViewById<TextView>(R.id.tvDocumento)

        tvDocumento.text = getString(recursoTexto)

        val dialogo = AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(vista)
            .setPositiveButton("Cerrar") { dialog, _ -> dialog.dismiss() }
            .create()

        dialogo.setCancelable(true)
        dialogo.show()
    }
}

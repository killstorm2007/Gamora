package com.gamora.feature.auth.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gamora.R
import com.gamora.data.SupabaseConfig
import com.gamora.data.repository.AuthRepository
import com.gamora.feature.auth.register.RegisterScreen
import com.gamora.feature.home.HomeActivity
import kotlinx.coroutines.launch

class LoginScreen : AppCompatActivity() {

    private var iniciandoSesion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_screen)

        val btnIniciar = findViewById<Button>(R.id.btnIniciar)
        val txtRegistrarse = findViewById<TextView>(R.id.txtRegistrarse)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtCorreo = findViewById<EditText>(R.id.edtCorreo)

        txtRegistrarse.setOnClickListener {
            startActivity(Intent(this, RegisterScreen::class.java))
        }

        btnIniciar.setOnClickListener {
            val correo = edtCorreo.text.toString().trim()
            val password = edtPassword.text.toString()

            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Rellena todos los campos",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                iniciarSesion(correo, password, btnIniciar)
            }
        }

        restaurarSesion()
    }

    /**
     * Si el SDK encuentra una sesión guardada, entra directo a la app.
     */
    private fun restaurarSesion() {
        if (!SupabaseConfig.isConfigured) {
            mostrarErrorDeConfiguracion()
            return
        }

        lifecycleScope.launch {
            AuthRepository.awaitSession()

            if (AuthRepository.currentUserId() != null) {
                irAInicio()
            }
        }
    }

    private fun iniciarSesion(
        correo: String,
        password: String,
        boton: Button
    ) {
        if (iniciandoSesion) return

        iniciandoSesion = true
        boton.isEnabled = false
        boton.text = "Iniciando sesión..."

        lifecycleScope.launch {
            AuthRepository.signIn(correo, password)
                .onSuccess {
                    Toast.makeText(
                        this@LoginScreen,
                        "Sesión iniciada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    irAInicio()
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@LoginScreen,
                        error.message,
                        Toast.LENGTH_LONG
                    ).show()
                }

            iniciandoSesion = false
            boton.isEnabled = true
            boton.text = "Iniciar Sesion"
        }
    }

    private fun irAInicio() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun mostrarErrorDeConfiguracion() {
        Toast.makeText(
            this,
            "Falta configurar SUPABASE_URL y SUPABASE_ANON_KEY en local.properties.",
            Toast.LENGTH_LONG
        ).show()
    }
}

package com.gamora.feature.auth.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.gamora.R
import com.gamora.feature.auth.register.RegisterScreen
import com.gamora.feature.home.HomeActivity

class LoginScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_screen)

        val btnIniciar = findViewById<Button>(R.id.btnIniciar)
        val txtRegistrarse = findViewById<TextView>(R.id.txtRegistrarse)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtCorreo = findViewById<EditText>(R.id.edtCorreo)

        txtRegistrarse.setOnClickListener {
            val cambio = Intent(this, RegisterScreen::class.java)

            startActivity(cambio)
        }

        btnIniciar.setOnClickListener {

            val correo = edtCorreo.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if(correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sesion Iniciada Correctamente", Toast.LENGTH_SHORT).show()
                val cambio = Intent(this, HomeActivity::class.java)

                startActivity(cambio)
                finish()

            }

        }

    }
}
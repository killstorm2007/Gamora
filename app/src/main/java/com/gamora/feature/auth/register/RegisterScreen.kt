
package com.gamora.feature.auth.register

import com.gamora.core.texto
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gamora.R
import com.gamora.feature.auth.configurarBotonGoogle
import com.gamora.core.auth.AuthRepository
import com.gamora.core.auth.ConsentData
import com.gamora.core.auth.RegisterData
import com.gamora.feature.auth.login.LoginScreen
import com.gamora.feature.home.HomeActivity

class RegisterScreen : AppCompatActivity() {

    private lateinit var btnRegistrarse: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_screen)

        val edtNombre = findViewById<EditText>(R.id.edtNombre)
        val edtApPat = findViewById<EditText>(R.id.edtApPat)
        val edtApMat = findViewById<EditText>(R.id.edtApMat)
        val edtCorreo = findViewById<EditText>(R.id.edtCorreo)
        val edtTelefono = findViewById<EditText>(R.id.edtTelefono)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtConfPassword = findViewById<EditText>(R.id.edtConfPassword)

        val cbTerminos = findViewById<CheckBox>(R.id.cbTerminos)
        val cbPrivacidad = findViewById<CheckBox>(R.id.cbPrivacidad)
        val cbAnalitica = findViewById<CheckBox>(R.id.cbAnalitica)
        val cbIA = findViewById<CheckBox>(R.id.cbIA)

        val txtVerTerminos = findViewById<TextView>(R.id.txtVerTerminos)
        val txtVerPrivacidad = findViewById<TextView>(R.id.txtVerPrivacidad)
        val txtRegistrarse = findViewById<TextView>(R.id.txtRegistrarse)

        btnRegistrarse = findViewById(R.id.btnRegistrarse)
        configurarBotonGoogle(findViewById(R.id.btnGoogle))

        txtVerTerminos.setOnClickListener {
            mostrarDocumento(
                texto(R.string.register_terminos_y_condiciones),
                R.string.terminos_condiciones
            )
        }

        txtVerPrivacidad.setOnClickListener {
            mostrarDocumento(
                texto(R.string.mainactivity_politica_de_privacidad),
                R.string.politica_privacidad
            )
        }

        txtRegistrarse.setOnClickListener {
            startActivity(Intent(this, LoginScreen::class.java))
            finish()
        }

        btnRegistrarse.setOnClickListener {

            val nombre = edtNombre.text.toString().trim()
            val apellidoPaterno = edtApPat.text.toString().trim()
            val apellidoMaterno = edtApMat.text.toString().trim()
            val correo = edtCorreo.text.toString().trim()
            val telefono = edtTelefono.text.toString().trim()

            // No recortamos las contraseñas: los espacios pueden formar parte de ellas.
            val password = edtPassword.text.toString()
            val confirmarPassword = edtConfPassword.text.toString()

            when {
                nombre.isBlank() ||
                        apellidoPaterno.isBlank() ||
                        apellidoMaterno.isBlank() ||
                        correo.isBlank() ||
                        password.isBlank() ||
                        confirmarPassword.isBlank() -> {
                    mostrarMensaje(texto(R.string.register_completa_todos_los_campos))
                }

                !Patterns.EMAIL_ADDRESS.matcher(correo).matches() -> {
                    mostrarMensaje(texto(R.string.register_introduce_un_correo_electronico))
                }

                password.length < 8 -> {
                    mostrarMensaje(texto(R.string.register_la_contrasena_debe_tener))
                }

                password != confirmarPassword -> {
                    mostrarMensaje(texto(R.string.register_las_contrasenas_no_coinciden))
                }

                !cbTerminos.isChecked -> {
                    mostrarMensaje(texto(R.string.register_debes_aceptar_los_terminos))
                }

                !cbPrivacidad.isChecked -> {
                    mostrarMensaje(texto(R.string.register_debes_confirmar_que_revisaste))
                }

                else -> {

                    btnRegistrarse.isEnabled = false
                    btnRegistrarse.text = texto(R.string.register_creando_cuenta)

                    val datos = RegisterData(
                        nombre = nombre,
                        apellidoPaterno = apellidoPaterno,
                        apellidoMaterno = apellidoMaterno,
                        correo = correo,
                        telefono = telefono
                    )

                    val consentimientos = ConsentData(
                        terminosAceptados = cbTerminos.isChecked,
                        privacidadRevisada = cbPrivacidad.isChecked,
                        analiticaAutorizada = cbAnalitica.isChecked,
                        iaAutorizada = cbIA.isChecked
                    )

                    AuthRepository.registrarUsuario(
                        datos = datos,
                        password = password,
                        consentimientos = consentimientos,

                        onSuccess = {
                            runOnUiThread {
                                mostrarMensaje(texto(R.string.register_cuenta_creada_correctamente))

                                startActivity(
                                    Intent(this, HomeActivity::class.java)
                                )

                                finish()
                            }
                        },

                        onError = { error ->
                            runOnUiThread {
                                btnRegistrarse.isEnabled = true
                                btnRegistrarse.text = texto(R.string.mainactivity_crear_cuenta)
                                mostrarMensaje(error)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun mostrarDocumento(
        titulo: String,
        recursoTexto: Int
    ) {
        val vista = layoutInflater.inflate(
            R.layout.dialog_documento_legal,
            null
        )

        val tvDocumento = vista.findViewById<TextView>(
            R.id.tvDocumento
        )

        tvDocumento.text = getString(recursoTexto)

        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setView(vista)
            .setPositiveButton(texto(R.string.main_cerrar), null)
            .show()
    }
}

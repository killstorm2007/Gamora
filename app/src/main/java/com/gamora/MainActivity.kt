
package com.gamora

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gamora.core.preferences.AppearancePreferences
import com.gamora.core.preferences.GamoraTheme
import com.gamora.feature.auth.login.LoginScreen
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_mainactivity)

        val botonIniciar = findViewById<Button>(R.id.btnIniciar)
        val txtPoliticas = findViewById<TextView>(R.id.txtPoliticas)

        botonIniciar.setOnClickListener {
            val cambio = Intent(this, LoginScreen::class.java)
            startActivity(cambio)
        }

        txtPoliticas.setOnClickListener {
            mostrarPoliticas()
        }

        observarTemaGuardado()
    }

    private fun observarTemaGuardado() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppearancePreferences
                    .themeFlow(this@MainActivity)
                    .collect { tema ->
                        aplicarTema(tema)
                    }
            }
        }
    }

    private fun aplicarTema(tema: GamoraTheme) {
        val modo = when (tema) {
            GamoraTheme.LIGHT ->
                AppCompatDelegate.MODE_NIGHT_NO

            GamoraTheme.DARK ->
                AppCompatDelegate.MODE_NIGHT_YES

            GamoraTheme.SYSTEM ->
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        if (AppCompatDelegate.getDefaultNightMode() != modo) {
            AppCompatDelegate.setDefaultNightMode(modo)
        }
    }

    private fun mostrarPoliticas() {
        mostrarDocumento(
            "Política de Privacidad",
            R.string.politica_privacidad
        )
    }

    private fun mostrarDocumento(
        titulo: String,
        recursoTexto: Int
    ) {
        val vista = LayoutInflater.from(this)
            .inflate(R.layout.dialog_documento_legal, null)

        val tvDocumento = vista.findViewById<TextView>(
            R.id.tvDocumento
        )

        tvDocumento.text = getString(recursoTexto)

        val dialogo = AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(vista)
            .setPositiveButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialogo.setCancelable(true)
        dialogo.show()
    }
}

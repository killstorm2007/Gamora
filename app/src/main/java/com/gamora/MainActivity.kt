
package com.gamora

import com.gamora.R
import com.gamora.core.texto
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.gamora.core.auth.AuthRepository
import com.gamora.core.preferences.AppearancePreferences
import com.gamora.feature.auth.login.LoginScreen
import com.gamora.feature.auth.register.RegisterScreen
import com.gamora.feature.home.HomeActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var splashPreparada = false

    override fun onCreate(savedInstanceState: Bundle?) {

        // Instalar SplashScreen antes de super.onCreate().
        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition {
            !splashPreparada
        }

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        prepararAplicacion()
    }

    private fun prepararAplicacion() {

        lifecycleScope.launch {

            // 1. Leer el tema guardado en DataStore.
            val tema = AppearancePreferences
                .themeFlow(this@MainActivity)
                .first()

            // 2. Obtener el modo correspondiente.
            val modo = tema.nightMode

            // 3. Aplicar el tema antes de mostrar la interfaz.
            if (AppCompatDelegate.getDefaultNightMode() != modo) {
                AppCompatDelegate.setDefaultNightMode(modo)
            }

            // 4. Comprobar la sesión de Firebase.
            if (AuthRepository.obtenerUid() != null) {

                splashPreparada = true

                startActivity(
                    Intent(this@MainActivity, HomeActivity::class.java)
                )

                finish()
                return@launch
            }

            // 5. Si no hay sesión, mostrar la bienvenida.
            setContentView(R.layout.activity_mainactivity)

            // Fondo verde en cualquier tema: iconos de la barra de estado en blanco.
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )

            // Suma las barras del sistema al padding del XML en vez de reemplazarlo.
            val raiz = findViewById<View>(R.id.main)
            val izq = raiz.paddingLeft
            val arriba = raiz.paddingTop
            val der = raiz.paddingRight
            val abajo = raiz.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
                val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                vista.setPadding(izq + barras.left, arriba + barras.top, der + barras.right, abajo + barras.bottom)
                insets
            }

            val botonIniciar = findViewById<Button>(R.id.btnIniciar)
            val txtPoliticas = findViewById<TextView>(R.id.txtPoliticas)

            botonIniciar.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, LoginScreen::class.java)
                )
                finish()
            }

            findViewById<Button>(R.id.btnCrearCuenta).setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, RegisterScreen::class.java)
                )
                finish()
            }

            txtPoliticas.setOnClickListener {
                mostrarPoliticas()
            }

            // La aplicación ya está preparada.
            splashPreparada = true
        }
    }

    private fun mostrarPoliticas() {
        mostrarDocumento(
            texto(R.string.main_politica_de_privacidad),
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

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setView(vista)
            .setPositiveButton(texto(R.string.main_cerrar)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialogo.show()
    }
}

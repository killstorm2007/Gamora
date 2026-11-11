
package com.gamora.feature.home

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.gamora.core.notifications.programarAvisos
import com.gamora.core.notifications.puedeNotificar
import com.gamora.core.notifications.revisarAvisosAhora
import com.gamora.core.ui.Moneda
import com.gamora.feature.transactions.Recurrentes
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.gamora.core.auth.AuthRepository
import com.gamora.core.preferences.AppearancePreferences
import com.gamora.core.preferences.GamoraTheme
import com.gamora.feature.goals.GamoraGoalStore
import com.gamora.feature.transactions.GamoraTransactionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.gamora.core.preferences.SeguridadPreferences
import com.gamora.core.security.pedirDesbloqueo
import com.gamora.core.security.puedeBloquear
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.gamora.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeActivity : AppCompatActivity() {

    // Bloqueo con huella/PIN: se pide al abrir y al volver de segundo plano.
    private var desbloqueado = false
    private var autenticando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Al rotar o cambiar de tema no se vuelve a pedir.
        desbloqueado = savedInstanceState?.getBoolean(CLAVE_DESBLOQUEADO) ?: false

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_home)

        AuthRepository.obtenerUid()?.let { uid ->
            GamoraTransactionStore.escuchar(uid)
            GamoraGoalStore.escuchar(uid)

            if (savedInstanceState == null) {
                // Registra rentas, salarios, etc. que tocaban mientras la app estaba cerrada.
                lifecycleScope.launch {
                    try {
                        Recurrentes.procesar(uid)
                    } catch (error: Exception) {
                        Log.w("HomeActivity", "No se pudieron registrar los recurrentes", error)
                    }
                }
                cargarMoneda()
                programarAvisos(this)
            }
        }
        // Solo en el arranque: al cambiar de tema la actividad se recrea con estado guardado.
        if (savedInstanceState == null) restaurarTema()

        val root = findViewById<View>(R.id.root_container)

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHost.navController

        val bottomNavigation =
            findViewById<BottomNavigationView>(R.id.bottom_navigation)

        val fabChatbot =
            findViewById<FloatingActionButton>(R.id.fabChatbot)

        bottomNavigation.setupWithNavController(navController)

        fabChatbot.setOnClickListener {
            if (navController.currentDestination?.id == R.id.chatbotFragment) {
                navController.popBackStack()
            } else {
                navController.navigate(R.id.chatbotFragment)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->

            val isChatbot = destination.id == R.id.chatbotFragment

            if (isChatbot) {
                bottomNavigation.visibility = View.GONE
                fabChatbot.visibility = View.GONE
            } else {
                bottomNavigation.visibility = View.VISIBLE
                fabChatbot.visibility = View.VISIBLE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        verificarBloqueo()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !autenticando) desbloqueado = false

        // Al salir, revisa si los gastos recién registrados merecen un aviso.
        if (!isChangingConfigurations && AuthRepository.obtenerUid() != null) revisarAvisosAhora(this)
    }

    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Android 13+: se pide una sola vez; después se puede activar desde Perfil › Notificaciones. */
    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || puedeNotificar(this)) return

        val prefs = getSharedPreferences("gamora_permisos", MODE_PRIVATE)
        if (prefs.getBoolean("notificaciones_pedido", false)) return

        prefs.edit().putBoolean("notificaciones_pedido", true).apply()
        permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** La moneda de la cuenta manda sobre la guardada en el teléfono (p. ej. al cambiar de dispositivo). */
    private fun cargarMoneda() {
        AuthRepository.obtenerPreferenciasFinancieras(
            onSuccess = { Moneda.cambiar(this, it.moneda) },
            onError = {}
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CLAVE_DESBLOQUEADO, desbloqueado)
    }

    private fun verificarBloqueo() {
        if (desbloqueado || autenticando) return

        // Se oculta antes de leer la preferencia para no mostrar saldos ni por un instante.
        val contenido = findViewById<View>(R.id.root_container)
        contenido.visibility = View.INVISIBLE

        lifecycleScope.launch {
            val activo = SeguridadPreferences.bloqueoFlow(this@HomeActivity).first()

            // Si el teléfono ya no tiene huella ni PIN, no dejar a la persona fuera.
            if (!activo || !puedeBloquear(this@HomeActivity)) {
                desbloqueado = true
                contenido.visibility = View.VISIBLE
                pedirPermisoNotificaciones()
                return@launch
            }

            autenticando = true

            pedirDesbloqueo(this@HomeActivity) { exito ->
                autenticando = false

                if (exito) {
                    desbloqueado = true
                    contenido.visibility = View.VISIBLE
                    pedirPermisoNotificaciones()
                } else {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Al rotar, onCreate vuelve a escuchar; al cerrar sesión no quedan datos del usuario anterior.
        GamoraTransactionStore.detener()
        GamoraGoalStore.detener()
    }

    /** Aplica el tema guardado en la cuenta (útil al iniciar sesión en otro dispositivo). */
    private fun restaurarTema() {
        AuthRepository.obtenerPreferencia(
            "apariencia",
            onSuccess = { datos ->
                val valor = datos?.get("tema") as? String ?: return@obtenerPreferencia
                val tema = GamoraTheme.from(valor)

                lifecycleScope.launch {
                    AppearancePreferences.setTheme(this@HomeActivity, tema)
                }

                if (AppCompatDelegate.getDefaultNightMode() != tema.nightMode) {
                    AppCompatDelegate.setDefaultNightMode(tema.nightMode)
                }
            },
            onError = {}
        )
    }

    private companion object {
        const val CLAVE_DESBLOQUEADO = "desbloqueado"
    }
}

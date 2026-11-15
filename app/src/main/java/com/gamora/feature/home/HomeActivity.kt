
package com.gamora.feature.home

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gamora.core.Conexion
import com.gamora.core.EstadoDatos
import com.gamora.core.mensajeDeError
import com.gamora.core.security.ResultadoDesbloqueo
import com.gamora.core.security.SalidaBreve
import com.gamora.core.ui.IconoDialogo
import com.gamora.core.ui.dialogo
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.gamora.core.notifications.programarAvisos
import com.gamora.core.notifications.puedeNotificar
import com.gamora.core.notifications.revisarAvisosAhora
import com.gamora.core.ui.Moneda
import com.gamora.feature.transactions.Recurrentes
import com.gamora.feature.transactions.mostrarDialogoMovimiento
import com.gamora.feature.onboarding.mostrarBienvenidaSiHaceFalta
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.gamora.core.auth.AuthRepository
import com.gamora.feature.legal.pedirAceptarPoliticasSiHaceFalta
import com.gamora.core.preferences.AppearancePreferences
import com.gamora.core.preferences.GamoraTheme
import com.gamora.feature.budget.PresupuestoStore
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

        // Con el bloqueo activo, la pantalla no aparece en "apps recientes" ni en capturas:
        // si no, los saldos se verían sin pasar por la huella.
        lifecycleScope.launch {
            SeguridadPreferences.bloqueoFlow(this@HomeActivity).collect { activo ->
                if (activo) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        AuthRepository.obtenerUid()?.let { uid ->
            GamoraTransactionStore.escuchar(this, uid)
            GamoraGoalStore.escuchar(uid)
            PresupuestoStore.escuchar(uid)

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
                AuthRepository.obtenerConsentimiento(
                    onSuccess = {
                        AuthRepository.aplicarAnalitica(it.analiticaAutorizada)
                        // Quien aceptó una versión anterior del aviso y los términos debe aceptar la vigente.
                        pedirAceptarPoliticasSiHaceFalta(it)
                    },
                    onError = { Log.w("HomeActivity", it) }
                )
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

            // Pantallas completas: sin barra inferior ni botón de Gamora AI.
            val pantallaCompleta = destination.id == R.id.chatbotFragment ||
                    destination.id == R.id.importReviewFragment

            if (pantallaCompleta) {
                bottomNavigation.visibility = View.GONE
                fabChatbot.visibility = View.GONE
            } else {
                bottomNavigation.visibility = View.VISIBLE
                fabChatbot.visibility = View.VISIBLE
            }
        }

        // Errores al cargar los datos y falta de internet: se avisa en lugar de mostrar todo vacío.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(Conexion.enLinea, EstadoDatos.problemas) { enLinea, problemas -> enLinea to problemas }
                    .collect { (enLinea, problemas) -> mostrarAviso(enLinea, problemas) }
            }
        }
    }

    private var aviso: Snackbar? = null
    private var avisoSinInternetCerrado = false

    private fun mostrarAviso(enLinea: Boolean, problemas: Map<String, Throwable>) {
        aviso?.dismiss()
        aviso = null
        if (enLinea) avisoSinInternetCerrado = false

        val raiz = findViewById<View>(R.id.root_container)
        val error = problemas.values.firstOrNull()

        val barra = when {
            error != null ->
                Snackbar.make(raiz, mensajeDeError(error, R.string.datos_no_se_pudieron_cargar), Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.datos_reintentar) { reintentarCarga() }

            !enLinea && !avisoSinInternetCerrado ->
                Snackbar.make(raiz, R.string.conexion_sin_internet, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.conexion_entendido) { avisoSinInternetCerrado = true }

            else -> return
        }

        val navegacion = findViewById<View>(R.id.bottom_navigation)
        if (navegacion.isVisible) barra.anchorView = navegacion
        barra.setTextMaxLines(4)
        barra.show()
        aviso = barra
    }

    private fun reintentarCarga() {
        val uid = AuthRepository.obtenerUid() ?: return
        EstadoDatos.limpiar()
        GamoraTransactionStore.escuchar(this, uid, forzar = true)
        GamoraGoalStore.escuchar(uid, forzar = true)
        PresupuestoStore.escuchar(uid, forzar = true)
    }

    // Momento en que se salió a otra app abierta por Gamora (elegir archivo, compartir…); 0 si no.
    private var salidaBreveDesde = 0L

    override fun onStart() {
        super.onStart()
        if (salidaBreveDesde != 0L) {
            if (SystemClock.elapsedRealtime() - salidaBreveDesde > SalidaBreve.MAXIMO_MS) desbloqueado = false
            salidaBreveDesde = 0L
        }
        verificarBloqueo()
    }

    override fun onResume() {
        super.onResume()
        // Si la otra pantalla no tapó a Gamora (no hubo onStop), el permiso ya no sirve.
        SalidaBreve.consumir()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !autenticando) {
            if (SalidaBreve.consumir()) salidaBreveDesde = SystemClock.elapsedRealtime()
            else desbloqueado = false
        }

        // Al salir, revisa si los gastos recién registrados merecen un aviso.
        if (!isChangingConfigurations && AuthRepository.obtenerUid() != null) revisarAvisosAhora(this)
    }

    private var bienvenidaRevisada = false

    /** Tras desbloquear: bienvenida (solo cuentas nuevas) y luego el permiso de avisos. */
    private fun alDesbloquear() {
        if (bienvenidaRevisada) return
        bienvenidaRevisada = true

        mostrarBienvenidaSiHaceFalta(
            registrarMovimiento = { esIngreso ->
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                navHost.childFragmentManager.primaryNavigationFragment
                    ?.mostrarDialogoMovimiento(esIngreso)
            },
            alTerminar = ::pedirPermisoNotificaciones
        )
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
                alDesbloquear()
                return@launch
            }

            autenticando = true

            pedirDesbloqueo(this@HomeActivity) { resultado ->
                autenticando = false

                when (resultado) {
                    ResultadoDesbloqueo.EXITO -> {
                        reintento?.dismiss()
                        desbloqueado = true
                        contenido.visibility = View.VISIBLE
                        alDesbloquear()
                    }
                    ResultadoDesbloqueo.CANCELADO -> finish()
                    // Pantalla apagada u otra app encima: se pide de nuevo al volver (onStart).
                    // Si Gamora sigue al frente (p. ej. demasiados intentos), se ofrece reintentar.
                    ResultadoDesbloqueo.INTERRUMPIDO ->
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) ofrecerReintento()
                }
            }
        }
    }

    private var reintento: AlertDialog? = null

    private fun ofrecerReintento() {
        if (reintento?.isShowing == true) return
        reintento = dialogo(this, IconoDialogo.CANDADO)
            .setTitle(R.string.bloqueo_app_desbloquea_gamora)
            .setMessage(R.string.bloqueo_no_se_pudo_verificar)
            .setCancelable(false)
            .setNegativeButton(R.string.main_cerrar) { _, _ -> finish() }
            .setPositiveButton(R.string.bloqueo_intentar_de_nuevo) { _, _ -> verificarBloqueo() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Al rotar, onCreate vuelve a escuchar; al cerrar sesión no quedan datos del usuario anterior.
        GamoraTransactionStore.detener()
        GamoraGoalStore.detener()
        PresupuestoStore.detener()
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

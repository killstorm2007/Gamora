package com.gamora.feature.profile

import com.gamora.core.security.SalidaBreve
import com.gamora.feature.exportar.exportarDatos
import com.gamora.core.texto
import com.gamora.core.nombre
import com.gamora.core.ui.enCampo
import com.gamora.core.ui.dinero
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gamora.MainActivity
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.gamora.core.preferences.AppearancePreferences
import com.gamora.core.preferences.GamoraTheme
import com.gamora.feature.transactions.GamoraTransactionStore
import com.gamora.feature.transactions.observarMovimientos
import com.gamora.feature.transactions.ahorroAcumulado
import com.gamora.feature.transactions.observar
import com.gamora.feature.goals.GamoraGoalStore
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputEditText

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var actualizandoSelector = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        observarMovimientos(::actualizarResumen)
        observar(GamoraGoalStore.goalsFlow, ::actualizarResumen)
        cargarPerfil(view)
        configurarSelectorTema(view)
        configurarCerrarSesion(view)
        configurarInformacionPersonal(view)
        configurarPreferenciasFinancieras(view)
        configurarPrivacidadEIA(view)
        configurarNotificaciones(view)
        configurarLegalesYAyuda(view)
        configurarIdioma(view)
        configurarEliminarDatos(view)
        configurarBloqueo(view)
        configurarRecurrentes(view)
        view.findViewById<View>(R.id.btnExportar).setOnClickListener { exportarDatos() }
    }

    internal fun cargarPerfil(view: View) {

        val tvNombre = view.findViewById<TextView>(
            R.id.tvNombrePerfil
        )

        val tvCorreo = view.findViewById<TextView>(
            R.id.tvCorreoPerfil
        )

        tvNombre.text = texto(R.string.profile_cargando_perfil)
        tvCorreo.text = ""

        val propietarioVista = viewLifecycleOwner

        AuthRepository.obtenerPerfil(
            onSuccess = { perfil ->

                if (!propietarioVista.lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                ) {
                    return@obtenerPerfil
                }

                val nombreCompleto = listOf(
                    perfil.nombre,
                    perfil.apellidoPaterno,
                    perfil.apellidoMaterno
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

                tvNombre.text = nombreCompleto.ifBlank {
                    texto(R.string.profile_usuario_gamora)
                }

                view.findViewById<TextView>(R.id.tvAvatar).text =
                    perfil.nombre.firstOrNull()?.uppercase() ?: texto(R.string.login_screen_g)

                tvCorreo.text = perfil.correo.ifBlank {
                    texto(R.string.profile_correo_no_disponible)
                }
            },
            onError = { mensaje ->

                if (!propietarioVista.lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                ) {
                    return@obtenerPerfil
                }

                tvNombre.text = texto(R.string.profile_no_se_pudo_cargar)

                Toast.makeText(
                    requireContext(),
                    mensaje,
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun actualizarResumen(view: View) {

        val movimientos = GamoraTransactionStore.movements

        val ingresos = movimientos
            .filter { it.isIncome }
            .sumOf { it.amountCents }

        val gastos = movimientos
            .filter { !it.isIncome }
            .sumOf { it.amountCents }

        val balance = ingresos - gastos

        view.findViewById<TextView>(
            R.id.tvPatrimonio
        ).text = dinero(balance)

        view.findViewById<TextView>(
            R.id.tvAhorroAcumulado
        ).text = dinero(movimientos.ahorroAcumulado())

        view.findViewById<TextView>(
            R.id.tvMetasActivas
        ).text = texto(R.string.comun_numero, GamoraGoalStore.goals.count { it.activa })
    }

    private fun configurarSelectorTema(view: View) {

        val grupoTema = view.findViewById<MaterialButtonToggleGroup>(
            R.id.groupTema
        )

        grupoTema.addOnButtonCheckedListener {
                _, checkedId, isChecked ->

            if (!isChecked || actualizandoSelector) {
                return@addOnButtonCheckedListener
            }

            val tema = when (checkedId) {
                R.id.btnTemaClaro -> GamoraTheme.LIGHT
                R.id.btnTemaOscuro -> GamoraTheme.DARK
                R.id.btnTemaSistema -> GamoraTheme.SYSTEM
                else -> return@addOnButtonCheckedListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                AppearancePreferences.setTheme(
                    requireContext(),
                    tema
                )

                // Se aplica después de guardar: cambiar el modo recrea la pantalla.
                AppCompatDelegate.setDefaultNightMode(tema.nightMode)
            }

            AuthRepository.guardarPreferencia(
                "apariencia",
                mapOf("tema" to tema.value),
                onSuccess = {},
                onError = ::mostrarToast
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                AppearancePreferences
                    .themeFlow(requireContext())
                    .collect { tema ->

                        actualizandoSelector = true

                        when (tema) {
                            GamoraTheme.LIGHT ->
                                grupoTema.check(R.id.btnTemaClaro)

                            GamoraTheme.DARK ->
                                grupoTema.check(R.id.btnTemaOscuro)

                            GamoraTheme.SYSTEM ->
                                grupoTema.check(R.id.btnTemaSistema)
                        }

                        actualizandoSelector = false
                    }
            }
        }
    }

    internal fun irAInicio() {
        startActivity(
            Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    internal fun mostrarToast(mensaje: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
        }
    }

    internal fun vistaActiva(): Boolean =
        isAdded && view != null &&
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    // Si la persona ya lo negó antes, Android no vuelve a preguntar: se abren los ajustes de la app.
    internal val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido || !isAdded) return@registerForActivityResult

            mostrarToast(texto(R.string.profile_activa_las_notificaciones_de))
            val paquete = requireContext().packageName
            SalidaBreve.permitir()
            startActivity(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, paquete)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquete, null))
                }
            )
        }

    /** Campo con contorno dentro de un diálogo; devuelve el EditText para leer su valor. */
    internal fun campoEnDialogo(
        contenedor: LinearLayout,
        etiqueta: String,
        valor: String,
        tipo: Int,
        editable: Boolean = true
    ): TextInputEditText = TextInputEditText(requireContext()).apply {
        hint = etiqueta
        setText(valor)
        inputType = tipo
        isEnabled = editable
        contenedor.addView(enCampo())
    }

}

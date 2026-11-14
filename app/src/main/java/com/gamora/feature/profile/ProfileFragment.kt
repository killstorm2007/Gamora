
package com.gamora.feature.profile

import androidx.core.os.LocaleListCompat
import com.gamora.core.texto
import com.gamora.core.nombre
import kotlinx.coroutines.flow.first
import com.gamora.core.security.puedeBloquear
import com.gamora.core.preferences.SeguridadPreferences
import com.gamora.core.ui.enCampo
import com.gamora.core.ui.centavosDe
import com.gamora.core.ui.dinero
import com.gamora.core.ui.Moneda
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.gamora.core.auth.GoogleNoDisponible
import com.gamora.core.auth.elegirCuentaGoogle
import com.gamora.core.notifications.cancelarAvisos
import com.gamora.core.notifications.puedeNotificar
import com.gamora.feature.transactions.MovimientoRecurrente
import com.gamora.feature.transactions.Recurrentes
import com.google.firebase.auth.AuthCredential
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.gamora.core.auth.ConsentData
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import android.text.InputType
import android.widget.ScrollView
import com.gamora.core.auth.PreferenciasFinancieras
import java.math.BigDecimal
import java.math.RoundingMode
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputLayout
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
    }

    private fun configurarRecurrentes(view: View) {
        view.findViewById<View>(R.id.btnRecurrentes).setOnClickListener {
            val uid = AuthRepository.obtenerUid() ?: return@setOnClickListener

            viewLifecycleOwner.lifecycleScope.launch {
                val lista = try {
                    Recurrentes.listar(uid)
                } catch (error: Exception) {
                    mostrarToast(error.localizedMessage ?: texto(R.string.profile_no_se_pudieron_cargar))
                    return@launch
                }
                if (!vistaActiva()) return@launch

                if (lista.isEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(texto(R.string.profile_movimientos_recurrentes_2))
                        .setMessage(
                            texto(R.string.profile_aun_no_tienes_ninguno)
                        )
                        .setPositiveButton(texto(R.string.profile_entendido), null)
                        .show()
                    return@launch
                }

                val etiquetas = lista.map { r ->
                    val signo = if (r.esIngreso) "+" else "−"
                    texto(R.string.profile_ndia_de_cada_mes, r.descripcion, signo, dinero(r.montoCentavos), r.dia, r.categoria.nombre(requireContext()))
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(texto(R.string.profile_movimientos_recurrentes_2))
                    .setItems(etiquetas.toTypedArray()) { _, indice ->
                        confirmarFinRecurrente(uid, lista[indice])
                    }
                    .setPositiveButton(texto(R.string.main_cerrar), null)
                    .show()
            }
        }
    }

    private fun confirmarFinRecurrente(uid: String, recurrente: MovimientoRecurrente) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(texto(R.string.profile_dejar_de_repetir))
            .setMessage(
                texto(R.string.profile_ya_no_se_registrara, recurrente.descripcion)
            )
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.profile_dejar_de_repetir)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        Recurrentes.eliminar(uid, recurrente.id)
                        mostrarToast(texto(R.string.profile_ya_no_se_repetira))
                    } catch (error: Exception) {
                        mostrarToast(error.localizedMessage ?: texto(R.string.profile_no_se_pudo_actualizar))
                    }
                }
            }
            .show()
    }

    private fun configurarBloqueo(view: View) {
        view.findViewById<View>(R.id.btnBloqueo).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val activo = SeguridadPreferences.bloqueoFlow(requireContext()).first()

                mostrarDialogoInterruptores(
                    texto(R.string.profile_bloqueo_de_la_app),
                    texto(R.string.profile_gamora_pedira_tu_huella),
                    listOf(texto(R.string.profile_activar_bloqueo) to activo)
                ) { valores, cerrar ->
                    val nuevo = valores[0]

                    if (nuevo && !puedeBloquear(requireContext())) {
                        mostrarToast(texto(R.string.profile_primero_configura_una_huella))
                        return@mostrarDialogoInterruptores
                    }

                    viewLifecycleOwner.lifecycleScope.launch {
                        SeguridadPreferences.setBloqueo(requireContext(), nuevo)
                        cerrar()
                        mostrarToast(if (nuevo) texto(R.string.profile_bloqueo_activado) else texto(R.string.profile_bloqueo_desactivado))
                    }
                }
            }
        }
    }

    private fun cargarPerfil(view: View) {

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

    private fun configurarCerrarSesion(view: View) {

        val btnCerrarSesion = view.findViewById<MaterialButton>(
            R.id.btnCerrarSesion
        )

        btnCerrarSesion.setOnClickListener {

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(texto(R.string.profile_cerrar_sesion))
                .setMessage(
                    texto(R.string.profile_estas_seguro_de_que)
                )
                .setNegativeButton(texto(R.string.import_review_cancelar), null)
                .setPositiveButton(texto(R.string.profile_cerrar_sesion)) { _, _ ->

                    cancelarAvisos(requireContext())
                    AuthRepository.cerrarSesion()
                    irAInicio()
                }
                .show()
        }
    }

    private fun irAInicio() {
        startActivity(
            Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun mostrarToast(mensaje: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
        }
    }

    private fun vistaActiva(): Boolean =
        isAdded && view != null &&
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /**
     * Diálogo con interruptores. [onGuardar] recibe los valores elegidos
     * y llama a cerrar() cuando se hayan guardado.
     */
    private fun mostrarDialogoInterruptores(
        titulo: String,
        mensaje: String,
        opciones: List<Pair<String, Boolean>>,
        botonExtra: Pair<String, () -> Unit>? = null,
        onGuardar: (valores: List<Boolean>, cerrar: () -> Unit) -> Unit
    ) {
        val contexto = requireContext()

        val contenedor = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
        }

        val interruptores = opciones.map { (etiqueta, activo) ->
            MaterialSwitch(contexto).apply {
                text = etiqueta
                isChecked = activo
                contenedor.addView(this)
            }
        }

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setView(contenedor)
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.import_review_guardar), null)
            .apply {
                botonExtra?.let { (etiqueta, accion) ->
                    setNeutralButton(etiqueta) { _, _ -> accion() }
                }
            }
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                onGuardar(interruptores.map { it.isChecked }) { dialogo.dismiss() }
            }
        }

        dialogo.show()
    }

    private fun configurarPrivacidadEIA(view: View) {

        fun editarConsentimiento(
            titulo: String,
            mensaje: String,
            etiqueta: String,
            valorActual: (ConsentData) -> Boolean,
            aplicar: (ConsentData, Boolean) -> ConsentData,
            botonExtra: Pair<String, () -> Unit>? = null
        ) {
            AuthRepository.obtenerConsentimiento(
                onSuccess = { actual ->
                    if (!vistaActiva()) return@obtenerConsentimiento

                    mostrarDialogoInterruptores(
                        titulo,
                        mensaje,
                        listOf(etiqueta to valorActual(actual)),
                        botonExtra
                    ) { valores, cerrar ->

                        if (valores[0] == valorActual(actual)) {
                            cerrar()
                            return@mostrarDialogoInterruptores
                        }

                        AuthRepository.actualizarConsentimiento(
                            aplicar(actual, valores[0]),
                            onSuccess = {
                                cerrar()
                                mostrarToast(texto(R.string.profile_preferencia_guardada))
                            },
                            onError = ::mostrarToast
                        )
                    }
                },
                onError = ::mostrarToast
            )
        }

        view.findViewById<View>(R.id.btnPrivacidad).setOnClickListener {
            editarConsentimiento(
                titulo = texto(R.string.profile_privacidad_y_seguridad_2),
                mensaje = texto(R.string.profile_decide_como_usa_gamora),
                etiqueta = texto(R.string.profile_permitir_analitica_de_uso),
                valorActual = { it.analiticaAutorizada },
                aplicar = { c, valor -> c.copy(analiticaAutorizada = valor) },
                botonExtra = texto(R.string.profile_cambiar_contrasena) to {
                    val correo = AuthRepository.obtenerCorreo()

                    if (correo == null) {
                        mostrarToast(texto(R.string.profile_no_hay_un_correo))
                    } else {
                        AuthRepository.recuperarPassword(
                            correo,
                            onSuccess = {
                                mostrarToast(texto(R.string.profile_te_enviamos_un_correo, correo))
                            },
                            onError = ::mostrarToast
                        )
                    }
                }
            )
        }

        view.findViewById<View>(R.id.btnPreferenciasIA).setOnClickListener {
            editarConsentimiento(
                titulo = texto(R.string.profile_inteligencia_artificial_2),
                mensaje = texto(R.string.profile_el_asistente_puede_usar),
                etiqueta = texto(R.string.profile_permitir_que_la_ia),
                valorActual = { it.iaAutorizada },
                aplicar = { c, valor -> c.copy(iaAutorizada = valor) }
            )
        }
    }

    // Si la persona ya lo negó antes, Android no vuelve a preguntar: se abren los ajustes de la app.
    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido || !isAdded) return@registerForActivityResult

            mostrarToast(texto(R.string.profile_activa_las_notificaciones_de))
            val paquete = requireContext().packageName
            startActivity(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, paquete)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquete, null))
                }
            )
        }

    private fun configurarNotificaciones(view: View) {

        // clave en Firestore, etiqueta, valor por defecto
        val opciones = listOf(
            Triple("alertasPresupuesto", texto(R.string.profile_alertas_de_presupuesto), true),
            Triple("recordatoriosMetas", texto(R.string.profile_recordatorios_de_metas), true),
            Triple("resumenSemanal", texto(R.string.profile_resumen_semanal), false)
        )

        view.findViewById<View>(R.id.btnNotificaciones).setOnClickListener {

            AuthRepository.obtenerPreferencia(
                "notificaciones",
                onSuccess = { datos ->
                    if (!vistaActiva()) return@obtenerPreferencia

                    mostrarDialogoInterruptores(
                        texto(R.string.profile_notificaciones_2),
                        texto(R.string.profile_elige_que_avisos_quieres),
                        opciones.map { (clave, etiqueta, porDefecto) ->
                            etiqueta to (datos?.get(clave) as? Boolean ?: porDefecto)
                        }
                    ) { valores, cerrar ->

                        AuthRepository.guardarPreferencia(
                            "notificaciones",
                            opciones.map { it.first }.zip(valores).toMap(),
                            onSuccess = {
                                cerrar()
                                mostrarToast(texto(R.string.profile_notificaciones_guardadas))
                                if (valores.any { it } && !puedeNotificar(requireContext())) {
                                    permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onError = ::mostrarToast
                        )
                    }
                },
                onError = ::mostrarToast
            )
        }
    }

    /** Idioma de la app (idiomas por app de AndroidX; en Android 13+ también desde Ajustes del teléfono). */
    private fun configurarIdioma(view: View) {
        view.findViewById<View>(R.id.btnIdioma).setOnClickListener {
            val etiquetas = arrayOf("", "es", "en")
            val actual = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_idioma_titulo)
                .setSingleChoiceItems(
                    arrayOf(getString(R.string.profile_idioma_sistema), "Español", "English"),
                    etiquetas.indexOf(actual).coerceAtLeast(0)
                ) { dialogo, opcion ->
                    dialogo.dismiss()
                    // La app se vuelve a dibujar en el idioma elegido; la sesión se conserva.
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(etiquetas[opcion]))
                }
                .setNegativeButton(R.string.import_review_cancelar, null)
                .show()
        }
    }

    private fun configurarLegalesYAyuda(view: View) {

        fun mostrarDocumento(titulo: String, recursoTexto: Int) {
            val vista = layoutInflater.inflate(R.layout.dialog_documento_legal, null)
            vista.findViewById<TextView>(R.id.tvDocumento).text = getString(recursoTexto)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(titulo)
                .setView(vista)
                .setPositiveButton(texto(R.string.main_cerrar), null)
                .show()
        }

        view.findViewById<View>(R.id.btnDocumentosLegales).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(texto(R.string.profile_documentos_legales_2))
                .setItems(arrayOf(texto(R.string.register_terminos_y_condiciones), texto(R.string.mainactivity_politica_de_privacidad))) { _, opcion ->
                    if (opcion == 0) {
                        mostrarDocumento(texto(R.string.register_terminos_y_condiciones), R.string.terminos_condiciones)
                    } else {
                        mostrarDocumento(texto(R.string.mainactivity_politica_de_privacidad), R.string.politica_privacidad)
                    }
                }
                .setNegativeButton(texto(R.string.main_cerrar), null)
                .show()
        }

        view.findViewById<View>(R.id.btnAyuda).setOnClickListener {
            val correoSoporte = getString(R.string.correo_soporte)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(texto(R.string.profile_ayuda_y_soporte_2))
                .setMessage(
                    texto(R.string.profile_registra_ingresos_y_gastos, correoSoporte)
                )
                .setNegativeButton(texto(R.string.main_cerrar), null)
                .setPositiveButton(texto(R.string.profile_escribir_a_soporte)) { _, _ ->
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$correoSoporte"))
                        .putExtra(Intent.EXTRA_SUBJECT, texto(R.string.profile_ayuda_con_gamora))

                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        mostrarToast(texto(R.string.profile_no_hay_una_app))
                    }
                }
                .show()
        }
    }

    private fun configurarEliminarDatos(view: View) {

        view.findViewById<View>(R.id.btnEliminarDatos).setOnClickListener {

            val contexto = requireContext()

            val conGoogle = AuthRepository.esCuentaGoogle()

            val campoPassword = TextInputEditText(contexto).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            val contenedor = LinearLayout(contexto).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 12, 48, 0)
                addView(
                    TextInputLayout(contexto, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                        hint = texto(R.string.login_screen_contrasena)
                        endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                        addView(campoPassword)
                    }
                )
                if (conGoogle) visibility = View.GONE
            }

            val dialogo = MaterialAlertDialogBuilder(contexto)
                .setTitle(texto(R.string.profile_eliminar_mi_cuenta_y))
                .setMessage(
                    texto(R.string.profile_se_borraran_de_forma) +
                            if (conGoogle) texto(R.string.profile_confirma_con_tu_cuenta) else texto(R.string.profile_escribe_tu_contrasena_para)
                )
                .setView(contenedor)
                .setNegativeButton(texto(R.string.import_review_cancelar), null)
                .setPositiveButton(texto(R.string.goals_eliminar), null)
                .create()

            dialogo.setOnShowListener {
                val botonEliminar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
                botonEliminar.setTextColor(resources.getColor(R.color.gamora_expense, contexto.theme))

                fun eliminarCon(credencial: AuthCredential) {
                    AuthRepository.eliminarCuenta(
                        credencial,
                        onSuccess = {
                            dialogo.dismiss()
                            cancelarAvisos(contexto)
                            mostrarToast(texto(R.string.profile_tu_cuenta_y_tus))
                            if (isAdded) irAInicio()
                        },
                        onError = { mensaje ->
                            botonEliminar.isEnabled = true
                            mostrarToast(mensaje)
                        }
                    )
                }

                botonEliminar.setOnClickListener {
                    if (conGoogle) {
                        botonEliminar.isEnabled = false

                        viewLifecycleOwner.lifecycleScope.launch {
                            val cuenta = try {
                                elegirCuentaGoogle(requireActivity())
                            } catch (error: GoogleNoDisponible) {
                                mostrarToast(error.message.orEmpty())
                                null
                            }

                            if (cuenta == null) botonEliminar.isEnabled = true
                            else eliminarCon(cuenta.credencial)
                        }
                        return@setOnClickListener
                    }

                    val password = campoPassword.text?.toString().orEmpty()

                    if (password.isEmpty()) {
                        campoPassword.error = texto(R.string.profile_escribe_tu_contrasena)
                        return@setOnClickListener
                    }

                    val credencial = AuthRepository.credencialConPassword(password) ?: run {
                        mostrarToast(texto(R.string.profile_no_hay_un_correo))
                        return@setOnClickListener
                    }

                    botonEliminar.isEnabled = false
                    eliminarCon(credencial)
                }
            }

            dialogo.show()
        }
    }

    private fun configurarPreferenciasFinancieras(view: View){
        val boton = view.findViewById<TextView>(
            R.id.btnPreferenciasFinancieras
        )

        boton.setOnClickListener {
            AuthRepository.obtenerPreferenciasFinancieras(
                onSuccess = { preferencias ->
                    if (vistaActiva()) mostrarDialogoEditarPreferencias(preferencias)
                },
                onError = ::mostrarToast
            )
        }
    }

    private fun configurarInformacionPersonal(view: View) {

        val boton = view.findViewById<TextView>(
            R.id.btnInformacionPersonal
        )

        boton.setOnClickListener {

            AuthRepository.obtenerPerfil(
                onSuccess = { perfil ->
                    if (!vistaActiva()) return@obtenerPerfil

                    mostrarDialogoEditarPerfil(
                        perfil.nombre,
                        perfil.apellidoPaterno,
                        perfil.apellidoMaterno,
                        perfil.correo,
                        perfil.telefono
                    )
                },
                onError = ::mostrarToast
            )
        }
    }

    /** Campo con contorno dentro de un diálogo; devuelve el EditText para leer su valor. */
    private fun campoEnDialogo(
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

    private fun mostrarDialogoEditarPreferencias(
        preferencias: PreferenciasFinancieras
    ){
        val contexto = requireContext()

        val contenedor = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48,12,48,0)
        }

        val scroll = ScrollView(contexto).apply {
            isFillViewport = true
            addView(contenedor)
        }

        fun crearCampo(etiqueta: String, valor: String, tipo: Int) =
            campoEnDialogo(contenedor, etiqueta, valor, tipo)

        // Vacío cuenta como 0; el resto de las reglas las valida AuthRepository.
        fun convertirCentavos(texto: String): Long? =
            if (texto.isBlank()) 0L else centavosDe(texto)

        fun mostrarError(mensaje: String){
            Toast.makeText(
                contexto,
                mensaje,
                Toast.LENGTH_LONG
            ).show()
        }

        val tipoDecimal =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        // Lista desplegable: solo se puede elegir una moneda válida.
        val nombresMoneda = Moneda.DISPONIBLES.map { Moneda.NOMBRES.getValue(it) }
        val campoMoneda = MaterialAutoCompleteTextView(contexto).apply {
            inputType = InputType.TYPE_NULL
            setSimpleItems(nombresMoneda.toTypedArray())
            setText(Moneda.NOMBRES[preferencias.moneda] ?: nombresMoneda.first(), false)
        }
        contenedor.addView(
            TextInputLayout(
                contexto, null,
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle
            ).apply {
                hint = texto(R.string.profile_moneda)
                endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                addView(campoMoneda)
            }
        )

        val campoIngreso = crearCampo(
            texto(R.string.profile_ingreso_mensual),
            BigDecimal.valueOf(
                preferencias.ingresoMensualCentavos,
                2
            ).toPlainString(),
            tipoDecimal
        )

        val campoPresupuesto = crearCampo(
            texto(R.string.profile_presupuesto_mensual),
            BigDecimal.valueOf(
                preferencias.presupuestoMensualCentavos,
                2
            ).toPlainString(),
            tipoDecimal
        )

        val campoDia = crearCampo(
            texto(R.string.profile_dia_de_inicio_del),
            preferencias.diaInicioPeriodo.toString(),
            InputType.TYPE_CLASS_NUMBER
        )

        val campoAhorro = crearCampo(
            texto(R.string.profile_objetivo_de_ahorro_mensual),
            BigDecimal.valueOf(
                preferencias.objetivoAhorroMensualCentavos,
                2
            ).toPlainString(),
            tipoDecimal
        )

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(texto(R.string.profile_preferencias_financieras_2))
            .setMessage(
                texto(R.string.profile_personaliza_la_forma_en)
            )
            .setView(scroll)
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.import_review_guardar), null)
            .create()

        dialogo.setOnShowListener {

            dialogo.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val moneda = Moneda.DISPONIBLES
                    .getOrElse(nombresMoneda.indexOf(campoMoneda.text.toString())) { "MXN" }

                val ingreso = convertirCentavos(
                    campoIngreso.text?.toString().orEmpty()
                )

                val presupuesto = convertirCentavos(
                    campoPresupuesto.text?.toString().orEmpty()
                )

                val ahorro = convertirCentavos(
                    campoAhorro.text?.toString().orEmpty()
                )

                val dia = campoDia.text
                    ?.toString()
                    ?.trim()
                    ?.toIntOrNull()

                if (ingreso == null ||
                    presupuesto == null ||
                    ahorro == null
                ) {
                    mostrarError(texto(R.string.profile_revisa_las_cantidades_ingresadas))
                    return@setOnClickListener
                }

                val nuevasPreferencias = PreferenciasFinancieras(
                    moneda = moneda,
                    ingresoMensualCentavos = ingreso,
                    presupuestoMensualCentavos = presupuesto,
                    diaInicioPeriodo = dia ?: 0,
                    objetivoAhorroMensualCentavos = ahorro
                )

                AuthRepository.guardarPreferenciasFinancieras(
                    preferencias = nuevasPreferencias,

                    onSuccess = {

                        if (!isAdded) {
                            return@guardarPreferenciasFinancieras
                        }

                        dialogo.dismiss()
                        Moneda.cambiar(requireContext(), moneda)

                        Toast.makeText(
                            requireContext(),
                            texto(R.string.profile_preferencias_financieras_guardadas),
                            Toast.LENGTH_SHORT
                        ).show()
                    },

                    onError = { mensaje ->

                        if (!isAdded) {
                            return@guardarPreferenciasFinancieras
                        }

                        mostrarError(mensaje)
                    }
                )
            }
        }

        dialogo.show()
    }

    private fun mostrarDialogoEditarPerfil(
        nombreActual: String,
        paternoActual: String,
        maternoActual: String,
        correoActual: String,
        telefonoActual: String
    ) {

        val contexto = requireContext()

        val contenedor = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
        }

        fun crearCampo(etiqueta: String, valor: String, editable: Boolean = true) =
            campoEnDialogo(contenedor, etiqueta, valor, InputType.TYPE_CLASS_TEXT, editable)

        val campoNombre = crearCampo(texto(R.string.register_screen_nombre), nombreActual)
        val campoPaterno = crearCampo(
            texto(R.string.register_screen_apellido_paterno),
            paternoActual
        )
        val campoMaterno = crearCampo(
            texto(R.string.register_screen_apellido_materno),
            maternoActual
        )
        val campoCorreo = crearCampo(
            texto(R.string.login_screen_correo_electronico),
            correoActual,
            false
        )
        val campoTelefono = crearCampo(
            texto(R.string.profile_telefono),
            telefonoActual
        )

        MaterialAlertDialogBuilder(contexto)
            .setTitle(texto(R.string.profile_informacion_personal_2))
            .setMessage(
                texto(R.string.profile_manten_tus_datos_actualizados)
            )
            .setView(contenedor)
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.import_review_guardar), null)
            .create()
            .also { dialogo ->

                dialogo.setOnShowListener {

                    dialogo.getButton(
                        AlertDialog.BUTTON_POSITIVE
                    ).setOnClickListener {

                        val nombre = campoNombre.text
                            ?.toString()?.trim().orEmpty()

                        val paterno = campoPaterno.text
                            ?.toString()?.trim().orEmpty()

                        val materno = campoMaterno.text
                            ?.toString()?.trim().orEmpty()

                        val telefono = campoTelefono.text
                            ?.toString()?.trim().orEmpty()

                        AuthRepository.actualizarPerfil(
                            nombre = nombre,
                            apellidoPaterno = paterno,
                            apellidoMaterno = materno,
                            telefono = telefono,
                            onSuccess = {

                                if (!isAdded) return@actualizarPerfil

                                dialogo.dismiss()

                                Toast.makeText(
                                    requireContext(),
                                    texto(R.string.profile_informacion_actualizada_correctamente),
                                    Toast.LENGTH_SHORT
                                ).show()

                                view?.let { cargarPerfil(it) }
                            },
                            onError = { mensaje ->

                                if (!isAdded) return@actualizarPerfil

                                Toast.makeText(
                                    requireContext(),
                                    mensaje,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }

                dialogo.show()
            }
    }

}

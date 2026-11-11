
package com.gamora.feature.profile

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
                    mostrarToast(error.localizedMessage ?: "No se pudieron cargar los recurrentes.")
                    return@launch
                }
                if (!vistaActiva()) return@launch

                if (lista.isEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Movimientos recurrentes")
                        .setMessage(
                            "Aún no tienes ninguno. Al registrar un ingreso o gasto, marca " +
                                    "«Repetir cada mes» y Gamora lo registrará solo cada mes."
                        )
                        .setPositiveButton("Entendido", null)
                        .show()
                    return@launch
                }

                val etiquetas = lista.map { r ->
                    val signo = if (r.esIngreso) "+" else "−"
                    "${r.descripcion} · $signo${dinero(r.montoCentavos)}\nDía ${r.dia} de cada mes · ${r.categoria}"
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Movimientos recurrentes")
                    .setItems(etiquetas.toTypedArray()) { _, indice ->
                        confirmarFinRecurrente(uid, lista[indice])
                    }
                    .setPositiveButton("Cerrar", null)
                    .show()
            }
        }
    }

    private fun confirmarFinRecurrente(uid: String, recurrente: MovimientoRecurrente) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Dejar de repetir")
            .setMessage(
                "«${recurrente.descripcion}» ya no se registrará cada mes. " +
                        "Los movimientos que ya se registraron se conservan."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Dejar de repetir") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        Recurrentes.eliminar(uid, recurrente.id)
                        mostrarToast("Ya no se repetirá")
                    } catch (error: Exception) {
                        mostrarToast(error.localizedMessage ?: "No se pudo actualizar.")
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
                    "Bloqueo de la app",
                    "Gamora pedirá tu huella, rostro o el PIN de tu teléfono cada vez que la abras.",
                    listOf("Activar bloqueo" to activo)
                ) { valores, cerrar ->
                    val nuevo = valores[0]

                    if (nuevo && !puedeBloquear(requireContext())) {
                        mostrarToast("Primero configura una huella o un PIN en los ajustes de tu teléfono.")
                        return@mostrarDialogoInterruptores
                    }

                    viewLifecycleOwner.lifecycleScope.launch {
                        SeguridadPreferences.setBloqueo(requireContext(), nuevo)
                        cerrar()
                        mostrarToast(if (nuevo) "Bloqueo activado" else "Bloqueo desactivado")
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

        tvNombre.text = "Cargando perfil..."
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
                    "Usuario Gamora"
                }

                view.findViewById<TextView>(R.id.tvAvatar).text =
                    perfil.nombre.firstOrNull()?.uppercase() ?: "G"

                tvCorreo.text = perfil.correo.ifBlank {
                    "Correo no disponible"
                }
            },
            onError = { mensaje ->

                if (!propietarioVista.lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                ) {
                    return@obtenerPerfil
                }

                tvNombre.text = "No se pudo cargar el perfil"

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
        ).text = GamoraGoalStore.goals.count { it.activa }.toString()
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
                .setTitle("Cerrar sesión")
                .setMessage(
                    "¿Estás seguro de que deseas cerrar tu sesión de Gamora?"
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión") { _, _ ->

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
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
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
                                mostrarToast("Preferencia guardada")
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
                titulo = "Privacidad y seguridad",
                mensaje = "Decide cómo usa Gamora tus datos y protege tu cuenta.",
                etiqueta = "Permitir analítica de uso",
                valorActual = { it.analiticaAutorizada },
                aplicar = { c, valor -> c.copy(analiticaAutorizada = valor) },
                botonExtra = "Cambiar contraseña" to {
                    val correo = AuthRepository.obtenerCorreo()

                    if (correo == null) {
                        mostrarToast("No hay un correo asociado a la cuenta.")
                    } else {
                        AuthRepository.recuperarPassword(
                            correo,
                            onSuccess = {
                                mostrarToast("Te enviamos un correo a $correo para cambiar tu contraseña.")
                            },
                            onError = ::mostrarToast
                        )
                    }
                }
            )
        }

        view.findViewById<View>(R.id.btnPreferenciasIA).setOnClickListener {
            editarConsentimiento(
                titulo = "Inteligencia artificial",
                mensaje = "El asistente puede usar tus movimientos para darte recomendaciones personalizadas.",
                etiqueta = "Permitir que la IA use mis datos financieros",
                valorActual = { it.iaAutorizada },
                aplicar = { c, valor -> c.copy(iaAutorizada = valor) }
            )
        }
    }

    // Si la persona ya lo negó antes, Android no vuelve a preguntar: se abren los ajustes de la app.
    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido || !isAdded) return@registerForActivityResult

            mostrarToast("Activa las notificaciones de Gamora para recibir los avisos.")
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            )
        }

    private fun configurarNotificaciones(view: View) {

        // clave en Firestore, etiqueta, valor por defecto
        val opciones = listOf(
            Triple("alertasPresupuesto", "Alertas de presupuesto", true),
            Triple("recordatoriosMetas", "Recordatorios de metas", true),
            Triple("resumenSemanal", "Resumen semanal", false)
        )

        view.findViewById<View>(R.id.btnNotificaciones).setOnClickListener {

            AuthRepository.obtenerPreferencia(
                "notificaciones",
                onSuccess = { datos ->
                    if (!vistaActiva()) return@obtenerPreferencia

                    mostrarDialogoInterruptores(
                        "Notificaciones",
                        "Elige qué avisos quieres recibir de Gamora.",
                        opciones.map { (clave, etiqueta, porDefecto) ->
                            etiqueta to (datos?.get(clave) as? Boolean ?: porDefecto)
                        }
                    ) { valores, cerrar ->

                        AuthRepository.guardarPreferencia(
                            "notificaciones",
                            opciones.map { it.first }.zip(valores).toMap(),
                            onSuccess = {
                                cerrar()
                                mostrarToast("Notificaciones guardadas")
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

    private fun configurarLegalesYAyuda(view: View) {

        fun mostrarDocumento(titulo: String, recursoTexto: Int) {
            val vista = layoutInflater.inflate(R.layout.dialog_documento_legal, null)
            vista.findViewById<TextView>(R.id.tvDocumento).text = getString(recursoTexto)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(titulo)
                .setView(vista)
                .setPositiveButton("Cerrar", null)
                .show()
        }

        view.findViewById<View>(R.id.btnDocumentosLegales).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Documentos legales")
                .setItems(arrayOf("Términos y condiciones", "Política de privacidad")) { _, opcion ->
                    if (opcion == 0) {
                        mostrarDocumento("Términos y condiciones", R.string.terminos_condiciones)
                    } else {
                        mostrarDocumento("Política de privacidad", R.string.politica_privacidad)
                    }
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }

        view.findViewById<View>(R.id.btnAyuda).setOnClickListener {
            val correoSoporte = getString(R.string.correo_soporte)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ayuda y soporte")
                .setMessage(
                    "• Registra ingresos y gastos desde Inicio.\n" +
                            "• Ajusta tu presupuesto en Preferencias financieras.\n" +
                            "• Tus datos se guardan en tu cuenta y se sincronizan entre dispositivos.\n\n" +
                            "¿Necesitas más ayuda? Escríbenos a $correoSoporte"
                )
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Escribir a soporte") { _, _ ->
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$correoSoporte"))
                        .putExtra(Intent.EXTRA_SUBJECT, "Ayuda con Gamora")

                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        mostrarToast("No hay una app de correo instalada.")
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
                        hint = "Contraseña"
                        endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                        addView(campoPassword)
                    }
                )
                if (conGoogle) visibility = View.GONE
            }

            val dialogo = MaterialAlertDialogBuilder(contexto)
                .setTitle("Eliminar mi cuenta y mis datos")
                .setMessage(
                    "Se borrarán de forma permanente tu perfil, movimientos, preferencias " +
                            "y consentimientos, y tu cuenta de acceso. Esta acción no se puede deshacer.\n\n" +
                            if (conGoogle) "Confirma con tu cuenta de Google." else "Escribe tu contraseña para confirmar."
                )
                .setView(contenedor)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", null)
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
                            mostrarToast("Tu cuenta y tus datos fueron eliminados.")
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
                        campoPassword.error = "Escribe tu contraseña"
                        return@setOnClickListener
                    }

                    val credencial = AuthRepository.credencialConPassword(password) ?: run {
                        mostrarToast("No hay un correo asociado a la cuenta.")
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
                hint = "Moneda"
                endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                addView(campoMoneda)
            }
        )

        val campoIngreso = crearCampo(
            "Ingreso Mensual",
            BigDecimal.valueOf(
                preferencias.ingresoMensualCentavos,
                2
            ).toPlainString(),
            tipoDecimal
        )

        val campoPresupuesto = crearCampo(
            "Presupuesto mensual",
            BigDecimal.valueOf(
                preferencias.presupuestoMensualCentavos,
                2
            ).toPlainString(),
            tipoDecimal
        )

        val campoDia = crearCampo(
            "Dia de inicio del periodo (1-28)",
            preferencias.diaInicioPeriodo.toString(),
            InputType.TYPE_CLASS_NUMBER
        )

        val campoAhorro = crearCampo(
            "Objetivo de Ahorro Mensual",
            BigDecimal.valueOf(
                preferencias.objetivoAhorroMensualCentavos,
                2
            ).toPlainString(),
            tipoDecimal
        )

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle("Preferencias financieras")
            .setMessage(
                "Personaliza la forma en que Gamora organiza tus finanzas."
            )
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
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
                    mostrarError("Revisa las cantidades ingresadas.")
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
                            "Preferencias financieras guardadas",
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

        val campoNombre = crearCampo("Nombre", nombreActual)
        val campoPaterno = crearCampo(
            "Apellido paterno",
            paternoActual
        )
        val campoMaterno = crearCampo(
            "Apellido materno",
            maternoActual
        )
        val campoCorreo = crearCampo(
            "Correo electrónico",
            correoActual,
            false
        )
        val campoTelefono = crearCampo(
            "Teléfono",
            telefonoActual
        )

        MaterialAlertDialogBuilder(contexto)
            .setTitle("Información personal")
            .setMessage(
                "Mantén tus datos actualizados en Gamora."
            )
            .setView(contenedor)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
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
                                    "Información actualizada correctamente",
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

package com.gamora.feature.profile

import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.gamora.core.auth.PreferenciasFinancieras
import com.gamora.core.texto
import com.gamora.core.ui.IconoDialogo
import com.gamora.core.ui.Moneda
import com.gamora.core.ui.centavosDe
import com.gamora.core.ui.contenidoDesplazable
import com.gamora.core.ui.dialogo
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.math.BigDecimal
import androidx.lifecycle.lifecycleScope
import com.gamora.core.Conexion
import com.gamora.core.mensajeDeError
import com.gamora.core.ui.enCampo
import com.gamora.feature.transactions.GamoraTransactionStore
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

// Perfil › Preferencias financieras (moneda, ingreso, presupuesto, día de inicio y objetivo de ahorro).

internal fun ProfileFragment.configurarPreferenciasFinancieras(view: View){
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

internal fun ProfileFragment.mostrarDialogoEditarPreferencias(
    preferencias: PreferenciasFinancieras
){
    val contexto = requireContext()

    val contenedor = LinearLayout(contexto).apply { orientation = LinearLayout.VERTICAL }

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

    val dialogo = dialogo(contexto, IconoDialogo.AHORRO)
        .setTitle(texto(R.string.profile_preferencias_financieras_2))
        .setView(contenidoDesplazable(contexto, texto(R.string.profile_personaliza_la_forma_en), contenedor))
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

            fun guardar(preferenciasFinales: PreferenciasFinancieras) {
                AuthRepository.guardarPreferenciasFinancieras(
                    preferencias = preferenciasFinales,
                    onSuccess = {
                        if (!isAdded) return@guardarPreferenciasFinancieras
                        dialogo.dismiss()
                        Moneda.cambiar(requireContext(), moneda)
                        mostrarToast(texto(R.string.profile_preferencias_financieras_guardadas))
                    },
                    onError = { mensaje -> if (isAdded) mostrarError(mensaje) }
                )
            }

            if (moneda == preferencias.moneda) {
                guardar(nuevasPreferencias)
            } else {
                // Otra moneda: ¿convertir los montos o solo cambiar el símbolo?
                preguntarConversion(preferencias.moneda, moneda) { factor ->
                    if (factor == null) {
                        guardar(nuevasPreferencias)
                    } else {
                        convertirMontos(factor, dialogo) {
                            guardar(
                                nuevasPreferencias.copy(
                                    ingresoMensualCentavos = convertirMonto(ingreso, factor, minimo = 0),
                                    presupuestoMensualCentavos = convertirMonto(presupuesto, factor, minimo = 0),
                                    objetivoAhorroMensualCentavos = convertirMonto(ahorro, factor, minimo = 0)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    dialogo.show()
}

/**
 * Al cambiar de moneda pregunta qué hacer con los montos. [alElegir] recibe el factor para convertirlos,
 * o null para solo cambiar el símbolo (p. ej. si se eligió mal la moneda al registrarse).
 * El tipo de cambio se pide como "1 [moneda fuerte] = X [moneda débil]", que es como se publica.
 */
private fun ProfileFragment.preguntarConversion(anterior: String, nueva: String, alElegir: (BigDecimal?) -> Unit) {
    val contexto = requireContext()
    val (fuerte, debil) = monedasDelTipoDeCambio(anterior, nueva)

    val campoTasa = TextInputEditText(contexto).apply {
        hint = texto(R.string.moneda_tipo_de_cambio, fuerte, debil)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        maxLines = 1
    }

    val dialogo = dialogo(contexto, IconoDialogo.EXPORTAR)
        .setTitle(texto(R.string.moneda_cambiar_a, Moneda.NOMBRES[nueva] ?: nueva))
        .setView(contenidoDesplazable(
            contexto,
            texto(R.string.moneda_explicacion, Moneda.NOMBRES[anterior] ?: anterior),
            LinearLayout(contexto).apply { orientation = LinearLayout.VERTICAL; addView(campoTasa.enCampo()) }
        ))
        .setNegativeButton(texto(R.string.import_review_cancelar), null)
        .setNeutralButton(texto(R.string.moneda_solo_simbolo)) { _, _ -> alElegir(null) }
        .setPositiveButton(texto(R.string.moneda_convertir), null)
        .create()

    dialogo.setOnShowListener {
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val tasa = campoTasa.text?.toString()?.trim()?.replace(',', '.')?.toBigDecimalOrNull()
            val factor = tasa?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100_000) }
                ?.let { factorDeConversion(anterior, nueva, it) }

            if (factor == null) {
                campoTasa.error = texto(R.string.moneda_tipo_de_cambio_invalido)
                return@setOnClickListener
            }
            dialogo.dismiss()
            alElegir(factor)
        }
    }
    dialogo.show()
}

/** Convierte todo en Firestore y después llama a [alTerminar]; mientras, el diálogo no se puede tocar. */
private fun ProfileFragment.convertirMontos(factor: BigDecimal, dialogoPreferencias: AlertDialog, alTerminar: () -> Unit) {
    val uid = AuthRepository.obtenerUid() ?: return
    val contexto = requireContext().applicationContext

    if (!Conexion.enLinea.value) {
        mostrarToast(texto(R.string.error_sin_conexion))
        return
    }

    val botones = listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).mapNotNull(dialogoPreferencias::getButton)
    botones.forEach { it.isEnabled = false }
    dialogoPreferencias.setCancelable(false)
    mostrarToast(texto(R.string.moneda_convirtiendo))

    viewLifecycleOwner.lifecycleScope.launch {
        try {
            CambioDeMoneda.convertirTodo(uid, factor)
            // El historial de más de un año no llega por el listener: se vuelve a leer.
            GamoraTransactionStore.escuchar(contexto, uid, forzar = true)
            alTerminar()
        } catch (error: Exception) {
            botones.forEach { it.isEnabled = true }
            dialogoPreferencias.setCancelable(true)
            mostrarToast(mensajeDeError(error, R.string.moneda_no_se_pudo_convertir))
        }
    }
}

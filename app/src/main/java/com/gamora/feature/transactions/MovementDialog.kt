package com.gamora.feature.transactions

import com.gamora.core.ui.formatoFecha
import com.gamora.core.texto
import com.gamora.core.nombre
import com.gamora.core.Categoria
import android.app.DatePickerDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.google.android.material.checkbox.MaterialCheckBox
import com.gamora.core.ui.LOCALE_MX
import com.gamora.core.ui.centavosDe
import com.gamora.core.ui.dp
import com.gamora.core.ui.enCampo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date


/** Abre el formulario para editar [movimiento] (ingreso o gasto según su tipo). */
fun Fragment.editarMovimiento(movimiento: GamoraMovement) =
    mostrarDialogoMovimiento(movimiento.isIncome, movimiento)

/**
 * Diálogo para registrar un ingreso o gasto; lo usan Inicio y Transacciones.
 * Con [existente] edita ese movimiento y permite eliminarlo.
 * Con [alGuardarLocal] no escribe en Firestore: entrega el movimiento editado (revisión de importación).
 */
fun Fragment.mostrarDialogoMovimiento(
    esIngreso: Boolean,
    existente: GamoraMovement? = null,
    alGuardarLocal: ((GamoraMovement) -> Unit)? = null
) {
    val context = requireContext()
    val formatoDia = formatoFecha("EEEEdMMMMyyyy")
    val categorias = if (esIngreso) Categoria.DE_INGRESO else Categoria.DE_GASTO

    val form = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(4), dp(24), 0)
    }

    val descriptionInput = TextInputEditText(context).apply {
        hint = texto(R.string.movement_dialog_descripcion_ej_nomina)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        maxLines = 1
        filters = arrayOf(InputFilter.LengthFilter(200))
        existente?.let { setText(it.description) }
    }

    val amountInput = TextInputEditText(context).apply {
        hint = texto(R.string.movement_dialog_importe_ej_250_50)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        maxLines = 1
        existente?.let { setText(BigDecimal.valueOf(it.amountCents, 2).toPlainString()) }
    }

    // Fecha elegida; al editar se conserva la hora original para no alterar el orden.
    val fecha = Calendar.getInstance().apply {
        existente?.let { timeInMillis = it.timestamp }
    }

    val dateInput = TextInputEditText(context).apply {
        hint = texto(R.string.import_review_fecha)
        isFocusable = false
        isClickable = true
        setText(formatoDia.format(fecha.time))
        setOnClickListener {
            DatePickerDialog(
                context,
                { _, anio, mes, dia ->
                    fecha.set(anio, mes, dia)
                    setText(formatoDia.format(fecha.time))
                },
                fecha.get(Calendar.YEAR),
                fecha.get(Calendar.MONTH),
                fecha.get(Calendar.DAY_OF_MONTH)
            ).apply {
                // No se registran movimientos a futuro.
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }
    }

    val categorySpinner = Spinner(context).apply {
        adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            categorias.map { it.nombre(context) }
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        existente?.let { mov ->
            categorias.indexOf(mov.category).takeIf { it >= 0 }?.let(::setSelection)
        }
    }

    form.addView(descriptionInput.enCampo())
    form.addView(amountInput.enCampo())
    form.addView(dateInput.enCampo())
    form.addView(
        TextView(context).apply {
            text = texto(R.string.import_review_categoria)
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.gamora_text_secondary))
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) }
    )
    form.addView(categorySpinner)

    // Solo al registrar: editar un movimiento no cambia su repetición.
    val repetirCheck = if (existente == null && alGuardarLocal == null) MaterialCheckBox(context).apply {
        text = texto(R.string.movement_dialog_repetir_cada_mes_renta)
        form.addView(this, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
    } else null

    fun mostrarError(mensaje: String) =
        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()

    val tipo = texto(if (esIngreso) R.string.movement_tipo_ingreso else R.string.movement_tipo_gasto)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (existente == null) texto(R.string.movement_dialog_registrar, tipo) else texto(R.string.movement_dialog_editar, tipo))
        .setView(form)
        .setNegativeButton(texto(R.string.import_review_cancelar), null)
        .setPositiveButton(texto(R.string.import_review_guardar), null)
        .apply {
            if (existente != null && alGuardarLocal == null) {
                setNeutralButton(texto(R.string.goals_eliminar)) { _, _ ->
                    MaterialAlertDialogBuilder(context)
                        .setTitle(texto(R.string.movement_dialog_eliminar, tipo))
                        .setMessage(texto(R.string.movement_dialog_eliminar_esta_accion_no, existente.description))
                        .setNegativeButton(texto(R.string.import_review_cancelar), null)
                        .setPositiveButton(texto(R.string.goals_eliminar)) { _, _ ->
                            GamoraTransactionStore.eliminar(existente.id, ::mostrarError)
                        }
                        .show()
                }
            }
        }
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(
            ContextCompat.getColor(context, R.color.gamora_expense)
        )

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val description = descriptionInput.text.toString().trim()
            val cents = centavosDe(amountInput.text.toString())

            if (description.isBlank()) {
                descriptionInput.error = texto(R.string.movement_dialog_escribe_una_descripcion)
                return@setOnClickListener
            }

            // Mismo límite que las reglas de Firestore.
            if (cents == null || cents <= 0L || cents > 100_000_000_000L) {
                amountInput.error = texto(R.string.movement_dialog_introduce_un_importe_valido)
                return@setOnClickListener
            }

            val movimiento = GamoraMovement(
                description = description,
                category = categorias[categorySpinner.selectedItemPosition],
                amountCents = cents,
                isIncome = esIngreso,
                timestamp = fecha.timeInMillis,
                id = existente?.id.orEmpty()
            )

            if (alGuardarLocal != null) {
                alGuardarLocal(movimiento)
                dialog.dismiss()
                return@setOnClickListener
            }

            GamoraTransactionStore.guardar(movimiento, onError = ::mostrarError)

            if (repetirCheck?.isChecked == true) {
                AuthRepository.obtenerUid()?.let { uid ->
                    Recurrentes.crear(uid, movimiento, ::mostrarError)
                }
            }

            Toast.makeText(
                context,
                when {
                    existente != null -> texto(R.string.movement_dialog_cambios_guardados)
                    repetirCheck?.isChecked == true -> texto(R.string.movement_dialog_registrado_se_repetira_cada)
                    esIngreso -> texto(R.string.movement_dialog_ingreso_registrado)
                    else -> texto(R.string.movement_dialog_gasto_registrado)
                },
                Toast.LENGTH_SHORT
            ).show()

            dialog.dismiss()
        }
    }

    dialog.show()
}

/** Para mostrar fechas cortas en listas, p. ej. "14 nov". */
fun fechaCorta(millis: Long): String =
    formatoFecha("dMMM").format(Date(millis)).trimEnd('.')

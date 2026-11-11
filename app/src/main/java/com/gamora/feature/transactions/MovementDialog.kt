package com.gamora.feature.transactions

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

val CATEGORIAS_INGRESO = listOf(
    "Salario",
    "Ventas",
    "Negocio",
    "Inversiones",
    "Regalo",
    "Otros"
)

val CATEGORIAS_GASTO = listOf(
    "Comida",
    "Transporte",
    "Vivienda",
    "Servicios",
    "Salud",
    "Educación",
    "Entretenimiento",
    "Compras",
    "Ahorro",
    "Otros"
)

/** Todas las categorías, sin repetir, para los filtros. */
val CATEGORIAS = (CATEGORIAS_INGRESO + CATEGORIAS_GASTO).distinct()

private val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM yyyy", LOCALE_MX)

/** Abre el formulario para editar [movimiento] (ingreso o gasto según su tipo). */
fun Fragment.editarMovimiento(movimiento: GamoraMovement) =
    mostrarDialogoMovimiento(movimiento.isIncome, movimiento)

/**
 * Diálogo para registrar un ingreso o gasto; lo usan Inicio y Transacciones.
 * Con [existente] edita ese movimiento y permite eliminarlo.
 */
fun Fragment.mostrarDialogoMovimiento(
    esIngreso: Boolean,
    existente: GamoraMovement? = null
) {
    val context = requireContext()
    val categorias = if (esIngreso) CATEGORIAS_INGRESO else CATEGORIAS_GASTO

    val form = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(4), dp(24), 0)
    }

    val descriptionInput = TextInputEditText(context).apply {
        hint = "Descripción (ej. Nómina)"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        maxLines = 1
        filters = arrayOf(InputFilter.LengthFilter(200))
        existente?.let { setText(it.description) }
    }

    val amountInput = TextInputEditText(context).apply {
        hint = "Importe (ej. 250.50)"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        maxLines = 1
        existente?.let { setText(BigDecimal.valueOf(it.amountCents, 2).toPlainString()) }
    }

    // Fecha elegida; al editar se conserva la hora original para no alterar el orden.
    val fecha = Calendar.getInstance().apply {
        existente?.let { timeInMillis = it.timestamp }
    }

    val dateInput = TextInputEditText(context).apply {
        hint = "Fecha"
        isFocusable = false
        isClickable = true
        setText(formatoFecha.format(fecha.time))
        setOnClickListener {
            DatePickerDialog(
                context,
                { _, anio, mes, dia ->
                    fecha.set(anio, mes, dia)
                    setText(formatoFecha.format(fecha.time))
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
            categorias
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
            text = "Categoría"
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
    val repetirCheck = if (existente == null) MaterialCheckBox(context).apply {
        text = "Repetir cada mes (renta, salario, suscripciones…)"
        form.addView(this, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
    } else null

    fun mostrarError(mensaje: String) =
        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()

    val tipo = if (esIngreso) "ingreso" else "gasto"

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (existente == null) "Registrar $tipo" else "Editar $tipo")
        .setView(form)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Guardar", null)
        .apply {
            if (existente != null) {
                setNeutralButton("Eliminar") { _, _ ->
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Eliminar $tipo")
                        .setMessage("¿Eliminar \"${existente.description}\"? Esta acción no se puede deshacer.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar") { _, _ ->
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
                descriptionInput.error = "Escribe una descripción"
                return@setOnClickListener
            }

            // Mismo límite que las reglas de Firestore.
            if (cents == null || cents <= 0L || cents > 100_000_000_000L) {
                amountInput.error = "Introduce un importe válido con hasta 2 decimales"
                return@setOnClickListener
            }

            val movimiento = GamoraMovement(
                description = description,
                category = categorySpinner.selectedItem.toString(),
                amountCents = cents,
                isIncome = esIngreso,
                timestamp = fecha.timeInMillis,
                id = existente?.id.orEmpty()
            )

            GamoraTransactionStore.guardar(movimiento, onError = ::mostrarError)

            if (repetirCheck?.isChecked == true) {
                AuthRepository.obtenerUid()?.let { uid ->
                    Recurrentes.crear(uid, movimiento, ::mostrarError)
                }
            }

            Toast.makeText(
                context,
                when {
                    existente != null -> "Cambios guardados"
                    repetirCheck?.isChecked == true -> "Registrado; se repetirá cada mes"
                    esIngreso -> "Ingreso registrado"
                    else -> "Gasto registrado"
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
    SimpleDateFormat("d MMM", LOCALE_MX).format(Date(millis)).trimEnd('.')

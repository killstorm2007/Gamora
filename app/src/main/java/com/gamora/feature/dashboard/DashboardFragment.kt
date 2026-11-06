
package com.gamora.feature.dashboard

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.gamora.R
import com.gamora.feature.transactions.GamoraMovement
import com.gamora.feature.transactions.GamoraTransactionStore
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val movements = GamoraTransactionStore.movements

    private val currencyFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    private var categoriaSeleccionada: String = "Todas"

    private val categorias = listOf(
        "Todas",
        "Salario",
        "Ventas",
        "Negocio",
        "Inversiones",
        "Regalo",
        "Comida",
        "Transporte",
        "Vivienda",
        "Servicios",
        "Salud",
        "Educación",
        "Entretenimiento",
        "Compras",
        "Otros"
    )

    private val ingresoCategoria = listOf(
        "Salario",
        "Ventas",
        "Negocio",
        "Inversiones",
        "Regalo",
        "Otros"
    )

    private val gastoCategoria = listOf(
        "Comida",
        "Transporte",
        "Vivienda",
        "Servicios",
        "Salud",
        "Educación",
        "Entretenimiento",
        "Compras",
        "Otros"
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnAddIncome)
            .setOnClickListener {
                showMovementDialog(isIncome = true)
            }

        view.findViewById<View>(R.id.btnAddExpense)
            .setOnClickListener {
                showMovementDialog(isIncome = false)
            }

        updateDashboard(view)

        val filtroCategoria = view.findViewById<Spinner>(R.id.spinnerCategoria)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categorias)

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        filtroCategoria.adapter = adapter

        filtroCategoria.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    categoriaSeleccionada = categorias[position]
                    renderMovements(requireView())
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) = Unit
            }
    }

    private fun showMovementDialog(isIncome: Boolean) {
        val context = requireContext()

        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(24)
            setPadding(padding, padding, padding, padding)
        }

        val descriptionInput = EditText(context).apply {
            hint = "Descripción (ej. Nómina)"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
        }

        val amountInput = EditText(context).apply {
            hint = "Importe (ej. 250.50)"
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            maxLines = 1
        }

        val categoryLabel = TextView(context).apply {
            text = "Categoría"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#A7B4C2"))
        }

        val categories = if (isIncome) {
            ingresoCategoria
        } else {
            gastoCategoria
        }

        val categorySpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                categories
            ).also {
                it.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )
            }
        }

        form.addView(descriptionInput)
        form.addView(amountInput)

        form.addView(
            categoryLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )

        form.addView(categorySpinner)

        val dialog = AlertDialog.Builder(context)
            .setTitle(
                if (isIncome) "Registrar ingreso"
                else "Registrar gasto"
            )
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val description =
                        descriptionInput.text.toString().trim()

                    val rawAmount = amountInput.text.toString()
                        .trim()
                        .replace(',', '.')

                    val amount = rawAmount.toBigDecimalOrNull()

                    if (description.isBlank()) {
                        descriptionInput.error =
                            "Escribe una descripción"
                        return@setOnClickListener
                    }

                    if (
                        amount == null ||
                        amount <= BigDecimal.ZERO ||
                        amount.scale() > 2
                    ) {
                        amountInput.error =
                            "Introduce un importe válido con hasta 2 decimales"
                        return@setOnClickListener
                    }

                    val cents = try {
                        amount.movePointRight(2).longValueExact()
                    } catch (_: ArithmeticException) {
                        null
                    }

                    if (cents == null) {
                        amountInput.error = "Importe demasiado grande"
                        return@setOnClickListener
                    }

                    movements.add(
                        GamoraMovement(
                            description = description,
                            category = categorySpinner.selectedItem.toString(),
                            amountCents = cents,
                            isIncome = isIncome
                        )
                    )

                    view?.let { updateDashboard(it) }

                    Toast.makeText(
                        context,
                        if (isIncome) "Ingreso registrado"
                        else "Gasto registrado",
                        Toast.LENGTH_SHORT
                    ).show()

                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    private fun updateDashboard(view: View) {
        val income = movements
            .filter { it.isIncome }
            .sumOf { it.amountCents }

        val expenses = movements
            .filter { !it.isIncome }
            .sumOf { it.amountCents }

        view.findViewById<TextView>(R.id.tvBalance).text =
            formatMoney(income - expenses)

        view.findViewById<TextView>(R.id.tvIncome).text =
            formatMoney(income)

        view.findViewById<TextView>(R.id.tvExpenses).text =
            formatMoney(expenses)

        renderMovements(view)
    }

    private fun renderMovements(view: View) {
        val container = view.findViewById<LinearLayout>(
            R.id.transactionsContainer
        )

        container.removeAllViews()

        if (movements.isEmpty()) {
            val emptyMessage = TextView(requireContext()).apply {
                text =
                    "Tus movementss aparecerán aquí cuando registres ingresos o gastos."
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#A7B4C2"))
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }

            container.addView(emptyMessage)
            return
        }

        movements
            .sortedByDescending { it.timestamp }
            .take(5)
            .forEach { movement ->

                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setBackgroundResource(R.drawable.bg_dashboard_card)

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                }

                val details = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val description = TextView(requireContext()).apply {
                    text = movement.description
                    textSize = 15f
                    setTextColor(
                        android.graphics.Color.parseColor("#F1F5F9")
                    )
                }

                val category = TextView(requireContext()).apply {
                    text = "${movement.category} · " +
                            if (movement.isIncome) "Ingreso" else "Gasto"
                    textSize = 12f
                    setTextColor(
                        android.graphics.Color.parseColor("#A7B4C2")
                    )
                }

                details.addView(description)
                details.addView(category)

                val amount = TextView(requireContext()).apply {
                    val sign = if (movement.isIncome) "+" else "−"
                    text = "$sign${formatMoney(movement.amountCents)}"
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)

                    setTextColor(
                        android.graphics.Color.parseColor(
                            if (movement.isIncome) "#7AE0B0"
                            else "#F87171"
                        )
                    )
                }

                row.addView(
                    details,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                row.addView(amount)
                container.addView(row)
            }
    }

    private fun formatMoney(cents: Long): String {
        return currencyFormat.format(cents / 100.0)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

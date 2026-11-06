
package com.gamora.feature.transactions

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gamora.R
import com.gamora.data.repository.MovementRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private var selectedType = "Todos"
    private var selectedCategory = "Todas las categorías"
    private var searchQuery = ""

    private val categories = listOf(
        "Todas las categorías",
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

    private val currencyFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    private val dateFormat =
        SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        observarMovimientos(view)

        val searchView =
            view.findViewById<SearchView>(R.id.svBusqueda)

        val categorySpinner =
            view.findViewById<Spinner>(R.id.spinnerCategoria)

        val btnTodos = view.findViewById<Button>(R.id.btnTodos)
        val btnGastos = view.findViewById<Button>(R.id.btnGastos)
        val btnIngresos = view.findViewById<Button>(R.id.btnIngresos)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        categorySpinner.adapter = adapter

        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {

                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchQuery = query.orEmpty()
                    aplicarFiltros(view)
                    searchView.clearFocus()
                    return true
                }

                override fun onQueryTextChange(
                    newText: String?
                ): Boolean {
                    searchQuery = newText.orEmpty()
                    aplicarFiltros(view)
                    return true
                }
            }
        )

        btnTodos.setOnClickListener {
            selectedType = "Todos"
            actualizarBotones(btnTodos, btnGastos, btnIngresos)
            aplicarFiltros(view)
        }

        btnGastos.setOnClickListener {
            selectedType = "Gastos"
            actualizarBotones(btnTodos, btnGastos, btnIngresos)
            aplicarFiltros(view)
        }

        btnIngresos.setOnClickListener {
            selectedType = "Ingresos"
            actualizarBotones(btnTodos, btnGastos, btnIngresos)
            aplicarFiltros(view)
        }

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    itemView: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedCategory = categories[position]
                    aplicarFiltros(view)
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }

        actualizarBotones(btnTodos, btnGastos, btnIngresos)
        aplicarFiltros(view)

        view.findViewById<View>(R.id.btnAddIncome)
            .setOnClickListener {
                Toast.makeText(
                    requireContext(),
                    "Por ahora, registra los movimientos desde Inicio.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun actualizarBotones(
        todos: Button,
        gastos: Button,
        ingresos: Button
    ) {
        val buttons = listOf(
            "Todos" to todos,
            "Gastos" to gastos,
            "Ingresos" to ingresos
        )

        buttons.forEach { (type, button) ->
            val selected = selectedType == type

            button.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor(
                    if (selected) "#7AE0B0" else "#243447"
                )
            )

            button.setTextColor(
                Color.parseColor(
                    if (selected) "#101820" else "#F1F5F9"
                )
            )
        }
    }

    private fun aplicarFiltros(view: View) {
        val filteredMovements = MovementRepository.movements.value
            .filter { movement ->
                val matchesSearch =
                    movement.description.contains(
                        searchQuery.trim(),
                        ignoreCase = true
                    )

                val matchesType = when (selectedType) {
                    "Gastos" -> !movement.isIncome
                    "Ingresos" -> movement.isIncome
                    else -> true
                }

                val matchesCategory =
                    selectedCategory == "Todas las categorías" ||
                            movement.category == selectedCategory

                matchesSearch && matchesType && matchesCategory
            }
            .sortedByDescending { it.timestamp }

        renderMovements(view, filteredMovements)
    }

    private fun renderMovements(
        view: View,
        movements: List<GamoraMovement>
    ) {
        val container = view.findViewById<LinearLayout>(
            R.id.transactionsContainer
        )

        container.removeAllViews()

        if (movements.isEmpty()) {
            val emptyMessage = TextView(requireContext()).apply {
                text = "No hay movimientos que coincidan con tu búsqueda."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#A7B4C2"))
                setPadding(32, 40, 32, 40)
            }

            container.addView(emptyMessage)
            return
        }

        var lastDate = ""

        movements.forEach { movement ->
            val currentDate = dateFormat.format(
                Date(movement.timestamp)
            )

            if (currentDate != lastDate) {
                val dateHeader = TextView(requireContext()).apply {
                    text = currentDate
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#A7B4C2"))

                    setPadding(0, 16, 0, 8)
                }

                container.addView(dateHeader)
                lastDate = currentDate
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundResource(R.drawable.bg_dashboard_card)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }

            val icon = TextView(requireContext()).apply {
                text = if (movement.isIncome) "＋" else "−"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(
                    Color.parseColor(
                        if (movement.isIncome) "#7AE0B0"
                        else "#F87171"
                    )
                )
            }

            val details = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 0, 8, 0)
            }

            val description = TextView(requireContext()).apply {
                text = movement.description
                textSize = 15f
                setTextColor(Color.parseColor("#F1F5F9"))
            }

            val category = TextView(requireContext()).apply {
                text = movement.category
                textSize = 12f
                setTextColor(Color.parseColor("#A7B4C2"))
            }

            details.addView(description)
            details.addView(category)

            val amount = TextView(requireContext()).apply {
                val sign = if (movement.isIncome) "+" else "−"
                text = sign + currencyFormat.format(
                    movement.amountCents / 100.0
                )
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)

                setTextColor(
                    Color.parseColor(
                        if (movement.isIncome) "#7AE0B0"
                        else "#F87171"
                    )
                )
            }

            row.addView(icon)

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

    /**
     * Vuelve a cargar los movimientos de Supabase cada vez que la pantalla
     * vuelve a estar visible y se redibuja cuando cambian los datos.
     */
    private fun observarMovimientos(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MovementRepository.movements.collect {
                        aplicarFiltros(view)
                    }
                }

                MovementRepository.refresh().onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

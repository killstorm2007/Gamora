
package com.gamora.feature.transactions

import com.gamora.core.ui.localeApp
import com.gamora.core.ui.formatoFecha
import com.gamora.core.texto
import com.gamora.core.nombre
import com.gamora.core.Categoria
import com.gamora.core.ui.LOCALE_MX
import com.gamora.core.ui.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gamora.R
import com.gamora.feature.importar.ImportadorDocumentos
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    // Se registra al crear el fragment, como exige registerForActivityResult.
    private val importador = ImportadorDocumentos(this)

    private var selectedType = texto(R.string.transactions_todos)
    private var selectedCategory: Categoria? = null
    private var searchQuery = ""

    // null = todas las categorías.
    private val categories: List<Categoria?> = listOf(null) + Categoria.TODAS

    private val dateFormat =
        formatoFecha("ddMMMyyyy")

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

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
            categories.map { it?.nombre(requireContext()) ?: texto(R.string.transactions_todas_las_categorias) }
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
            selectedType = texto(R.string.transactions_todos)
            actualizarBotones(btnTodos, btnGastos, btnIngresos)
            aplicarFiltros(view)
        }

        btnGastos.setOnClickListener {
            selectedType = texto(R.string.transactions_gastos)
            actualizarBotones(btnTodos, btnGastos, btnIngresos)
            aplicarFiltros(view)
        }

        btnIngresos.setOnClickListener {
            selectedType = texto(R.string.transactions_ingresos)
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

        view.findViewById<View>(R.id.btnImportar).setOnClickListener { importador.iniciar() }

        view.findViewById<View>(R.id.btnAddIncome)
            .setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(texto(R.string.transactions_agregar_movimiento_2))
                    .setItems(arrayOf(texto(R.string.transactions_ingreso), texto(R.string.transactions_gasto))) { _, opcion ->
                        mostrarDialogoMovimiento(esIngreso = opcion == 0)
                    }
                    .setNegativeButton(texto(R.string.import_review_cancelar), null)
                    .show()
            }

        observarMovimientos(::aplicarFiltros)
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
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.gamora_accent else R.color.gamora_surface_variant
                )
            )

            button.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.gamora_on_accent else R.color.gamora_text_primary
                )
            )
        }
    }

    private fun aplicarFiltros(view: View) {
        val contexto = requireContext()

        // Busca en descripción y categoría (sin acentos) o en el importe ("450", "450.50").
        val filteredMovements = filtrarMovimientos(
            GamoraTransactionStore.movements, searchQuery, selectedCategory
        ) { it.nombre(contexto) }
            .filter { movement ->
                when (selectedType) {
                    texto(R.string.transactions_gastos) -> !movement.isIncome
                    texto(R.string.transactions_ingresos) -> movement.isIncome
                    else -> true
                }
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
            container.addView(
                crearEstadoVacio(
                    requireContext(),
                    "🔍",
                    if (GamoraTransactionStore.movements.isEmpty()) texto(R.string.transactions_aun_no_registras_movimientos)
                    else texto(R.string.transactions_no_hay_movimientos_que)
                )
            )
            return
        }

        var lastDate = ""

        movements.forEach { movement ->
            val currentDate = dateFormat.format(Date(movement.timestamp))

            if (currentDate != lastDate) {
                container.addView(TextView(requireContext()).apply {
                    text = currentDate.uppercase(localeApp())
                    setTextAppearance(R.style.TextAppearance_Gamora_Overline)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.gamora_text_secondary))
                    setPadding(dp(4), dp(18), 0, dp(10))
                })
                lastDate = currentDate
            }

            container.addView(
                crearFilaMovimiento(requireContext(), movement) { editarMovimiento(movement) }
            )
        }
    }

}

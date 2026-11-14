
package com.gamora.feature.dashboard

import com.gamora.core.texto
import com.gamora.core.nombre
import com.gamora.core.Categoria
import com.gamora.feature.transactions.fechaCorta
import com.gamora.feature.transactions.editarMovimiento
import com.gamora.core.ui.dinero
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
import com.gamora.feature.transactions.observarMovimientos
import com.gamora.feature.transactions.mostrarDialogoMovimiento
import com.gamora.feature.transactions.crearFilaMovimiento
import com.gamora.feature.transactions.crearEstadoVacio
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val movements
        get() = GamoraTransactionStore.movements

    private var categoriaSeleccionada: Categoria? = null

    private val categorias: List<Categoria?> = listOf(null) + Categoria.TODAS

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnAddIncome)
            .setOnClickListener {
                mostrarDialogoMovimiento(esIngreso = true)
            }

        view.findViewById<View>(R.id.btnAddExpense)
            .setOnClickListener {
                mostrarDialogoMovimiento(esIngreso = false)
            }

        observarMovimientos(::updateDashboard)

        val filtroCategoria = view.findViewById<Spinner>(R.id.spinnerCategoria)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categorias.map { it?.nombre(requireContext()) ?: texto(R.string.dashboard_todas) })

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

    private fun updateDashboard(view: View) {
        val income = movements
            .filter { it.isIncome }
            .sumOf { it.amountCents }

        val expenses = movements
            .filter { !it.isIncome }
            .sumOf { it.amountCents }

        view.findViewById<TextView>(R.id.tvBalance).text =
            dinero(income - expenses)

        view.findViewById<TextView>(R.id.tvIncome).text =
            dinero(income)

        view.findViewById<TextView>(R.id.tvExpenses).text =
            dinero(expenses)

        renderMovements(view)
    }

    private fun renderMovements(view: View) {
        val container = view.findViewById<LinearLayout>(
            R.id.transactionsContainer
        )

        container.removeAllViews()

        val visibles = movements
            .filter { categoriaSeleccionada == null || it.category == categoriaSeleccionada }
            .sortedByDescending { it.timestamp }
            .take(5)

        if (visibles.isEmpty()) {
            container.addView(
                crearEstadoVacio(
                    requireContext(),
                    "🧾",
                    if (movements.isEmpty()) texto(R.string.dashboard_tus_movimientos_apareceran_aqui)
                    else texto(R.string.dashboard_no_hay_movimientos_recientes)
                )
            )
            return
        }

        visibles.forEach { movement ->
            container.addView(
                crearFilaMovimiento(requireContext(), movement, fechaCorta(movement.timestamp)) {
                    editarMovimiento(movement)
                }
            )
        }
    }

}

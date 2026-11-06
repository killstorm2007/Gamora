
package com.gamora.feature.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gamora.R
import com.gamora.data.repository.MovementRepository
import com.gamora.feature.transactions.GamoraMovement
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private val currencyFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    private val graphite = Color.parseColor("#A7B4C2")
    private val muted = Color.parseColor("#68716B")
    private val sage = Color.parseColor("#527A65")
    private val red = Color.parseColor("#B54747")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observarMovimientos(view)
    }

    /**
     * Recalcula las analíticas cuando cambian los movimientos en Supabase.
     */
    private fun observarMovimientos(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MovementRepository.movements.collect {
                        actualizarAnaliticas(view)
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

    private fun actualizarAnaliticas(view: View) {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)

        val movementsThisMonth = MovementRepository.movements.value.filter { movement ->
            val date = Calendar.getInstance().apply {
                timeInMillis = movement.timestamp
            }

            date.get(Calendar.MONTH) == currentMonth &&
                    date.get(Calendar.YEAR) == currentYear
        }

        val income = movementsThisMonth
            .filter { it.isIncome }
            .sumOf { it.amountCents }

        val expenses = movementsThisMonth
            .filterNot { it.isIncome }
            .sumOf { it.amountCents }

        val balance = income - expenses

        view.findViewById<TextView>(R.id.txtAnalyticsIncome).text =
            formatMoney(income)

        view.findViewById<TextView>(R.id.txtAnalyticsExpenses).text =
            formatMoney(expenses)

        view.findViewById<TextView>(R.id.txtAnalyticsBalance).apply {
            text = formatMoney(balance)
            setTextColor(if (balance < 0L) red else sage)
        }

        val expenseMovements = movementsThisMonth.filterNot { it.isIncome }

        val categoryContainer =
            view.findViewById<LinearLayout>(R.id.layoutExpenseCategories)

        val emptyMessage =
            view.findViewById<TextView>(R.id.txtEmptyAnalytics)

        renderCategories(
            container = categoryContainer,
            emptyMessage = emptyMessage,
            expenses = expenseMovements
        )
    }


    private fun renderCategories(
        container: LinearLayout,
        emptyMessage: TextView,
        expenses: List<GamoraMovement>
    ) {
        // El mensaje vacío permanece dentro del contenedor.
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child.id != R.id.txtEmptyAnalytics) {
                container.removeViewAt(i)
            }
        }

        if (expenses.isEmpty()) {
            emptyMessage.visibility = View.VISIBLE
            return
        }

        emptyMessage.visibility = View.GONE

        val totals = expenses
            .groupBy { it.category }
            .mapValues { (_, movements) ->
                movements.sumOf { it.amountCents }
            }
            .toList()
            .sortedByDescending { it.second }

        totals.forEach { (category, amount) ->
            val totalExpenses = expenses.sumOf { it.amountCents }

            val percentage = if (totalExpenses > 0L) {
                (amount.toDouble() / totalExpenses * 100).toInt()
            } else {
                0
            }

            val item = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }

            val header = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val categoryName = TextView(requireContext()).apply {
                text = category
                textSize = 14f
                setTextColor(graphite)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val categoryAmount = TextView(requireContext()).apply {
                text = formatMoney(amount)
                textSize = 14f
                setTextColor(graphite)
            }

            header.addView(categoryName)
            header.addView(categoryAmount)
            item.addView(header)

            val details = TextView(requireContext()).apply {
                text = "$percentage% de tus gastos del mes"
                textSize = 12f
                setTextColor(muted)
                setPadding(0, dp(3), 0, dp(6))
            }

            item.addView(details)

            val progress = ProgressBar(
                requireContext(),
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                this.progress = percentage
                progressTintList =
                    android.content.res.ColorStateList.valueOf(sage)
                progressBackgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#A7B4C2")
                    )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(6)
                )
            }

            item.addView(progress)
            container.addView(item)
        }
    }


    private fun formatMoney(amountCents: Long): String {
        return currencyFormat.format(amountCents / 100.0)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

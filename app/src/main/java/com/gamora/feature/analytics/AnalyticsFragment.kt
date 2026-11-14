package com.gamora.feature.analytics

import com.gamora.core.ui.LOCALE_MX
import com.gamora.core.ui.dp
import com.gamora.core.ui.dinero
import com.gamora.core.ui.centavosDe
import com.gamora.core.ui.enCampo
import com.gamora.core.notifications.inicioPeriodo
import com.gamora.feature.budget.PresupuestoStore
import com.gamora.feature.budget.avancePresupuestos
import com.gamora.feature.transactions.CATEGORIAS_GASTO
import com.gamora.feature.transactions.iconoCategoria
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import android.text.InputType
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.math.BigDecimal
import java.util.Date
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gamora.R
import com.gamora.feature.transactions.GamoraTransactionStore
import com.gamora.feature.transactions.observarMovimientos
import com.gamora.feature.transactions.observar
import com.gamora.feature.goals.GamoraGoalStore
import com.gamora.core.auth.AuthRepository
import com.gamora.feature.transactions.totalesPorMes
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private val graphite get() = color(R.color.gamora_text_primary)
    private val muted get() = color(R.color.gamora_text_secondary)
    private val track get() = color(R.color.gamora_surface_variant)

    private var presupuestoCentavos = 0L
    private var diaInicioPeriodo = 1

    // Un color por categoría, en orden de mayor a menor gasto.
    private val paleta = listOf(
        "#527A65", "#C65D57", "#D4A24C", "#4F7CAC",
        "#8E6C9E", "#3E9A94", "#E07B39", "#8A948E"
    ).map(Color::parseColor)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observarMovimientos(::actualizarAnaliticas)
        observar(GamoraGoalStore.goalsFlow, ::actualizarAnaliticas)
        observar(PresupuestoStore.flow, ::actualizarAnaliticas)

        view.findViewById<View>(R.id.btnEditarPresupuestos).setOnClickListener {
            mostrarDialogoPresupuestos()
        }

        AuthRepository.obtenerPreferenciasFinancieras(
            onSuccess = { preferencias ->
                presupuestoCentavos = preferencias.presupuestoMensualCentavos
                diaInicioPeriodo = preferencias.diaInicioPeriodo
                if (isAdded) this.view?.let(::actualizarAnaliticas)
            },
            onError = {}
        )
    }

    private fun actualizarAnaliticas(view: View) {
        val movimientos = GamoraTransactionStore.movements

        val income = movimientos
            .filter { it.isIncome }
            .sumOf { it.amountCents }

        val expenses = movimientos
            .filterNot { it.isIncome }
            .sumOf { it.amountCents }

        val balance = income - expenses

        view.findViewById<TextView>(R.id.txtAnalyticsIncome).text =
            dinero(income)

        view.findViewById<TextView>(R.id.txtAnalyticsExpenses).text =
            dinero(expenses)

        view.findViewById<TextView>(R.id.txtAnalyticsBalance).apply {
            text = dinero(balance)
        }

        val totalesCategoria = movimientos
            .filterNot { it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, delaCategoria) -> delaCategoria.sumOf { it.amountCents } }
            .toList()
            .sortedByDescending { it.second }

        renderCategories(view, totalesCategoria, expenses)
        renderPresupuestos(view)
        renderMeses(view)

        view.findViewById<TextView>(R.id.txtConsejos).text =
            generarConsejos(movimientos, GamoraGoalStore.goals, presupuestoCentavos)
                .joinToString("\n\n") { "• $it" }
    }

    private fun renderCategories(
        view: View,
        totals: List<Pair<String, Long>>,
        totalExpenses: Long
    ) {
        val container = view.findViewById<LinearLayout>(R.id.layoutExpenseCategories)
        val emptyMessage = view.findViewById<TextView>(R.id.txtEmptyAnalytics)
        val dona = view.findViewById<DonaView>(R.id.graficaGastos)

        // El mensaje vacío permanece dentro del contenedor.
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child.id != R.id.txtEmptyAnalytics) {
                container.removeViewAt(i)
            }
        }

        if (totals.isEmpty()) {
            emptyMessage.visibility = View.VISIBLE
            dona.visibility = View.GONE
            return
        }

        emptyMessage.visibility = View.GONE
        dona.visibility = View.VISIBLE

        val colores = totals.indices.map { paleta[it % paleta.size] }

        dona.mostrar(
            totals.map { it.second }.zip(colores),
            titulo = "Gastos totales",
            valor = dinero(totalExpenses)
        )

        dona.contentDescription = "Gastos por categoría: " +
                totals.joinToString { (categoria, monto) ->
                    "$categoria ${porcentaje(monto, totalExpenses)}%"
                }

        totals.forEachIndexed { indice, (category, amount) ->
            val percentage = porcentaje(amount, totalExpenses)
            val color = colores[indice]

            val item = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }

            val header = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val categoryName = TextView(requireContext()).apply {
                text = android.text.SpannableString("●  $category").apply {
                    setSpan(
                        android.text.style.ForegroundColorSpan(color),
                        0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                textSize = 14f
                setTextColor(graphite)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val categoryAmount = TextView(requireContext()).apply {
                text = dinero(amount)
                textSize = 14f
                setTextColor(graphite)
            }

            header.addView(categoryName)
            header.addView(categoryAmount)
            item.addView(header)

            val details = TextView(requireContext()).apply {
                text = "$percentage% de tus gastos"
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
                progressTintList = ColorStateList.valueOf(color)
                progressBackgroundTintList = ColorStateList.valueOf(track)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(6)
                )
            }

            item.addView(progress)
            container.addView(item)
        }
    }

    private fun renderPresupuestos(view: View) {
        val contenedor = view.findViewById<LinearLayout>(R.id.layoutPresupuestos)
        contenedor.removeAllViews()

        val inicio = inicioPeriodo(System.currentTimeMillis(), diaInicioPeriodo)
        view.findViewById<TextView>(R.id.txtPeriodoPresupuesto).text =
            "Desde el ${SimpleDateFormat("d 'de' MMMM", LOCALE_MX).format(Date(inicio))}"

        val avances = avancePresupuestos(GamoraTransactionStore.movements, PresupuestoStore.limites, inicio)

        if (avances.isEmpty()) {
            contenedor.addView(TextView(requireContext()).apply {
                text = "🎯\n\nDefine cuánto quieres gastar al mes en cada categoría y Gamora te avisará antes de que te pases."
                gravity = Gravity.CENTER
                setTextAppearance(R.style.TextAppearance_Gamora_Body)
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { mostrarDialogoPresupuestos() }
            })
            return
        }

        avances.forEachIndexed { indice, avance ->
            val colorBarra = color(
                when {
                    avance.porcentaje >= 100 -> R.color.gamora_expense
                    avance.porcentaje >= 80 -> R.color.gamora_warning
                    else -> R.color.gamora_accent
                }
            )

            val fila = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, if (indice == 0) 0 else dp(16), 0, 0)
            }

            fila.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(requireContext()).apply {
                    text = "${iconoCategoria(avance.categoria)}  ${avance.categoria}"
                    textSize = 14f
                    setTextColor(graphite)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(requireContext()).apply {
                    text = "${dinero(avance.gastadoCentavos)} / ${dinero(avance.limiteCentavos)}"
                    textSize = 13f
                    setTextColor(graphite)
                })
            })

            fila.addView(LinearProgressIndicator(requireContext()).apply {
                max = 100
                progress = avance.porcentaje.coerceAtMost(100)
                trackCornerRadius = dp(4)
                trackThickness = dp(8)
                setIndicatorColor(colorBarra)
                trackColor = track
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            })

            fila.addView(TextView(requireContext()).apply {
                text = if (avance.restanteCentavos >= 0) {
                    "${avance.porcentaje}% usado · te quedan ${dinero(avance.restanteCentavos)}"
                } else {
                    "Te pasaste por ${dinero(-avance.restanteCentavos)}"
                }
                textSize = 12f
                setTextColor(if (avance.restanteCentavos >= 0) muted else colorBarra)
                setPadding(0, dp(6), 0, 0)
            })

            contenedor.addView(fila)
        }
    }

    /** Un campo por categoría de gasto; vacío o 0 quita el límite. */
    private fun mostrarDialogoPresupuestos() {
        val contexto = requireContext()
        val categorias = CATEGORIAS_GASTO.filter { it != "Ahorro" }

        val contenedor = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(8))
        }

        val campos = categorias.associateWith { categoria ->
            TextInputEditText(contexto).apply {
                hint = "${iconoCategoria(categoria)}  $categoria"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                maxLines = 1
                PresupuestoStore.limites[categoria]?.let {
                    setText(BigDecimal.valueOf(it, 2).toPlainString())
                }
                contenedor.addView(enCampo())
            }
        }

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle("Presupuesto por categoría")
            .setMessage("Límite mensual para cada categoría. Déjalo vacío si no quieres límite.")
            .setView(ScrollView(contexto).apply { addView(contenedor) })
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()

        dialogo.setOnShowListener {
            val guardar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)

            guardar.setOnClickListener {
                val limites = mutableMapOf<String, Long>()

                for ((categoria, campo) in campos) {
                    val texto = campo.text?.toString().orEmpty()
                    if (texto.isBlank()) continue

                    val centavos = centavosDe(texto)
                    if (centavos == null || centavos > 100_000_000_000L) {
                        campo.error = "Importe no válido"
                        return@setOnClickListener
                    }
                    if (centavos > 0) limites[categoria] = centavos
                }

                guardar.isEnabled = false

                PresupuestoStore.guardar(
                    limites,
                    onSuccess = {
                        dialogo.dismiss()
                        if (isAdded) Toast.makeText(contexto, "Presupuestos guardados", Toast.LENGTH_SHORT).show()
                    },
                    onError = { mensaje ->
                        guardar.isEnabled = true
                        if (isAdded) Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        dialogo.show()
    }

    /** Ingresos y gastos de los últimos 6 meses, incluido el actual (meses sin movimientos en cero). */
    private fun renderMeses(view: View) {
        val porMes = GamoraTransactionStore.movements.totalesPorMes()
        val formatoMes = SimpleDateFormat("MMM", LOCALE_MX)

        val meses = (5 downTo 0).map { atras ->
            val calendario = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -atras)
            }

            val clave = calendario.get(Calendar.YEAR) * 12 + calendario.get(Calendar.MONTH)
            val totales = porMes[clave]

            BarrasMensualesView.Mes(
                etiqueta = formatoMes.format(calendario.time).trimEnd('.'),
                ingresos = totales?.ingresos ?: 0L,
                gastos = totales?.gastos ?: 0L
            )
        }

        view.findViewById<BarrasMensualesView>(R.id.graficaMeses).apply {
            mostrar(meses)
            contentDescription = "Ingresos y gastos de los últimos 6 meses: " +
                    meses.joinToString { mes ->
                        "${mes.etiqueta}, ingresos ${dinero(mes.ingresos)}, gastos ${dinero(mes.gastos)}"
                    }
        }
    }

    private fun porcentaje(parte: Long, total: Long): Int =
        if (total > 0L) (parte.toDouble() / total * 100).toInt() else 0

}

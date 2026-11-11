package com.gamora.feature.chatbot

import com.gamora.core.ui.LOCALE_MX
import com.gamora.core.ui.dinero
import com.gamora.feature.analytics.generarConsejos
import com.gamora.feature.goals.SavingsGoal
import com.gamora.feature.transactions.GamoraMovement
import com.gamora.feature.transactions.ahorroAcumulado
import com.gamora.feature.transactions.totalesPorMes
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Resumen de las finanzas de la persona para el asistente.
 * Se manda en cada mensaje para que Gemini siempre vea los datos al día.
 */
fun construirContextoFinanciero(
    movimientos: List<GamoraMovement>,
    metas: List<SavingsGoal>,
    presupuestoCentavos: Long,
    ahora: Calendar = Calendar.getInstance()
): String = buildString {

    val fechaHoy = SimpleDateFormat("d 'de' MMMM 'de' yyyy", LOCALE_MX).format(ahora.time)
    appendLine("Fecha de hoy: $fechaHoy")

    if (movimientos.isEmpty()) {
        appendLine("- Aún no ha registrado movimientos.")
    } else {
        val ingresos = movimientos.filter { it.isIncome }.sumOf { it.amountCents }
        val gastos = movimientos.filterNot { it.isIncome }.sumOf { it.amountCents }

        appendLine("- Balance general: ${dinero(ingresos - gastos)} (ingresos ${dinero(ingresos)}, gastos ${dinero(gastos)})")
        appendLine("- Ahorro acumulado (lo que sobró cada mes): ${dinero(movimientos.ahorroAcumulado())}")

        // Últimos 3 meses, incluido el actual.
        val porMes = movimientos.totalesPorMes()
        val formatoMes = SimpleDateFormat("MMMM yyyy", LOCALE_MX)

        appendLine("- Por mes:")
        for (atras in 2 downTo 0) {
            val mes = (ahora.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -atras)
            }
            val totales = porMes[mes.get(Calendar.YEAR) * 12 + mes.get(Calendar.MONTH)]

            appendLine(
                "  • ${formatoMes.format(mes.time)}: ingresos ${dinero(totales?.ingresos ?: 0)}, " +
                        "gastos ${dinero(totales?.gastos ?: 0)}"
            )
        }

        // Gastos por categoría del mes en curso.
        val fecha = Calendar.getInstance()
        val gastosDelMes = movimientos.filter {
            fecha.timeInMillis = it.timestamp
            !it.isIncome &&
                    fecha.get(Calendar.YEAR) == ahora.get(Calendar.YEAR) &&
                    fecha.get(Calendar.MONTH) == ahora.get(Calendar.MONTH)
        }

        gastosDelMes.groupBy { it.category }
            .mapValues { (_, lista) -> lista.sumOf { it.amountCents } }
            .entries
            .sortedByDescending { it.value }
            .takeIf { it.isNotEmpty() }
            ?.let { categorias ->
                appendLine("- Gastos de este mes por categoría: " +
                        categorias.joinToString { "${it.key} ${dinero(it.value)}" })
            }

        // Movimientos recientes, para que pueda citar casos concretos.
        val formatoDia = SimpleDateFormat("d MMM", LOCALE_MX)
        appendLine("- Últimos movimientos:")
        movimientos.sortedByDescending { it.timestamp }.take(25).forEach {
            appendLine(
                "  • ${formatoDia.format(Date(it.timestamp))} · " +
                        "${if (it.isIncome) "ingreso" else "gasto"} · ${it.category} · " +
                        "\"${it.description}\" · ${dinero(it.amountCents)}"
            )
        }
    }

    appendLine(
        if (presupuestoCentavos > 0) "- Presupuesto mensual: ${dinero(presupuestoCentavos)}"
        else "- No ha definido presupuesto mensual."
    )

    if (metas.isEmpty()) {
        appendLine("- Sin metas de ahorro.")
    } else {
        val formatoFecha = SimpleDateFormat("d/MM/yyyy", LOCALE_MX)
        appendLine("- Metas de ahorro:")
        metas.forEach { meta ->
            val limite = meta.deadlineMillis?.let { ", fecha límite ${formatoFecha.format(Date(it))}" }.orEmpty()
            appendLine("  • \"${meta.name}\": ${dinero(meta.savedCents)} de ${dinero(meta.targetCents)}$limite")
        }
    }

    val consejos = generarConsejos(movimientos, metas, presupuestoCentavos, ahora)
    if (movimientos.isNotEmpty() && consejos.isNotEmpty()) {
        appendLine("- Observaciones automáticas de la app:")
        consejos.forEach { appendLine("  • $it") }
    }
}

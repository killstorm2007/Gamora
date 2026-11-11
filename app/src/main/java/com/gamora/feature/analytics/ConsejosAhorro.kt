package com.gamora.feature.analytics

import com.gamora.feature.transactions.esAhorro
import com.gamora.core.ui.dinero
import com.gamora.feature.goals.SavingsGoal
import com.gamora.feature.transactions.GamoraMovement
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Consejos basados en los movimientos del mes en curso, las metas y el presupuesto.
 * [presupuestoCentavos] = 0 si la persona no lo ha configurado.
 */
fun generarConsejos(
    movimientos: List<GamoraMovement>,
    metas: List<SavingsGoal>,
    presupuestoCentavos: Long,
    ahora: Calendar = Calendar.getInstance()
): List<String> {

    if (movimientos.isEmpty()) {
        return listOf("Registra tus ingresos y gastos para que Gamora pueda darte consejos basados en tus datos.")
    }

    val consejos = mutableListOf<String>()
    val fecha = Calendar.getInstance()

    val delMes = movimientos.filter {
        fecha.timeInMillis = it.timestamp
        fecha.get(Calendar.YEAR) == ahora.get(Calendar.YEAR) &&
                fecha.get(Calendar.MONTH) == ahora.get(Calendar.MONTH)
    }

    val ingresos = delMes.filter { it.isIncome }.sumOf { it.amountCents }
    // Lo apartado en "Ahorro" no es consumo: no cuenta para la tasa de ahorro ni para recortes.
    val gastos = delMes.filterNot { it.isIncome || it.esAhorro }.sumOf { it.amountCents }

    // 1. Balance del mes y tasa de ahorro.
    when {
        gastos > ingresos ->
            consejos += "Este mes llevas ${dinero(gastos - ingresos)} más de gastos que de ingresos. " +
                    "Revisa qué gastos puedes posponer."

        ingresos > 0 -> {
            val tasa = (ingresos - gastos) * 100 / ingresos

            consejos += if (tasa >= 20) {
                "¡Vas muy bien! Este mes conservas el $tasa% de tus ingresos."
            } else {
                "Este mes conservas el $tasa% de tus ingresos. Intenta apartar al menos el 20% en cuanto recibas tu ingreso."
            }
        }
    }

    // 2. Categoría con mayor gasto.
    val porCategoria = delMes.filterNot { it.isIncome || it.esAhorro }
        .groupBy { it.category }
        .mapValues { (_, lista) -> lista.sumOf { it.amountCents } }

    porCategoria.maxByOrNull { it.value }?.let { (categoria, monto) ->
        val porcentaje = monto * 100 / gastos

        if (porcentaje >= 30) {
            consejos += "Tu mayor gasto del mes es $categoria ($porcentaje%). " +
                    "Reducirlo un 10% te ahorraría ${dinero(monto / 10)} al mes."
        }
    }

    // 3. Gastos prescindibles.
    val prescindibles = (porCategoria["Entretenimiento"] ?: 0L) + (porCategoria["Compras"] ?: 0L)

    if (gastos > 0 && prescindibles * 100 / gastos >= 25) {
        consejos += "Entretenimiento y compras suman el ${prescindibles * 100 / gastos}% de tus gastos. " +
                "Espera 24 horas antes de compras no planeadas."
    }

    // 4. Presupuesto mensual.
    if (presupuestoCentavos > 0) {
        val uso = gastos * 100 / presupuestoCentavos

        when {
            uso >= 100 -> consejos += "Superaste tu presupuesto mensual por ${dinero(gastos - presupuestoCentavos)}."
            uso >= 80 -> consejos += "Ya usaste el $uso% de tu presupuesto mensual. Te quedan ${dinero(presupuestoCentavos - gastos)}."
        }
    } else {
        consejos += "Define un presupuesto mensual en Perfil › Preferencias financieras para recibir alertas."
    }

    // 5. Cuánto apartar al mes para cada meta con fecha límite.
    metas.filter { it.activa && it.deadlineMillis != null }.forEach { meta ->
        fecha.timeInMillis = meta.deadlineMillis!!

        val mesesRestantes = (fecha.get(Calendar.YEAR) - ahora.get(Calendar.YEAR)) * 12 +
                (fecha.get(Calendar.MONTH) - ahora.get(Calendar.MONTH)) + 1
        val faltante = meta.targetCents - meta.savedCents

        consejos += if (mesesRestantes <= 0) {
            "La fecha de tu meta \"${meta.name}\" ya pasó y te faltan ${dinero(faltante)}. Ajusta la fecha o el monto."
        } else {
            "Para lograr \"${meta.name}\" a tiempo, aparta ${dinero(faltante / mesesRestantes)} al mes."
        }
    }

    return consejos
}

package com.gamora.feature.analytics

import com.gamora.R
import com.gamora.core.texto
import com.gamora.core.Categoria
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
    ahora: Calendar = Calendar.getInstance(),
    nombre: (Categoria) -> String
): List<String> {

    if (movimientos.isEmpty()) {
        return listOf(texto(R.string.consejos_ahorro_registra_tus_ingresos_y))
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
            consejos += texto(R.string.consejos_ahorro_este_mes_llevas_mas, dinero(gastos - ingresos))

        ingresos > 0 -> {
            val tasa = (ingresos - gastos) * 100 / ingresos

            consejos += if (tasa >= 20) {
                texto(R.string.consejos_ahorro_vas_muy_bien_este, tasa)
            } else {
                texto(R.string.consejos_ahorro_este_mes_conservas_el, tasa)
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
            consejos += texto(R.string.consejos_ahorro_tu_mayor_gasto_del, nombre(categoria), porcentaje, dinero(monto / 10))
        }
    }

    // 3. Gastos prescindibles.
    val prescindibles = (porCategoria[Categoria.ENTRETENIMIENTO] ?: 0L) + (porCategoria[Categoria.COMPRAS] ?: 0L)

    if (gastos > 0 && prescindibles * 100 / gastos >= 25) {
        consejos += texto(R.string.consejos_ahorro_entretenimiento_y_compras_suman, prescindibles * 100 / gastos)
    }

    // 4. Presupuesto mensual.
    if (presupuestoCentavos > 0) {
        val uso = gastos * 100 / presupuestoCentavos

        when {
            uso >= 100 -> consejos += texto(R.string.consejos_ahorro_superaste_tu_presupuesto_mensual, dinero(gastos - presupuestoCentavos))
            uso >= 80 -> consejos += texto(R.string.consejos_ahorro_ya_usaste_el_de, uso, dinero(presupuestoCentavos - gastos))
        }
    } else {
        consejos += texto(R.string.consejos_ahorro_define_un_presupuesto_mensual)
    }

    // 5. Cuánto apartar al mes para cada meta con fecha límite.
    metas.filter { it.activa && it.deadlineMillis != null }.forEach { meta ->
        fecha.timeInMillis = meta.deadlineMillis!!

        val mesesRestantes = (fecha.get(Calendar.YEAR) - ahora.get(Calendar.YEAR)) * 12 +
                (fecha.get(Calendar.MONTH) - ahora.get(Calendar.MONTH)) + 1
        val faltante = meta.targetCents - meta.savedCents

        consejos += if (mesesRestantes <= 0) {
            texto(R.string.consejos_ahorro_la_fecha_de_tu, meta.name, dinero(faltante))
        } else {
            texto(R.string.consejos_ahorro_para_lograr_a_tiempo, meta.name, dinero(faltante / mesesRestantes))
        }
    }

    return consejos
}

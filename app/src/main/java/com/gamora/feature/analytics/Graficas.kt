package com.gamora.feature.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.gamora.R

private fun View.sp(valor: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valor, resources.displayMetrics)

/** Gráfica de dona: cada porción es (monto, color). */
class DonaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var porciones: List<Pair<Long, Int>> = emptyList()
    private var titulo = ""
    private var valor = ""
    private val area = RectF()

    private val pincelArco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val pincelValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sp(18f)
        color = ContextCompat.getColor(context, R.color.gamora_text_primary)
    }

    private val pincelTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        color = ContextCompat.getColor(context, R.color.gamora_text_secondary)
    }

    fun mostrar(porciones: List<Pair<Long, Int>>, titulo: String, valor: String) {
        this.porciones = porciones
        this.titulo = titulo
        this.valor = valor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val total = porciones.sumOf { it.first }
        if (total <= 0L) return

        val lado = minOf(width, height).toFloat()
        val grosor = lado * 0.16f
        pincelArco.strokeWidth = grosor

        val izquierda = (width - lado) / 2 + grosor / 2
        val arriba = (height - lado) / 2 + grosor / 2
        area.set(izquierda, arriba, izquierda + lado - grosor, arriba + lado - grosor)

        var inicio = -90f
        porciones.forEach { (monto, color) ->
            val barrido = monto.toFloat() / total * 360f
            pincelArco.color = color
            canvas.drawArc(area, inicio, barrido, false, pincelArco)
            inicio += barrido
        }

        canvas.drawText(valor, width / 2f, height / 2f + pincelValor.textSize / 3, pincelValor)
        canvas.drawText(titulo, width / 2f, height / 2f - pincelValor.textSize, pincelTitulo)
    }
}

/** Barras de ingresos y gastos por mes. */
class BarrasMensualesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Mes(val etiqueta: String, val ingresos: Long, val gastos: Long)

    private var meses: List<Mes> = emptyList()
    private val barra = RectF()

    private val pincelIngreso = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gamora_income)
    }

    private val pincelGasto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gamora_expense)
    }

    private val pincelEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
        color = ContextCompat.getColor(context, R.color.gamora_text_secondary)
    }

    fun mostrar(meses: List<Mes>) {
        this.meses = meses
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (meses.isEmpty()) return

        val maximo = meses.maxOf { maxOf(it.ingresos, it.gastos) }.coerceAtLeast(1L)
        val altoEtiqueta = pincelEtiqueta.textSize * 1.8f
        val altoBarras = height - altoEtiqueta
        val anchoGrupo = width.toFloat() / meses.size
        val anchoBarra = anchoGrupo * 0.28f
        val radio = anchoBarra / 4

        meses.forEachIndexed { indice, mes ->
            val centro = anchoGrupo * indice + anchoGrupo / 2

            listOf(
                Triple(mes.ingresos, centro - anchoBarra - 2f, pincelIngreso),
                Triple(mes.gastos, centro + 2f, pincelGasto)
            ).forEach { (monto, x, pincel) ->
                val alto = monto.toFloat() / maximo * altoBarras
                barra.set(x, altoBarras - alto, x + anchoBarra, altoBarras)
                canvas.drawRoundRect(barra, radio, radio, pincel)
            }

            canvas.drawText(mes.etiqueta, centro, height - pincelEtiqueta.textSize / 2, pincelEtiqueta)
        }
    }
}

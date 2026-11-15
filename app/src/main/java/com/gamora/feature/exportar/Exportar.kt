package com.gamora.feature.exportar

import com.gamora.core.security.SalidaBreve
import com.gamora.core.ui.IconoDialogo
import com.gamora.core.ui.dialogo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.gamora.R
import com.gamora.core.Categoria
import com.gamora.core.nombre
import com.gamora.core.texto
import com.gamora.core.ui.Moneda
import com.gamora.core.ui.dinero
import com.gamora.core.ui.formatoFecha
import com.gamora.feature.transactions.GamoraMovement
import com.gamora.feature.transactions.GamoraTransactionStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Rango de fechas que se exporta. */
enum class Periodo(@StringRes val etiqueta: Int) {
    ESTE_MES(R.string.exportar_este_mes),
    TRES_MESES(R.string.exportar_tres_meses),
    ESTE_ANIO(R.string.exportar_este_anio),
    TODO(R.string.exportar_todo)
}

/** Movimientos del periodo (hasta [hoy]), del más antiguo al más reciente. */
fun List<GamoraMovement>.enPeriodo(periodo: Periodo, hoy: Long = System.currentTimeMillis()): List<GamoraMovement> {
    val desde = Calendar.getInstance().apply {
        timeInMillis = hoy
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_MONTH, 1)
        when (periodo) {
            Periodo.ESTE_MES -> Unit
            Periodo.TRES_MESES -> add(Calendar.MONTH, -2)
            Periodo.ESTE_ANIO -> set(Calendar.MONTH, Calendar.JANUARY)
            Periodo.TODO -> timeInMillis = Long.MIN_VALUE
        }
    }.timeInMillis
    return filter { it.timestamp >= desde }.sortedBy { it.timestamp }
}

private fun campoCsv(valor: String): String =
    if (valor.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + valor.replace("\"", "\"\"") + "\"" else valor

/**
 * CSV (RFC 4180) que Excel y Google Sheets abren bien: con BOM para los acentos, fecha ISO, gastos
 * en negativo y punto decimal. Los textos de encabezado y tipo vienen traducidos.
 */
fun csvDeMovimientos(
    movimientos: List<GamoraMovement>,
    moneda: String,
    nombre: (Categoria) -> String,
    encabezados: List<String>,
    ingreso: String,
    gasto: String
): String = buildString {
    append('\uFEFF')
    append(encabezados.joinToString(",") { campoCsv(it) }).append("\r\n")
    val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    movimientos.sortedBy { it.timestamp }.forEach { m ->
        val monto = BigDecimal.valueOf(if (m.isIncome) m.amountCents else -m.amountCents, 2).toPlainString()
        append(
            listOf(
                fecha.format(Date(m.timestamp)),
                if (m.isIncome) ingreso else gasto,
                nombre(m.category),
                m.description,
                monto,
                moneda
            ).joinToString(",") { campoCsv(it) }
        ).append("\r\n")
    }
}

/** PDF tamaño carta con resumen del periodo, gasto por categoría y la lista de movimientos. */
fun pdfDeMovimientos(context: Context, movimientos: List<GamoraMovement>, periodo: String): ByteArray {
    val ancho = 612
    val alto = 792
    val margen = 48f
    val documento = PdfDocument()

    val titulo = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    val subtitulo = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    val normal = Paint().apply { textSize = 10f; isAntiAlias = true }
    val gris = Paint(normal).apply { color = 0xFF6B7A72.toInt() }
    val derecha = Paint(normal).apply { textAlign = Paint.Align.RIGHT }
    val linea = Paint().apply { color = 0xFFD5DDD8.toInt(); strokeWidth = 0.6f }

    var numero = 0
    var pagina: PdfDocument.Page? = null
    var y = 0f

    fun nuevaPagina() {
        pagina?.let(documento::finishPage)
        numero++
        pagina = documento.startPage(PdfDocument.PageInfo.Builder(ancho, alto, numero).create())
        y = margen
        pagina!!.canvas.drawText(texto(R.string.exportar_pie, numero), ancho - margen, alto - 24f, Paint(gris).apply { textAlign = Paint.Align.RIGHT })
    }

    fun espacio(necesario: Float) {
        if (y + necesario > alto - margen - 16f) nuevaPagina()
    }

    fun recortar(textoLargo: String, paint: Paint, maximo: Float): String {
        if (paint.measureText(textoLargo) <= maximo) return textoLargo
        var corte = textoLargo
        while (corte.isNotEmpty() && paint.measureText("$corte…") > maximo) corte = corte.dropLast(1)
        return "$corte…"
    }

    nuevaPagina()
    val lienzo = { pagina!!.canvas }

    lienzo().drawText(texto(R.string.exportar_titulo_pdf), margen, y + 20f, titulo); y += 34f
    lienzo().drawText(periodo, margen, y, gris); y += 26f

    val ingresos = movimientos.filter { it.isIncome }.sumOf { it.amountCents }
    val gastos = movimientos.filterNot { it.isIncome }.sumOf { it.amountCents }
    listOf(
        texto(R.string.analytics_ingresos_2).removePrefix("● ") to dinero(ingresos),
        texto(R.string.analytics_gastos_2).removePrefix("● ") to dinero(gastos),
        texto(R.string.analytics_balance_general) to dinero(ingresos - gastos)
    ).forEach { (etiqueta, valor) ->
        lienzo().drawText(etiqueta, margen, y, normal)
        lienzo().drawText(valor, margen + 220f, y, Paint(derecha).apply { typeface = Typeface.DEFAULT_BOLD })
        y += 16f
    }
    y += 14f

    val porCategoria = movimientos.filterNot { it.isIncome }.groupBy { it.category }
        .mapValues { (_, lista) -> lista.sumOf { it.amountCents } }.entries.sortedByDescending { it.value }
    if (porCategoria.isNotEmpty()) {
        espacio(40f)
        lienzo().drawText(texto(R.string.analytics_a_donde_va_tu), margen, y, subtitulo); y += 18f
        porCategoria.forEach { (categoria, monto) ->
            espacio(16f)
            lienzo().drawText(categoria.nombre(context), margen, y, normal)
            lienzo().drawText(dinero(monto), margen + 220f, y, derecha)
            y += 15f
        }
        y += 14f
    }

    // Lista de movimientos.
    val columnas = listOf(margen, margen + 72f, margen + 300f, ancho - margen)
    fun encabezado() {
        espacio(40f)
        lienzo().drawText(texto(R.string.import_review_fecha), columnas[0], y, subtitulo)
        lienzo().drawText(texto(R.string.import_review_descripcion), columnas[1], y, subtitulo)
        lienzo().drawText(texto(R.string.import_review_categoria), columnas[2], y, subtitulo)
        lienzo().drawText(texto(R.string.import_review_monto), columnas[3], y, Paint(subtitulo).apply { textAlign = Paint.Align.RIGHT })
        y += 6f
        lienzo().drawLine(margen, y, ancho - margen, y, linea)
        y += 14f
    }
    encabezado()
    val formato = formatoFecha("ddMMMyyyy")
    movimientos.forEach { m ->
        if (y + 15f > alto - margen - 16f) { nuevaPagina(); encabezado() }
        lienzo().drawText(formato.format(Date(m.timestamp)), columnas[0], y, normal)
        lienzo().drawText(recortar(m.description, normal, columnas[2] - columnas[1] - 8f), columnas[1], y, normal)
        lienzo().drawText(recortar(m.category.nombre(context), normal, 110f), columnas[2], y, gris)
        lienzo().drawText((if (m.isIncome) "+" else "−") + dinero(m.amountCents), columnas[3], y, derecha)
        y += 15f
    }
    if (movimientos.isEmpty()) lienzo().drawText(texto(R.string.exportar_sin_movimientos), margen, y, gris)

    pagina?.let(documento::finishPage)
    return java.io.ByteArrayOutputStream().also { documento.writeTo(it); documento.close() }.toByteArray()
}

/**
 * Pregunta periodo y formato, genera el archivo en la caché privada de la app y abre el menú de
 * compartir de Android. Nada se envía a ningún servidor de Gamora. Los archivos de exportaciones
 * anteriores se borran al exportar de nuevo.
 */
fun Fragment.exportarDatos() {
    val contexto = requireContext()
    val periodos = Periodo.entries
    dialogo(contexto, IconoDialogo.EXPORTAR)
        .setTitle(R.string.exportar_titulo)
        .setItems(periodos.map { contexto.getString(it.etiqueta) }.toTypedArray()) { _, i ->
            val periodo = periodos[i]
            dialogo(contexto, IconoDialogo.EXPORTAR)
                .setTitle(R.string.exportar_formato)
                .setItems(arrayOf(contexto.getString(R.string.exportar_csv), contexto.getString(R.string.exportar_pdf))) { _, formato ->
                    compartir(contexto, periodo, esPdf = formato == 1)
                }
                .setNegativeButton(R.string.import_review_cancelar, null)
                .show()
        }
        .setNegativeButton(R.string.import_review_cancelar, null)
        .show()
}

private fun compartir(contexto: Context, periodo: Periodo, esPdf: Boolean) {
    val movimientos = GamoraTransactionStore.movements.enPeriodo(periodo)
    val carpeta = File(contexto.cacheDir, "exportes").apply { mkdirs(); listFiles()?.forEach(File::delete) }
    val sello = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val archivo = File(carpeta, "gamora-$sello." + if (esPdf) "pdf" else "csv")

    try {
        if (esPdf) {
            archivo.writeBytes(pdfDeMovimientos(contexto, movimientos, contexto.getString(periodo.etiqueta)))
        } else {
            archivo.writeText(
                csvDeMovimientos(
                    movimientos, Moneda.actual, { it.nombre(contexto) },
                    encabezados = contexto.resources.getStringArray(R.array.exportar_encabezados).toList(),
                    ingreso = contexto.getString(R.string.transactions_ingreso),
                    gasto = contexto.getString(R.string.transactions_gasto)
                ),
                Charsets.UTF_8
            )
        }

        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.exportes", archivo)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = if (esPdf) "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, archivo.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        SalidaBreve.permitir()
        ContextCompat.startActivity(contexto, Intent.createChooser(envio, contexto.getString(R.string.exportar_titulo)), null)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(contexto, R.string.exportar_no_hay_app, Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
        Toast.makeText(contexto, R.string.exportar_error, Toast.LENGTH_LONG).show()
    }
}

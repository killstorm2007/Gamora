package com.gamora.feature.importar

import com.gamora.feature.transactions.CATEGORIAS_GASTO
import com.gamora.feature.transactions.CATEGORIAS_INGRESO
import com.gamora.feature.transactions.GamoraMovement
import org.json.JSONException
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

/** Un renglón leído de un documento, pendiente de que la persona lo confirme. */
data class MovimientoImportado(
    val fecha: Long,
    val descripcion: String,
    val montoCentavos: Long,
    val esIngreso: Boolean,
    val categoria: String,
    val seleccionado: Boolean = true,
    val repetido: Boolean = false
)

data class ResultadoLectura(
    val banco: String,
    val periodo: String,
    val movimientos: List<MovimientoImportado>,
    // Renglones que no cumplían las reglas (monto, fecha o descripción inválidos).
    val descartados: Int
)

data class Resumen(val seleccionados: Int, val ingresosCentavos: Long, val gastosCentavos: Long)

// Mismos límites que firestore.rules para un movimiento.
private const val MONTO_MAXIMO = 100_000_000_000L
private const val DESCRIPCION_MAXIMA = 200
private const val TOLERANCIA_REPETIDO_MS = 2L * 24 * 60 * 60 * 1000
private val FORMATO_FECHA = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/** "AAAA-MM-DD" a milisegundos a las 12:00 locales; null si no es una fecha real. */
internal fun fechaDe(texto: String): Long? {
    val (anio, mes, dia) = FORMATO_FECHA.matchEntire(texto.trim())?.destructured ?: return null

    return try {
        Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(anio.toInt(), mes.toInt() - 1, dia.toInt(), 12, 0)
        }.timeInMillis
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun categoriaValida(categoria: String, esIngreso: Boolean): String {
    val permitidas = if (esIngreso) CATEGORIAS_INGRESO else CATEGORIAS_GASTO
    return categoria.trim().takeIf { it in permitidas } ?: "Otros"
}

/**
 * Convierte la respuesta JSON de Gemini en movimientos. Los renglones inválidos se cuentan en
 * [ResultadoLectura.descartados]. Lanza [IllegalArgumentException] si el JSON no se puede leer.
 */
fun leerRespuestaGemini(json: String): ResultadoLectura {
    val raiz = try {
        JSONObject(json)
    } catch (error: JSONException) {
        throw IllegalArgumentException("La respuesta no es JSON válido.", error)
    }

    val filas = raiz.optJSONArray("movimientos")
    val movimientos = mutableListOf<MovimientoImportado>()
    var descartados = 0

    for (i in 0 until (filas?.length() ?: 0)) {
        val fila = filas?.optJSONObject(i)
        val movimiento = fila?.let(::leerFila)
        if (movimiento == null) descartados++ else movimientos += movimiento
    }

    return ResultadoLectura(
        banco = raiz.optString("banco").trim(),
        periodo = raiz.optString("periodo").trim(),
        movimientos = movimientos,
        descartados = descartados
    )
}

private fun leerFila(fila: JSONObject): MovimientoImportado? {
    val descripcion = fila.optString("descripcion").trim()
    if (descripcion.isEmpty() || descripcion.length > DESCRIPCION_MAXIMA) return null

    // Solo números enteros: un monto como texto o con decimales se descarta.
    val monto = when (val valor = fila.opt("montoCentavos")) {
        is Int -> valor.toLong()
        is Long -> valor
        else -> return null
    }
    if (monto <= 0 || monto > MONTO_MAXIMO) return null

    val fecha = fechaDe(fila.optString("fecha")) ?: return null
    val esIngreso = fila.optBoolean("esIngreso", false)

    return MovimientoImportado(
        fecha = fecha,
        descripcion = descripcion,
        montoCentavos = monto,
        esIngreso = esIngreso,
        categoria = categoriaValida(fila.optString("categoria"), esIngreso)
    )
}

/**
 * Desmarca lo que parece repetido: mismo tipo y monto, con fecha a ±2 días de un movimiento
 * ya registrado o de un renglón anterior del mismo documento.
 */
fun marcarRepetidos(
    nuevos: List<MovimientoImportado>,
    existentes: List<GamoraMovement>
): List<MovimientoImportado> {
    fun coincide(esIngreso: Boolean, monto: Long, fecha: Long, otro: MovimientoImportado) =
        esIngreso == otro.esIngreso && monto == otro.montoCentavos && abs(fecha - otro.fecha) <= TOLERANCIA_REPETIDO_MS

    val vistos = mutableListOf<MovimientoImportado>()

    return nuevos.map { nuevo ->
        val repetido = existentes.any { coincide(it.isIncome, it.amountCents, it.timestamp, nuevo) } ||
                vistos.any { coincide(it.esIngreso, it.montoCentavos, it.fecha, nuevo) }
        vistos += nuevo

        if (repetido) nuevo.copy(repetido = true, seleccionado = false) else nuevo
    }
}

fun MovimientoImportado.aMovimiento(): GamoraMovement =
    GamoraMovement(descripcion, categoria, montoCentavos, esIngreso, fecha)

/** Firestore acepta hasta 500 escrituras por lote. */
fun <T> List<T>.enLotes(tamano: Int = 500): List<List<T>> = chunked(tamano)

fun resumen(lista: List<MovimientoImportado>): Resumen {
    val elegidos = lista.filter { it.seleccionado }
    return Resumen(
        seleccionados = elegidos.size,
        ingresosCentavos = elegidos.filter { it.esIngreso }.sumOf { it.montoCentavos },
        gastosCentavos = elegidos.filterNot { it.esIngreso }.sumOf { it.montoCentavos }
    )
}

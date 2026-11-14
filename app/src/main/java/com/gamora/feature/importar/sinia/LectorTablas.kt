package com.gamora.feature.importar.sinia

import com.gamora.feature.importar.MovimientoImportado
import com.gamora.feature.importar.ResultadoLectura
import com.gamora.feature.importar.categoriaValida
import java.util.Calendar
import kotlin.math.abs

/*
 * Lector de estados de cuenta sin IA. Funciona con la posición de cada palabra:
 *  1. Busca el encabezado de la tabla de movimientos ("Cargos/Retiros" y "Abonos/Depósitos",
 *     o una sola columna "Monto" con signo) y recuerda dónde está cada columna.
 *  2. Cada fila que empieza con una fecha abre un movimiento; el monto se toma de la columna
 *     en la que cae el número (así se sabe si es cargo o abono, igual que al leerlo a ojo).
 * Es general: no depende de un banco en particular. Si no encuentra movimientos devuelve null
 * y el documento se manda a Gemini.
 */

private val MESES = mapOf(
    "ENE" to 0, "JAN" to 0, "FEB" to 1, "MAR" to 2, "ABR" to 3, "APR" to 3, "MAY" to 4,
    "JUN" to 5, "JUL" to 6, "AGO" to 7, "AUG" to 7, "SEP" to 8, "SET" to 8, "OCT" to 9,
    "NOV" to 10, "DIC" to 11, "DEC" to 11
)

private val MESES_LARGOS = mapOf(
    "ENERO" to 0, "FEBRERO" to 1, "MARZO" to 2, "ABRIL" to 3, "MAYO" to 4, "JUNIO" to 5, "JULIO" to 6,
    "AGOSTO" to 7, "SEPTIEMBRE" to 8, "SETIEMBRE" to 8, "OCTUBRE" to 9, "NOVIEMBRE" to 10, "DICIEMBRE" to 11
)

private val CARGO = Regex("""^(CARGOS?|RETIROS?|RETIRO/CARGO|RETIROS?/CARGOS?)$""", RegexOption.IGNORE_CASE)
private val ABONO = Regex("""^(ABONOS?|DEP.SITOS?|DEP.SITO/ABONO|DEP.SITOS?/ABONOS?)$""", RegexOption.IGNORE_CASE)
private val SALDO = Regex("""^(SALDOS?|OPERACI.N|LIQUIDACI.N)$""", RegexOption.IGNORE_CASE)
private val DESCRIPCION = Regex("""^(DESCRIPCI.N|CONCEPTO|DETALLE)$""", RegexOption.IGNORE_CASE)
private val MONTO = Regex("""^MONTO$""", RegexOption.IGNORE_CASE)

// "1,234.56", "$1,234.56", "-$28.00", "+$1600.00", "983.86-"
private val NUMERO = Regex("""^([+-]?)\$?([+-]?)(\d{1,3}(?:,\d{3})+|\d+)\.(\d{2})(-?)$""")

private val DIA_MES = Regex("""^(\d{1,2})[/-]([A-Za-z]{3})$""")                 // 10/SEP
private val DIA_MES_ANIO = Regex("""^(\d{1,2})[/-]([A-Za-z]{3})[/-](\d{2,4})$""")  // 09-sep-2026
private val NUMERICA = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})$""")          // 01/09/2026
private val SOLO_DIA = Regex("""^\d{1,2}$""")

// Pagos a la propia tarjeta en estados de crédito: no son ingresos (se omiten).
private val PAGO_TARJETA = Regex("""\b(SU PAGO|PAGO RECIBIDO|ABONO|GRACIAS|PAGO TDC|PAGO A TARJETA)\b|^PAGO (POR|EN|DE) ?""", RegexOption.IGNORE_CASE)
private val PAGO_MINIMO = Regex("""PAGO M.NIMO""", RegexOption.IGNORE_CASE)
private val FECHA_LIMITE = Regex("""FECHA L.MITE DE PAGO""", RegexOption.IGNORE_CASE)

// "Compraste con tu tarjeta": el comercio viene en la línea siguiente y la reemplaza.
private val REEMPLAZABLE = Regex("""^(Compraste|Pagaste|Recibiste|Enviaste|Retiraste|Depositaste)""", RegexOption.IGNORE_CASE)
// "SPEI RECIBIDO": se completa con la línea siguiente (quién envía o el concepto).
private val COMPLETABLE = Regex("""^(SPEI|Transferencia|Traspaso|Pago|Abono|Dep.sito)""", RegexOption.IGNORE_CASE)

/** Banco por los 3 primeros dígitos de la CLABE de la cuenta (o por su nombre). */
private val BANCOS_CLABE = mapOf(
    "002" to "Banamex", "012" to "BBVA", "014" to "Santander", "021" to "HSBC", "030" to "BanBajío",
    "036" to "Inbursa", "044" to "Scotiabank", "058" to "Banregio", "062" to "Afirme", "072" to "Banorte",
    "127" to "Banco Azteca", "137" to "BanCoppel", "638" to "Nu", "722" to "Mercado Pago", "728" to "Spin by OXXO"
)
private val BANCOS_NOMBRE = listOf(
    "BBVA" to "BBVA", "HSBC" to "HSBC", "SANTANDER" to "Santander", "BANAMEX" to "Banamex",
    "BANCO NACIONAL DE MEXICO" to "Banamex", "BANORTE" to "Banorte", "NU M" to "Nu", "REVOLUT" to "Revolut",
    "SPIN" to "Spin by OXXO", "BANREGIO" to "Banregio", "BANCO REGIONAL" to "Banregio", "AFIRME" to "Afirme",
    "BANCOPPEL" to "BanCoppel"
)

private data class Columnas(
    val cargo: Float?,          // borde derecho de la columna de cargos/retiros
    val abono: Float?,          // borde derecho de la columna de abonos/depósitos
    val saldos: List<Float>,    // columnas de saldo (se ignoran)
    val monto: Float?,          // columna única con signo (Spin, tarjetas)
    val descripcion: Float,     // donde empieza la descripción
    val soloDia: Boolean        // la columna de fecha solo trae el día ("DIA")
)

private data class Fecha(val dia: Int, val mes: Int?, val anio: Int?)

private fun mayus(texto: String) = texto.uppercase()

internal fun monto(texto: String): Pair<Long, Int>? {
    val m = NUMERO.matchEntire(texto.replace(" ", "")) ?: return null
    val (s1, s2, enteros, centavos, s3) = m.destructured
    val valor = (enteros.replace(",", "") + centavos).toLongOrNull() ?: return null
    val signo = when {
        s1 == "-" || s2 == "-" || s3 == "-" -> -1
        s1 == "+" || s2 == "+" -> 1
        else -> 0
    }
    return valor to signo
}

private fun fechaDeTokens(tokens: List<String>): Pair<Fecha, Int>? {
    val primero = tokens.getOrNull(0) ?: return null
    DIA_MES_ANIO.matchEntire(primero)?.let { m ->
        val mes = MESES[mayus(m.groupValues[2])] ?: return null
        val anio = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
        return Fecha(m.groupValues[1].toInt(), mes, anio) to 1
    }
    NUMERICA.matchEntire(primero)?.let { m ->
        val anio = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
        return Fecha(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, anio) to 1
    }
    DIA_MES.matchEntire(primero)?.let { m ->
        val mes = MESES[mayus(m.groupValues[2])] ?: return null
        return Fecha(m.groupValues[1].toInt(), mes, null) to 1
    }
    if (SOLO_DIA.matches(primero)) {
        val mes = tokens.getOrNull(1)?.let { MESES[mayus(it)] }
        return if (mes != null) Fecha(primero.toInt(), mes, null) to 2 else Fecha(primero.toInt(), null, null) to 1
    }
    return null
}

/** Último día del periodo: la fecha completa más reciente de la primera página que no sea futura. */
private fun finDePeriodo(filas: List<Fila>, hoy: Long): Calendar {
    val limite = hoy + 45L * 24 * 60 * 60 * 1000
    var mejor: Calendar? = null

    fun considerar(dia: Int, mes: Int, anio: Int) {
        if (dia !in 1..31 || mes !in 0..11 || anio !in 2000..2100) return
        val c = Calendar.getInstance().apply { clear(); set(anio, mes, dia, 12, 0) }
        if (c.timeInMillis <= limite && (mejor == null || c.after(mejor))) mejor = c
    }

    val texto = filas.filter { it.pagina <= 2 }.joinToString("\n") { mayus(it.texto) }
    FIN_DIA_MES_ANIO.findAll(texto).forEach {
        MESES[it.groupValues[2]]?.let { mes -> considerar(it.groupValues[1].toInt(), mes, it.groupValues[3].toInt()) }
    }
    FIN_NUMERICA.findAll(texto).forEach {
        considerar(it.groupValues[1].toInt(), it.groupValues[2].toInt() - 1, it.groupValues[3].toInt())
    }
    FIN_LARGA.findAll(texto).forEach {
        MESES_LARGOS[it.groupValues[2]]?.let { mes -> considerar(it.groupValues[1].toInt(), mes, it.groupValues[3].toInt()) }
    }
    FIN_MES_ANIO.findAll(texto).forEach {
        MESES_LARGOS[it.groupValues[1]]?.let { mes ->
            val c = Calendar.getInstance().apply { clear(); set(it.groupValues[2].toInt(), mes, 1) }
            considerar(c.getActualMaximum(Calendar.DAY_OF_MONTH), mes, it.groupValues[2].toInt())
        }
    }
    return mejor ?: Calendar.getInstance().apply { timeInMillis = hoy }
}

/** Completa mes y año con el fin del periodo: nunca queda una fecha posterior al corte. */
private fun aMilis(fecha: Fecha, fin: Calendar): Long? {
    val c = Calendar.getInstance().apply { clear() }
    val mes = fecha.mes ?: if (fecha.dia <= fin.get(Calendar.DAY_OF_MONTH)) fin.get(Calendar.MONTH)
    else (fin.get(Calendar.MONTH) + 11) % 12
    var anio = fecha.anio ?: fin.get(Calendar.YEAR)
    if (fecha.anio == null) {
        c.set(anio, mes, 1)
        if (c.get(Calendar.YEAR) * 12 + mes > fin.get(Calendar.YEAR) * 12 + fin.get(Calendar.MONTH)) anio--
    }
    c.clear()
    c.isLenient = false
    return try {
        c.set(anio, mes, fecha.dia, 12, 0)
        c.timeInMillis
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun detectarEncabezado(filas: List<Fila>, i: Int, permitirMonto: Boolean): Columnas? {
    val fila = filas[i]
    val textos = fila.palabras.map { it.texto.trim(':') }
    if (textos.any { mayus(it) == "RESUMEN" }) return null

    // Encabezados como "FECHA" o "DIA" pueden estar en la fila de arriba (dos renglones).
    val vecinas = (maxOf(0, i - 2)..i).map { filas[it] }.filter { it.pagina == fila.pagina }
    val palabrasVecinas = vecinas.flatMap { it.palabras }
    val soloDia = palabrasVecinas.any { COLUMNA_DIA.matches(it.texto) } &&
            palabrasVecinas.none { mayus(it.texto) == "FECHA" }

    // Solo del renglón del encabezado: un título como "Detalle de movimientos" no es la columna.
    val descripcion = fila.palabras.firstOrNull { DESCRIPCION.matches(it.texto) }?.x0

    val cargo = fila.palabras.firstOrNull { CARGO.matches(it.texto) }
    val abono = fila.palabras.firstOrNull { ABONO.matches(it.texto) }
    // Una tabla de movimientos tiene columna de fecha; "Cuenta eje para cargos y abonos" no.
    val conFecha = palabrasVecinas.any { COLUMNA_FECHA.matches(it.texto) }
    if (cargo != null && abono != null && conFecha) {
        val saldos = palabrasVecinas.filter { SALDO.matches(it.texto) }.map { it.x1 }
        return Columnas(cargo.x1, abono.x1, saldos, null, descripcion ?: 0f, soloDia)
    }

    // Una sola columna "Monto" con signo; se descartan tablas de diferidos ("Monto original").
    // Si el documento ya tiene tabla de cargos/abonos, las de "Monto" son anexos (p. ej. comprobantes SPEI).
    if (!permitirMonto) return null
    val monto = fila.palabras.firstOrNull { MONTO.matches(it.texto) } ?: return null
    val siguiente = filas.getOrNull(i + 1)?.takeIf { it.pagina == fila.pagina }
    val alrededor = (fila.palabras + (siguiente?.palabras ?: emptyList())).map { mayus(it.texto) }
    if (alrededor.any { it.startsWith("ORIGINAL") || it.startsWith("PENDIENTE") || it == "SALDO" }) return null
    if (palabrasVecinas.none { mayus(it.texto) == "FECHA" }) return null

    // El número de "Monto total" queda alineado al final de la palabra más a la derecha.
    val derecha = fila.palabras.filter { it.x0 >= monto.x0 }.maxOf { it.x1 }
    return Columnas(null, null, emptyList(), derecha, descripcion ?: 0f, false)
}

private fun cerca(a: Float?, b: Float?) = (a == null && b == null) || (a != null && b != null && abs(a - b) < 6f)

/** Encabezado de una tabla que no es de movimientos del periodo. */
private fun esOtraTabla(fila: Fila): Boolean {
    val t = fila.palabras.map { mayus(it.texto) }
    return ("RESUMEN" in t && t.any { CARGO.matches(it) || ABONO.matches(it) }) ||
            ("MONTO" in t && "SALDO" in t) ||
            t.any { it.startsWith("DIFERIDO") }
}

private class Bloque(val fecha: Fecha, val filas: MutableList<Fila>, val inicioDescripcion: Int)

/**
 * Lee las tablas de movimientos. [hoy] sirve para no aceptar fechas futuras al deducir el periodo.
 * Devuelve null si el documento no tiene una tabla reconocible.
 */
fun leerTablas(palabrasCrudas: List<Palabra>, hoy: Long = System.currentTimeMillis()): ResultadoLectura? {
    val filas = agruparFilas(normalizarPalabras(palabrasCrudas))
    if (filas.isEmpty()) return null

    val todo = filas.joinToString("\n") { it.texto }
    // Estado de tarjeta de crédito: trae "pago mínimo" y "fecha límite de pago".
    val credito = PAGO_MINIMO.containsMatchIn(todo) && FECHA_LIMITE.containsMatchIn(todo)
    val fin = finDePeriodo(filas, hoy)

    val hayColumnas = filas.any { f ->
        f.palabras.none { mayus(it.texto) == "RESUMEN" } &&
                f.palabras.any { CARGO.matches(it.texto) } && f.palabras.any { ABONO.matches(it.texto) }
    }

    val movimientos = mutableListOf<MovimientoImportado>()
    var descartados = 0
    var columnas: Columnas? = null
    var bloque: Bloque? = null
    var encontroTabla = false

    fun cerrar() {
        val b = bloque ?: return
        bloque = null
        val cols = columnas ?: return
        val leido = leerBloque(b, cols, credito, fin)
        when {
            leido == null -> Unit
            leido.descripcion.isBlank() -> descartados++
            else -> movimientos += leido
        }
    }

    // Página de la última fila leída y si un movimiento sin monto espera el encabezado de la nueva página.
    var paginaActual = -1
    var esperandoEncabezado = false

    filas.forEachIndexed { i, fila ->
        if (fila.pagina != paginaActual) {
            paginaActual = fila.pagina
            val b = bloque
            val c = columnas
            esperandoEncabezado = b != null && c != null && leerBloque(b, c, credito, fin) == null
            if (!esperandoEncabezado) cerrar()
        }

        detectarEncabezado(filas, i, permitirMonto = !hayColumnas)?.let {
            // Un movimiento al final de una página puede traer su monto después del encabezado
            // de la siguiente: si aún no tiene monto, sigue abierto.
            val anteriores = columnas
            val mismasColumnas = anteriores != null && cerca(anteriores.cargo, it.cargo) &&
                    cerca(anteriores.abono, it.abono) && cerca(anteriores.monto, it.monto)
            val pendiente = mismasColumnas &&
                    bloque?.let { b -> leerBloque(b, anteriores!!, credito, fin) } == null
            if (!pendiente) cerrar()
            esperandoEncabezado = false
            columnas = it
            encontroTabla = true
            return@forEachIndexed
        }
        // Otra tabla (resumen, compras a meses…): dejar de leer hasta el siguiente encabezado válido.
        if (esOtraTabla(fila) || (hayColumnas && fila.palabras.any { MONTO.matches(it.texto) })) {
            cerrar()
            columnas = null
            return@forEachIndexed
        }
        val cols = columnas ?: return@forEachIndexed
        val izquierda = fila.palabras.first()
        val limiteFecha = if (cols.descripcion > 30f) cols.descripcion + 2f else 120f
        val fecha = if (izquierda.x0 < limiteFecha) {
            fechaDeTokens(fila.palabras.map { it.texto })
                ?.takeIf { (f, _) -> f.dia in 1..31 && (f.mes != null || cols.soloDia) }
        } else null

        // Entre el cambio de página y el encabezado (o el siguiente movimiento, en bancos que no
        // repiten encabezado) vienen membretes y pies: no son parte del movimiento pendiente.
        if (esperandoEncabezado) {
            if (fecha == null) return@forEachIndexed
            esperandoEncabezado = false
        }

        if (fecha != null) {
            cerrar()
            bloque = Bloque(fecha.first, mutableListOf(fila), fecha.second)
        } else {
            bloque?.filas?.add(fila)
        }
    }
    cerrar()

    if (!encontroTabla || movimientos.isEmpty()) return null

    return ResultadoLectura(
        banco = detectarBanco(filas),
        periodo = "",
        movimientos = movimientos,
        descartados = descartados
    )
}

private fun detectarBanco(filas: List<Fila>): String {
    val texto = filas.joinToString(" ") { it.texto }
    CLABE.find(texto)?.let {
        BANCOS_CLABE[it.groupValues[1].take(3)]?.let { banco -> return banco }
    }
    // El nombre del banco emisor aparece arriba de la primera página; abajo puede haber otros
    // bancos en transferencias ("SPEI RECIBIDO BBVA").
    for (parte in listOf(filas.filter { it.pagina == 1 }.take(15), filas.filter { it.pagina == 1 }, filas)) {
        val mayusculas = mayus(parte.joinToString(" ") { it.texto })
        BANCOS_NOMBRE.firstOrNull { (clave, _) -> clave in mayusculas }?.let { return it.second }
    }
    return ""
}

private fun leerBloque(b: Bloque, cols: Columnas, credito: Boolean, fin: Calendar): MovimientoImportado? {
    val columnasMonto = buildList {
        cols.cargo?.let { add(it to "cargo") }
        cols.abono?.let { add(it to "abono") }
        cols.monto?.let { add(it to "monto") }
        cols.saldos.forEach { add(it to "saldo") }
    }

    // El monto: la primera fila del bloque con un número en la columna de cargo, abono o monto.
    var importe: Long? = null
    var tipo: String? = null
    var signo = 0
    var xMonto = Float.MAX_VALUE
    for (fila in b.filas) {
        fila.palabras.forEachIndexed { idx, palabra ->
            if (importe != null) return@forEachIndexed
            val (valor, s) = monto(palabra.texto) ?: return@forEachIndexed
            val (x, columna) = columnasMonto.minByOrNull { abs(it.first - palabra.x1) } ?: return@forEachIndexed
            if (abs(x - palabra.x1) > 60f || columna == "saldo") return@forEachIndexed

            // Signo suelto antes del número ("+ $1,033.33" o "$ 1,200.00-").
            val anterior = fila.palabras.getOrNull(idx - 1)?.texto
            importe = valor
            tipo = columna
            xMonto = palabra.x0 - 4f
            signo = when {
                s != 0 -> s
                anterior == "-" -> -1
                anterior == "+" -> 1
                else -> 0
            }
        }
        if (importe != null) break
    }
    val valor = importe ?: return null
    if (valor <= 0L) return null

    // Descripción: lo que hay entre la fecha y la columna del monto, sin fechas, montos ni números largos
    // (tarjetas, referencias, claves de rastreo).
    fun descripcionDe(fila: Fila, desde: Int, desdeX: Float = -1f): String = fila.palabras.drop(desde)
        .filter { it.x1 < xMonto + 4f && it.x0 >= desdeX }
        .let(::sinRfc)
        // "4741000011112222-RETIRO" → "RETIRO": el número de tarjeta pegado no aporta.
        .map { it.texto.replace(NUMERO_PEGADO, "") }
        .filterNot { esRuido(it) }
        .joinToString(" ")
        .trim()

    var descripcion = descripcionDe(b.filas.first(), b.inicioDescripcion)
    val reemplazar = REEMPLAZABLE.containsMatchIn(descripcion)
    if (b.filas.size > 1 && (reemplazar || descripcion.split(" ").size < 3 || COMPLETABLE.containsMatchIn(descripcion))) {
        // La línea extra debe estar alineada con la descripción: así no se toman títulos de sección.
        val inicio = b.filas.first().palabras.drop(b.inicioDescripcion).firstOrNull { !esRuido(it.texto) }?.x0 ?: 0f
        val desdeX = minOf(inicio, if (cols.descripcion > 30f) cols.descripcion else inicio) - 6f
        val extra = b.filas.drop(1).asSequence()
            .take(3)
            // Una línea que empieza más a la izquierda que la descripción es otra cosa (p. ej. un título).
            .takeWhile { f -> (f.palabras.firstOrNull { !esRuido(it.texto) }?.x0 ?: desdeX) >= desdeX }
            .map { descripcionDe(it, 0, desdeX).substringBefore("Referencia").trim() }
            // Líneas con etiqueta ("CUENTA:", "EMISOR:", "RFC:") son datos de la operación, no la descripción.
            .filterNot { it.substringBefore(" ").contains(':') }
            // Totales y títulos de sección ("TOTAL", "CARGOS, ABONOS Y COMPRAS REGULARES").
            .filterNot { TITULO_SECCION.containsMatchIn(it) }
            .firstOrNull { it.count(Char::isLetter) >= 3 }
        if (extra != null) descripcion = if (reemplazar) extra else "$descripcion · $extra"
    }
    descripcion = descripcion.replace('�', ' ').replace(ESPACIOS, " ").trim().take(200)

    val esIngreso = when (tipo) {
        "abono" -> true
        "cargo" -> false
        // Tarjeta: "+" es cargo y "-" es pago o devolución. Cuenta (Spin): "-" es gasto y sin signo, depósito.
        else -> if (credito) signo < 0 else signo >= 0
    }

    // En tarjetas de crédito, los pagos a la propia tarjeta no son ingresos ni gastos: se omiten.
    // ("CANCELACION ABONO TEMPORAL" es lo contrario: un cargo).
    if (credito && PAGO_TARJETA.containsMatchIn(descripcion) && !descripcion.contains("CANCELACI", ignoreCase = true)) return null

    val fecha = aMilis(b.fecha, fin) ?: return null

    return MovimientoImportado(
        fecha = fecha,
        descripcion = descripcion,
        montoCentavos = valor,
        esIngreso = esIngreso,
        categoria = categoriaValida(categoriaSugerida(descripcion, esIngreso), esIngreso)
    )
}

// Expresiones que se usan por cada palabra o fila: se compilan una sola vez.
private val FIN_DIA_MES_ANIO = Regex("""(\d{1,2})[/-]([A-Z]{3})[/-](\d{4})""")
private val FIN_NUMERICA = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
private val FIN_LARGA = Regex("""(\d{1,2}) DE ([A-Z]+) DE (\d{4})""")
private val FIN_MES_ANIO = Regex("""\b([A-Z]+) (?:DE )?(\d{4})\b""")
private val COLUMNA_DIA = Regex("""^D.A$""", RegexOption.IGNORE_CASE)
private val COLUMNA_FECHA = Regex("""^(FECHA|D.A|OPER)$""", RegexOption.IGNORE_CASE)
private val CLABE = Regex("""CLABE[^0-9]{0,40}(\d{18})""", RegexOption.IGNORE_CASE)
private val ESPACIOS = Regex("""\s+""")
private val HORA = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
private val AM_PM = setOf("am", "pm")
private val HEXADECIMAL = Regex("""^[0-9a-fA-F]+$""")
private val NUMERO_PEGADO = Regex("""^\d{8,}-""")
private val TITULO_SECCION = Regex("""^(TOTAL|SALDO)\b|\bCARGOS\b|\bABONOS\b""", RegexOption.IGNORE_CASE)

/**
 * El RFC del comercio viene en su propia columna, separado de la descripción
 * ("OXXO GAS TRIGAL      SGM 950714DC2"): tras un hueco ancho, un tramo corto con fecha del RFC se quita.
 */
private fun sinRfc(palabras: List<Palabra>): List<Palabra> {
    for (i in 1 until palabras.size) {
        if (palabras[i].x0 - palabras[i - 1].x1 < 40f) continue
        val resto = palabras.drop(i).joinToString("") { it.texto }.trimEnd('+', '-')
        if (resto.length in 9..15 && resto.count(Char::isDigit) >= 5 && resto.any(Char::isLetter)) return palabras.take(i)
    }
    return palabras
}

/** Palabras que no aportan a la descripción: fechas, horas, montos, signos y números largos. */
private fun esRuido(palabra: String): Boolean {
    if (palabra == "$" || palabra == "+" || palabra == "-" || monto(palabra) != null) return true
    if (DIA_MES.matches(palabra) || DIA_MES_ANIO.matches(palabra) || NUMERICA.matches(palabra)) return true
    if (HORA.matches(palabra) || palabra.lowercase() in AM_PM) return true
    // Identificadores de operación como "5f00aa11bb22cc33dd44ee55".
    if (palabra.length >= 16 && HEXADECIMAL.matches(palabra)) return true
    if (palabra.equals("Referencia", ignoreCase = true)) return true
    val digitos = palabra.count(Char::isDigit)
    // Folios y números de operación ("0000000", "6823589").
    if (digitos >= 5 && palabra.none(Char::isLetter)) return true
    return digitos >= 8 && digitos * 10 >= palabra.length * 6
}

/** Categoría por palabras clave comunes; lo que no se reconoce queda en "Otros". */
internal fun categoriaSugerida(descripcion: String, esIngreso: Boolean): String {
    val d = mayus(descripcion)
    fun hay(vararg claves: String) = claves.any { it in d }
    return if (esIngreso) when {
        hay("NOMINA", "NÓMINA", "SUELDO", "SALARIO") -> "Salario"
        hay("INTERES", "RENDIMIENTO") -> "Inversiones"
        else -> "Otros"
    } else when {
        hay("TIEMPO AIRE", "RECARGA", "TELCEL", "CFE", "TELMEX", "IZZI", "TOTALPLAY", "MEGACABLE", "AGUA", "GAS DISTR", "AT&T") -> "Servicios"
        hay("PEMEX", "GASOLIN", "OXXO GAS", "CASETA", "ESTACIONAMIENTO") -> "Transporte"
        hay("OXXO", "SORIANA", "WALMART", "WAL MART", "CHEDRAUI", "SUPER", "HEB", "COSTCO", "SAMS", "IFOOD",
            "RAPPI", "DIDI FOOD", "UBER EATS", "RESTAUR", "TACOS", "STARBUCKS", "MCDONALD", "BURGER", "PIZZA") -> "Comida"
        hay("UBER", "DIDI") -> "Transporte"
        hay("NETFLIX", "SPOTIFY", "DISNEY", "HBO", "PRIME VIDEO", "STEAM", "PLAYSTATION", "XBOX", "CINEPOLIS", "CINEMEX") -> "Entretenimiento"
        hay("FARMACIA", "HOSPITAL", "MEDIC", "DOCTOR", "LABORATORIO") -> "Salud"
        hay("AMAZON", "MERCADO LIBRE", "MERCADOLIBRE", "LIVERPOOL", "COPPEL", "SHEIN", "ELEKTRA") -> "Compras"
        hay("COLEGIATURA", "ESCUELA", "UNIVERSIDAD") -> "Educación"
        hay("RENTA", "HIPOTECA") -> "Vivienda"
        else -> "Otros"
    }
}

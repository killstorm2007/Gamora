package com.gamora.feature.importar.sinia

import kotlin.math.abs

/**
 * Una palabra del documento con su posición en la página (en puntos de PDF: 1/72 de pulgada).
 * Vienen del texto del PDF o del reconocimiento de texto de una imagen.
 */
data class Palabra(
    val texto: String,
    val x0: Float,
    val x1: Float,
    val y: Float,
    val pagina: Int,
    val alto: Float = 8f
)

/** Palabras que están a la misma altura, ordenadas de izquierda a derecha. */
data class Fila(val pagina: Int, val y: Float, val palabras: List<Palabra>) {
    val texto: String get() = palabras.joinToString(" ") { it.texto }
}

/** Junta en filas las palabras cuya altura difiere menos que media letra. */
fun agruparFilas(palabras: List<Palabra>): List<Fila> {
    if (palabras.isEmpty()) return emptyList()

    val altos = palabras.map { it.alto }.sorted()
    val tolerancia = maxOf(2.5f, altos[altos.size / 2] * 0.45f)

    val filas = mutableListOf<Fila>()
    palabras.groupBy { it.pagina }.toSortedMap().forEach { (pagina, dePagina) ->
        var actual = mutableListOf<Palabra>()
        var yFila = Float.NaN

        dePagina.sortedBy { it.y }.forEach { palabra ->
            if (actual.isNotEmpty() && palabra.y - yFila > tolerancia) {
                filas += Fila(pagina, yFila, actual.sortedBy { it.x0 })
                actual = mutableListOf()
            }
            if (actual.isEmpty()) yFila = palabra.y
            actual += palabra
        }
        if (actual.isNotEmpty()) filas += Fila(pagina, yFila, actual.sortedBy { it.x0 })
    }
    return filas
}

// Algunos bancos (p. ej. HSBC) generan el PDF con una fuente en EBCDIC (IBM-037): el texto sale
// como "ÕÅãÕÔ@×" en lugar de "NETNM P". Cada carácter cuyo código Latin-1 es un byte 0x40-0xFF
// se traduce con la tabla IBM-037.
private const val IBM037 = "\u0020\u00A0\u00E2\u00E4\u00E0\u00E1\u00E3\u00E5\u00E7\u00F1\u00A2\u002E\u003C\u0028\u002B\u007C\u0026\u00E9\u00EA\u00EB\u00E8\u00ED\u00EE\u00EF\u00EC\u00DF\u0021\u0024\u002A\u0029\u003B\u00AC\u002D\u002F\u00C2\u00C4\u00C0\u00C1\u00C3\u00C5\u00C7\u00D1\u00A6\u002C\u0025\u005F\u003E\u003F\u00F8\u00C9\u00CA\u00CB\u00C8\u00CD\u00CE\u00CF\u00CC\u0060\u003A\u0023\u0040\u0027\u003D\u0022\u00D8\u0061\u0062\u0063\u0064\u0065\u0066\u0067\u0068\u0069\u00AB\u00BB\u00F0\u00FD\u00FE\u00B1\u00B0\u006A\u006B\u006C\u006D\u006E\u006F\u0070\u0071\u0072\u00AA\u00BA\u00E6\u00B8\u00C6\u00A4\u00B5\u007E\u0073\u0074\u0075\u0076\u0077\u0078\u0079\u007A\u00A1\u00BF\u00D0\u00DD\u00DE\u00AE\u005E\u00A3\u00A5\u00B7\u00A9\u00A7\u00B6\u00BC\u00BD\u00BE\u005B\u005D\u00AF\u00A8\u00B4\u00D7\u007B\u0041\u0042\u0043\u0044\u0045\u0046\u0047\u0048\u0049\u00AD\u00F4\u00F6\u00F2\u00F3\u00F5\u007D\u004A\u004B\u004C\u004D\u004E\u004F\u0050\u0051\u0052\u00B9\u00FB\u00FC\u00F9\u00FA\u00FF\u005C\u00F7\u0053\u0054\u0055\u0056\u0057\u0058\u0059\u005A\u00B2\u00D4\u00D6\u00D2\u00D3\u00D5\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037\u0038\u0039\u00B3\u00DB\u00DC\u00D9\u00DA\u009F"

private val EBCDIC: Map<Char, Char> = IBM037.mapIndexed { i, c -> (0x40 + i).toChar() to c }.toMap()

/**
 * Si la palabra parece EBCDIC (la mayoría de sus caracteres caen en el rango alto de Latin-1),
 * la traduce; si no, la deja igual. "Día" o "Depósito" no cambian.
 */
fun decodificarEbcdic(texto: String): String {
    if (texto.length < 2) return texto
    // Mitad o más en la parte alta de Latin-1 (0x80-0xFF): "Depósito" (1 de 8) no se toca.
    val altos = texto.count { it.code in 0x80..0xFF }
    if (altos * 2 < texto.length) return texto
    if (!texto.all { it in EBCDIC }) return texto
    return texto.map { EBCDIC[it] ?: it }.joinToString("")
}

/** Aplica [decodificarEbcdic] y separa en varias palabras si la traducción trae espacios. */
fun normalizarPalabras(palabras: List<Palabra>): List<Palabra> = palabras.flatMap { palabra ->
    val texto = decodificarEbcdic(palabra.texto)
    if (texto == palabra.texto) return@flatMap listOf(palabra)

    val partes = texto.split(' ').filter { it.isNotEmpty() }
    if (partes.size <= 1) return@flatMap listOf(palabra.copy(texto = texto.trim()))

    // Reparte el ancho original en proporción al largo de cada parte (los montos quedan al final).
    val total = texto.length.toFloat()
    val ancho = palabra.x1 - palabra.x0
    var desde = 0
    texto.split(' ').mapNotNull { parte ->
        val inicio = desde
        desde += parte.length + 1
        if (parte.isEmpty()) null
        else palabra.copy(
            texto = parte,
            x0 = palabra.x0 + ancho * inicio / total,
            x1 = palabra.x0 + ancho * (inicio + parte.length) / total
        )
    }
}

/*
 * Correcciones del reconocimiento de texto (solo para escaneos y fotos): confusiones típicas
 * de letras y números, y separadores de tabla ("|") que quedan pegados a las palabras.
 */

private val MESES_CORTOS = setOf(
    "ENE", "JAN", "FEB", "MAR", "ABR", "APR", "MAY", "JUN", "JUL", "AGO", "AUG",
    "SEP", "SET", "OCT", "NOV", "DIC", "DEC"
)

// "16-Mar-202618-Mar-2026" o "27-Mar-2026OXXO": la fecha y lo que trae pegado.
private val FECHA_PEGADA = Regex("""^(\d{1,3}[/-][A-Za-z0-9]{3}[/-]\d{4})(.+)$""")
private val FECHA_OCR = Regex("""^(\d{1,3})([/-])([A-Za-z0-9]{3})([/-]\d{2,4})?$""")
// "S8.885.05", "$O.00", "1,200.00": prefijo $ (o S), separadores confundidos y O por 0.
private val MONTO_OCR = Regex("""^([+-]?)([S$]?)([0-9Oo][0-9Oo.,]*)[.,]([0-9Oo]{2})(-?)$""")
// "$2.499 93": el punto decimal se leyó como espacio.
private val MONTO_PARTIDO = Regex("""^([S$]?)(\d{1,3}(?:[.,]\d{3})+)$""")
private val MILES = Regex("""^\d{1,3}(,\d{3})*$""")
private val DOS_DIGITOS = Regex("""^\d{2}$""")

/** Corrige una palabra suelta del reconocimiento de texto. */
fun corregirTextoOcr(texto: String): String {
    FECHA_OCR.matchEntire(texto)?.let { m ->
        var dia = m.groupValues[1]
        // Un "|" leído como "1" antes del día: "130-Mar-2026".
        if (dia.length == 3 && dia.startsWith("1")) dia = dia.drop(1)
        val mes = m.groupValues[3].uppercase()
            .replace('0', 'O').replace('1', 'I').replace('5', 'S').replace('8', 'B')
        if (mes in MESES_CORTOS) {
            val original = m.groupValues[3]
            return dia + m.groupValues[2] + (if (original.all { it.isLetter() }) original else mes) + m.groupValues[4]
        }
    }
    MONTO_OCR.matchEntire(texto)?.let { m ->
        val (signo, pesos, enteros, centavos, signoFinal) = m.destructured
        if (texto.none { it.isDigit() }) return texto
        val limpio = enteros.replace('O', '0').replace('o', '0').replace('.', ',').trimEnd(',')
        val cuerpo = limpio.replace(",", "")
        // Solo separadores de miles bien puestos (o ninguno): "8,885" sí, "88,85" no.
        val conMiles = if (MILES.matches(limpio)) limpio else cuerpo
        return signo + (if (pesos.isNotEmpty()) "$" else "") + conMiles + "." +
                centavos.replace('O', '0').replace('o', '0') + signoFinal
    }
    return texto
}

/** Correcciones del reconocimiento de texto antes de leer las tablas. */
fun corregirOcr(palabras: List<Palabra>): List<Palabra> {
    // 1. Separar en "|" y despegar fechas.
    val separadas = palabras.flatMap { palabra -> partir(palabra) }.map { it.copy(texto = corregirTextoOcr(it.texto)) }

    // 2. Con vecinos de la misma fila: "S" antes de un monto es "$"; "$2.499" + "93" es "$2,499.93".
    val resultado = mutableListOf<Palabra>()
    separadas.groupBy { it.pagina }.toSortedMap().values.forEach { dePagina ->
        val ordenadas = agruparFilas(dePagina).flatMap { it.palabras }
        var i = 0
        while (i < ordenadas.size) {
            val actual = ordenadas[i]
            val siguiente = ordenadas.getOrNull(i + 1)?.takeIf { abs(it.y - actual.y) < 4f && it.x0 - actual.x1 < 10f }
            when {
                actual.texto == "S" && siguiente != null && siguiente.texto.first().isDigit() &&
                        MONTO_OCR.matches(siguiente.texto) -> resultado += actual.copy(texto = "$")
                siguiente != null && DOS_DIGITOS.matches(siguiente.texto) && MONTO_PARTIDO.matches(actual.texto) &&
                        (actual.texto.first() in "S$") -> {
                    val m = MONTO_PARTIDO.matchEntire(actual.texto)!!
                    val enteros = m.groupValues[2].replace('.', ',')
                    resultado += actual.copy(texto = "$" + enteros + "." + siguiente.texto, x1 = siguiente.x1)
                    i++
                }
                else -> resultado += actual
            }
            i++
        }
    }
    return resultado
}

private fun partir(palabra: Palabra): List<Palabra> {
    val texto = palabra.texto
    val ancho = palabra.x1 - palabra.x0
    fun trozo(desde: Int, hasta: Int, t: String) = palabra.copy(
        texto = t,
        x0 = palabra.x0 + ancho * desde / texto.length,
        x1 = palabra.x0 + ancho * hasta / texto.length
    )

    // Partes separadas por "|" con su posición proporcional.
    val partes = mutableListOf<Palabra>()
    var inicio = 0
    for (i in 0..texto.length) {
        if (i == texto.length || texto[i] == '|') {
            if (i > inicio) partes += trozo(inicio, i, texto.substring(inicio, i))
            inicio = i + 1
        }
    }

    // Fechas con texto pegado (a veces otra fecha).
    return partes.flatMap { p ->
        val m = FECHA_PEGADA.matchEntire(p.texto) ?: return@flatMap listOf(p)
        val corte = m.groupValues[1].length
        val anchoP = p.x1 - p.x0
        val x = p.x0 + anchoP * corte / p.texto.length
        listOf(p.copy(texto = m.groupValues[1], x1 = x)) + partir(p.copy(texto = m.groupValues[2], x0 = x))
    }
}

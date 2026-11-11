package com.gamora.core.ui

import android.content.Context
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

val LOCALE_MX: Locale = Locale.forLanguageTag("es-MX")

/**
 * Moneda elegida en Perfil › Preferencias financieras. Se guarda también en el teléfono
 * para mostrar los montos bien desde el arranque, antes de leer Firestore.
 */
object Moneda {
    val DISPONIBLES = listOf("MXN", "USD", "EUR")

    val NOMBRES = mapOf(
        "MXN" to "Peso mexicano (MXN)",
        "USD" to "Dólar estadounidense (USD)",
        "EUR" to "Euro (EUR)"
    )

    private const val ARCHIVO = "gamora_moneda"
    private const val CLAVE = "codigo"

    private val estado = MutableStateFlow("MXN")
    val flow: StateFlow<String> = estado
    val actual: String get() = estado.value

    fun iniciar(context: Context) {
        val guardada = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).getString(CLAVE, null)
        if (guardada in DISPONIBLES) estado.value = guardada!!
    }

    fun cambiar(context: Context, codigo: String) {
        if (codigo !in DISPONIBLES) return
        context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().putString(CLAVE, codigo).apply()
        estado.value = codigo
    }
}

// Mismo formato de números para todas; solo cambia el símbolo.
private val SIMBOLOS = mapOf("MXN" to "$", "USD" to "US$", "EUR" to "€")

private val formatos = mutableMapOf<String, NumberFormat>()

private fun formato(codigo: String): NumberFormat = synchronized(formatos) {
    formatos.getOrPut(codigo) {
        (NumberFormat.getCurrencyInstance(LOCALE_MX) as DecimalFormat).apply {
            currency = Currency.getInstance(codigo)
            decimalFormatSymbols = decimalFormatSymbols.apply { currencySymbol = SIMBOLOS[codigo] ?: codigo }
        }
    }
}

/** Centavos a texto en la moneda elegida, p. ej. 123450 → "$1,234.50" o "€1,234.50". */
fun dinero(centavos: Long, moneda: String = Moneda.actual): String =
    formato(moneda).format(centavos / 100.0)

/**
 * Texto escrito por la persona ("1,250.5", "1250,50") a centavos.
 * Devuelve null si está vacío, no es número, es negativo o tiene más de 2 decimales.
 */
fun centavosDe(texto: String): Long? {
    val limpio = texto.trim().replace(',', '.')

    // "1.250.50": la última marca es el punto decimal; las anteriores, separadores de miles.
    val normalizado = if (limpio.count { it == '.' } > 1) {
        val ultimo = limpio.lastIndexOf('.')
        limpio.substring(0, ultimo).replace(".", "") + limpio.substring(ultimo)
    } else limpio

    val cantidad = normalizado.toBigDecimalOrNull() ?: return null
    if (cantidad.signum() < 0 || cantidad.scale() > 2) return null

    return try {
        cantidad.movePointRight(2).longValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

fun Context.dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

fun Fragment.dp(valor: Int): Int = requireContext().dp(valor)

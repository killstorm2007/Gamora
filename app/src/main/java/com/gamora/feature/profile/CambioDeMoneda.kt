package com.gamora.feature.profile

import com.gamora.core.esperar
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// Mismo límite que las reglas de Firestore.
private const val MAXIMO_CENTAVOS = 100_000_000_000L

// De más a menos valor: el tipo de cambio se escribe "1 de la fuerte = X de la débil" (1 USD = 18.50 MXN).
private val VALOR = listOf("EUR", "USD", "MXN")

/** (fuerte, débil) para pedir el tipo de cambio como se publica. */
fun monedasDelTipoDeCambio(anterior: String, nueva: String): Pair<String, String> =
    if (VALOR.indexOf(anterior).let { if (it < 0) Int.MAX_VALUE else it } <= VALOR.indexOf(nueva).let { if (it < 0) Int.MAX_VALUE else it })
        anterior to nueva
    else nueva to anterior

/** Por cuánto multiplicar los montos en [anterior] para tenerlos en [nueva], con "1 fuerte = [tasa] débil". */
fun factorDeConversion(anterior: String, nueva: String, tasa: BigDecimal): BigDecimal {
    val (fuerte, _) = monedasDelTipoDeCambio(anterior, nueva)
    return if (anterior == fuerte) tasa else BigDecimal.ONE.divide(tasa, MathContext.DECIMAL64)
}

/** El monto convertido, redondeado al centavo y dentro de los límites ([minimo] centavos o más). */
fun convertirMonto(centavos: Long, factor: BigDecimal, minimo: Long = 1): Long {
    if (centavos == 0L && minimo == 0L) return 0L
    return BigDecimal.valueOf(centavos).multiply(factor)
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()
        .coerceIn(minimo, MAXIMO_CENTAVOS)
}

/**
 * Convierte a otra moneda todo lo que tiene montos: movimientos, recurrentes, metas y presupuestos por
 * categoría. Lee del servidor (no del caché) para no dejar nada sin convertir. Con menos de 500 cambios
 * (lo normal) todo va en un solo batch: se guarda todo o nada.
 */
object CambioDeMoneda {

    suspend fun convertirTodo(uid: String, factor: BigDecimal) {
        val db = FirebaseFirestore.getInstance()
        val usuario = db.collection("users").document(uid)
        val cambios = mutableListOf<(WriteBatch) -> Unit>()

        fun monto(referencia: DocumentReference, valor: Long?) {
            if (valor != null) cambios += { it.update(referencia, "montoCentavos", convertirMonto(valor, factor)) }
        }

        usuario.collection("movimientos").get(Source.SERVER).esperar().documents
            .forEach { monto(it.reference, it.getLong("montoCentavos")) }

        usuario.collection("recurrentes").get(Source.SERVER).esperar().documents
            .forEach { monto(it.reference, it.getLong("montoCentavos")) }

        usuario.collection("metas").get(Source.SERVER).esperar().documents.forEach { meta ->
            val objetivo = meta.getLong("objetivoCentavos") ?: return@forEach
            val ahorrado = meta.getLong("ahorradoCentavos") ?: 0L
            cambios += {
                it.update(
                    meta.reference,
                    mapOf(
                        "objetivoCentavos" to convertirMonto(objetivo, factor),
                        "ahorradoCentavos" to convertirMonto(ahorrado, factor, minimo = 0)
                    )
                )
            }
        }

        val presupuestos = usuario.collection("preferencias").document("presupuestos").get(Source.SERVER).esperar()
        val limites = presupuestos.get("categorias") as? Map<*, *>
        if (limites != null) {
            val convertidos = limites.mapNotNull { (clave, valor) ->
                val limite = (valor as? Number)?.toLong() ?: return@mapNotNull null
                (clave as? String ?: return@mapNotNull null) to convertirMonto(limite, factor)
            }.toMap()
            cambios += {
                it.set(presupuestos.reference, mapOf("categorias" to convertidos, "fechaActualizacion" to FieldValue.serverTimestamp()))
            }
        }

        cambios.chunked(500).forEach { grupo ->
            db.batch().apply { grupo.forEach { cambio -> cambio(this) } }.commit().esperar()
        }
    }
}

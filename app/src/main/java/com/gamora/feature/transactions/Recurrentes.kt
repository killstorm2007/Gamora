package com.gamora.feature.transactions

import com.gamora.core.esperar
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

/** Movimiento que se registra solo cada mes (renta, salario, suscripciones…). */
data class MovimientoRecurrente(
    val id: String,
    val descripcion: String,
    val categoria: String,
    val montoCentavos: Long,
    val esIngreso: Boolean,
    // Día del mes (1-28) en que se registra.
    val dia: Int,
    val proximaFecha: Long
)

/** El día [dia] del mes siguiente a [desde], con la misma hora. */
fun siguienteFecha(desde: Long, dia: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = desde
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, 1)
        set(Calendar.DAY_OF_MONTH, dia.coerceIn(1, 28))
    }.timeInMillis

/** Fechas que ya tocaba registrar desde [proxima] hasta [ahora] (como máximo 24, por si la app no se abrió en mucho tiempo). */
fun fechasPendientes(proxima: Long, dia: Int, ahora: Long): List<Long> =
    generateSequence(proxima) { siguienteFecha(it, dia) }
        .takeWhile { it <= ahora }
        .take(24)
        .toList()

/** users/{uid}/recurrentes. Los movimientos generados usan el id "{recurrente}_{año}{mes}" para no duplicarse. */
object Recurrentes {

    private fun usuario(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid)

    private fun coleccion(uid: String): CollectionReference = usuario(uid).collection("recurrentes")

    /** Registra que [movimiento] (ya guardado) se repita cada mes a partir del siguiente. */
    fun crear(uid: String, movimiento: GamoraMovement, onError: (String) -> Unit) {
        val dia = Calendar.getInstance()
            .apply { timeInMillis = movimiento.timestamp }
            .get(Calendar.DAY_OF_MONTH)
            .coerceAtMost(28)

        coleccion(uid).add(
            mapOf(
                "descripcion" to movimiento.description,
                "categoria" to movimiento.category,
                "montoCentavos" to movimiento.amountCents,
                "esIngreso" to movimiento.isIncome,
                "dia" to dia,
                "proximaFecha" to siguienteFecha(movimiento.timestamp, dia)
            )
        ).addOnFailureListener { error ->
            onError(error.localizedMessage ?: "No se pudo programar la repetición.")
        }
    }

    suspend fun listar(uid: String): List<MovimientoRecurrente> =
        coleccion(uid).get().esperar().documents.map { doc ->
            MovimientoRecurrente(
                id = doc.id,
                descripcion = doc.getString("descripcion").orEmpty(),
                categoria = doc.getString("categoria") ?: "Otros",
                montoCentavos = doc.getLong("montoCentavos") ?: 0L,
                esIngreso = doc.getBoolean("esIngreso") ?: false,
                dia = (doc.getLong("dia") ?: 1L).toInt(),
                proximaFecha = doc.getLong("proximaFecha") ?: 0L
            )
        }.sortedBy { it.dia }

    /** Deja de repetirse; los movimientos ya registrados se conservan. */
    suspend fun eliminar(uid: String, id: String) {
        coleccion(uid).document(id).delete().esperar()
    }

    /** Registra los movimientos que ya tocaban. Devuelve cuántos se crearon. */
    suspend fun procesar(uid: String, ahora: Long = System.currentTimeMillis()): Int {
        val db = FirebaseFirestore.getInstance()
        val movimientos = usuario(uid).collection("movimientos")
        var creados = 0

        val vencidos = coleccion(uid).whereLessThanOrEqualTo("proximaFecha", ahora).get().esperar()

        for (doc in vencidos.documents) {
            val dia = (doc.getLong("dia") ?: continue).toInt()
            val proxima = doc.getLong("proximaFecha") ?: continue
            val monto = doc.getLong("montoCentavos") ?: continue
            val fechas = fechasPendientes(proxima, dia, ahora)
            if (fechas.isEmpty()) continue

            val lote = db.batch()
            val calendario = Calendar.getInstance()

            fechas.forEach { fecha ->
                calendario.timeInMillis = fecha
                val idMovimiento = "%s_%04d%02d".format(
                    doc.id, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH) + 1
                )
                lote.set(
                    movimientos.document(idMovimiento),
                    mapOf(
                        "descripcion" to (doc.getString("descripcion") ?: "Recurrente"),
                        "categoria" to (doc.getString("categoria") ?: "Otros"),
                        "montoCentavos" to monto,
                        "esIngreso" to (doc.getBoolean("esIngreso") ?: false),
                        "fecha" to fecha
                    )
                )
            }
            lote.update(doc.reference, "proximaFecha", siguienteFecha(fechas.last(), dia))
            lote.commit().esperar()
            creados += fechas.size
        }
        return creados
    }
}

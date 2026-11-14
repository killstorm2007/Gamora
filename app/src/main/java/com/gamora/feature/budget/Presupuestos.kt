package com.gamora.feature.budget

import android.util.Log
import com.gamora.feature.transactions.GamoraMovement
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cuánto se lleva gastado en una categoría frente a su límite del periodo. */
data class AvancePresupuesto(
    val categoria: String,
    val gastadoCentavos: Long,
    val limiteCentavos: Long
) {
    val porcentaje: Int get() = if (limiteCentavos > 0) (gastadoCentavos * 100 / limiteCentavos).toInt() else 0
    val restanteCentavos: Long get() = limiteCentavos - gastadoCentavos
}

/** Avance de cada categoría con límite, desde [inicioPeriodo]; primero las más cerca de pasarse. */
fun avancePresupuestos(
    movimientos: List<GamoraMovement>,
    limites: Map<String, Long>,
    inicioPeriodo: Long
): List<AvancePresupuesto> {
    val gastado = movimientos
        .filter { !it.isIncome && it.timestamp >= inicioPeriodo }
        .groupBy { it.category }
        .mapValues { (_, lista) -> lista.sumOf { it.amountCents } }

    return limites
        .filterValues { it > 0 }
        .map { (categoria, limite) -> AvancePresupuesto(categoria, gastado[categoria] ?: 0L, limite) }
        .sortedByDescending { it.gastadoCentavos.toDouble() / it.limiteCentavos }
}

/** Límites por categoría en users/{uid}/preferencias/presupuestos → { categorias: { "Comida": 300000, … } }. */
object PresupuestoStore {

    private val estado = MutableStateFlow<Map<String, Long>>(emptyMap())
    val flow: StateFlow<Map<String, Long>> = estado
    val limites: Map<String, Long> get() = estado.value

    private var uidActual: String? = null
    private var registro: ListenerRegistration? = null

    fun documento(uid: String): DocumentReference =
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("preferencias").document("presupuestos")

    /** Lee el mapa "categorias" de un documento de Firestore. */
    fun leer(datos: Map<String, Any>?): Map<String, Long> =
        (datos?.get("categorias") as? Map<*, *>).orEmpty()
            .mapNotNull { (clave, valor) ->
                val categoria = clave as? String ?: return@mapNotNull null
                val limite = (valor as? Number)?.toLong() ?: return@mapNotNull null
                categoria to limite
            }
            .toMap()

    fun escuchar(uid: String) {
        if (uid == uidActual && registro != null) return

        detener()
        uidActual = uid

        registro = documento(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("PresupuestoStore", "No se pudieron cargar los presupuestos", error)
                return@addSnapshotListener
            }
            estado.value = leer(snapshot?.data)
        }
    }

    fun detener() {
        registro?.remove()
        registro = null
        uidActual = null
        estado.value = emptyMap()
    }

    /** Reemplaza todos los límites (sin merge, para que borrar una categoría sí la quite). */
    fun guardar(limites: Map<String, Long>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = uidActual ?: return onError("No hay una sesión activa.")

        documento(uid)
            .set(
                mapOf(
                    "categorias" to limites.filterValues { it > 0 },
                    "fechaActualizacion" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "No se pudieron guardar los presupuestos.")
            }
    }
}

package com.gamora.feature.budget

import com.gamora.R
import com.gamora.core.texto
import com.gamora.core.Categoria
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
    val categoria: Categoria,
    val gastadoCentavos: Long,
    val limiteCentavos: Long
) {
    val porcentaje: Int get() = if (limiteCentavos > 0) (gastadoCentavos * 100 / limiteCentavos).toInt() else 0
    val restanteCentavos: Long get() = limiteCentavos - gastadoCentavos
}

/** Avance de cada categoría con límite, desde [inicioPeriodo]; primero las más cerca de pasarse. */
fun avancePresupuestos(
    movimientos: List<GamoraMovement>,
    limites: Map<Categoria, Long>,
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

/** Límites por categoría en users/{uid}/preferencias/presupuestos → { categorias: { "comida": 300000, … } } (claves de [Categoria]). */
object PresupuestoStore {

    private val estado = MutableStateFlow<Map<Categoria, Long>>(emptyMap())
    val flow: StateFlow<Map<Categoria, Long>> = estado
    val limites: Map<Categoria, Long> get() = estado.value

    private var uidActual: String? = null
    private var registro: ListenerRegistration? = null

    fun documento(uid: String): DocumentReference =
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("preferencias").document("presupuestos")

    /** Lee el mapa "categorias" de un documento de Firestore; las claves desconocidas se ignoran. */
    fun leer(datos: Map<String, Any>?): Map<Categoria, Long> =
        (datos?.get("categorias") as? Map<*, *>).orEmpty()
            .mapNotNull { (clave, valor) ->
                val categoria = Categoria.porClave(clave as? String) ?: return@mapNotNull null
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
    fun guardar(limites: Map<Categoria, Long>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = uidActual ?: return onError(texto(R.string.auth_repository_no_hay_una_sesion))

        documento(uid)
            .set(
                mapOf(
                    "categorias" to limites.filterValues { it > 0 }.mapKeys { it.key.clave },
                    "fechaActualizacion" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.presupuestos_no_se_pudieron_guardar))
            }
    }
}

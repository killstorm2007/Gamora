
package com.gamora.feature.transactions

import com.gamora.R
import com.gamora.core.texto
import com.gamora.core.Categoria
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.gamora.core.ui.Moneda
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class GamoraMovement(
    val description: String,
    val category: Categoria,
    val amountCents: Long,
    val isIncome: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    // Id del documento en Firestore; vacío si aún no se ha guardado.
    val id: String = ""
)

data class TotalesMes(val ingresos: Long, val gastos: Long) {
    val balance: Long get() = ingresos - gastos
}

/** Ingresos y gastos agrupados por mes. La clave es año * 12 + mes (0-11), así se ordena cronológicamente. */
fun List<GamoraMovement>.totalesPorMes(): Map<Int, TotalesMes> {
    val calendario = Calendar.getInstance()

    return groupBy { movimiento ->
        calendario.timeInMillis = movimiento.timestamp
        calendario.get(Calendar.YEAR) * 12 + calendario.get(Calendar.MONTH)
    }.mapValues { (_, delMes) ->
        TotalesMes(
            ingresos = delMes.filter { it.isIncome }.sumOf { it.amountCents },
            gastos = delMes.filterNot { it.isIncome }.sumOf { it.amountCents }
        )
    }
}

/** Gasto que en realidad es dinero apartado (p. ej. una aportación a una meta). */
val GamoraMovement.esAhorro: Boolean get() = !isIncome && category == Categoria.AHORRO

/**
 * Lo que sobró cada mes; un mes en negativo no resta lo ahorrado en otros meses. Incluye el mes en curso.
 * Lo apartado en la categoría "Ahorro" no cuenta como gasto: sigue siendo ahorro.
 */
fun List<GamoraMovement>.ahorroAcumulado(): Long =
    filterNot { it.esAhorro }.totalesPorMes().values.sumOf { maxOf(it.balance, 0L) }

/**
 * Movimientos del usuario en sesión, sincronizados con
 * Firestore en users/{uid}/movimientos.
 */
object GamoraTransactionStore {

    private val estado = MutableStateFlow<List<GamoraMovement>>(emptyList())

    val movementsFlow: StateFlow<List<GamoraMovement>> = estado

    val movements: List<GamoraMovement>
        get() = estado.value

    private var uidActual: String? = null
    private var registro: ListenerRegistration? = null

    private fun coleccion(uid: String): CollectionReference =
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("movimientos")

    fun escuchar(uid: String) {
        if (uid == uidActual && registro != null) return

        detener()
        uidActual = uid

        registro = coleccion(uid)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.w("GamoraTransactionStore", "No se pudieron cargar los movimientos", error)
                    return@addSnapshotListener
                }

                estado.value = snapshot?.documents.orEmpty().map { doc ->
                    GamoraMovement(
                        description = doc.getString("descripcion").orEmpty(),
                        category = Categoria.porClave(doc.getString("categoria")) ?: Categoria.OTROS,
                        amountCents = doc.getLong("montoCentavos") ?: 0L,
                        isIncome = doc.getBoolean("esIngreso") ?: false,
                        timestamp = doc.getLong("fecha") ?: 0L,
                        id = doc.id
                    )
                }
            }
    }

    fun detener() {
        registro?.remove()
        registro = null
        uidActual = null
        estado.value = emptyList()
    }

    /** Campos tal como se guardan en Firestore. */
    fun datos(movimiento: GamoraMovement): Map<String, Any> = mapOf(
        "descripcion" to movimiento.description,
        "categoria" to movimiento.category.clave,
        "montoCentavos" to movimiento.amountCents,
        "esIngreso" to movimiento.isIncome,
        "fecha" to movimiento.timestamp
    )

    /** Referencia para un movimiento nuevo; sirve para guardarlo dentro de un batch. */
    fun nuevaReferencia(): DocumentReference? = uidActual?.let { coleccion(it).document() }

    /** Crea el movimiento o, si ya tiene id, lo reemplaza. El listener refresca la lista al instante. */
    fun guardar(
        movimiento: GamoraMovement,
        onError: (String) -> Unit
    ) {
        val uid = uidActual ?: return onError(texto(R.string.auth_repository_no_hay_una_sesion))
        val coleccion = coleccion(uid)

        val tarea = if (movimiento.id.isBlank()) coleccion.add(datos(movimiento))
        else coleccion.document(movimiento.id).set(datos(movimiento))

        tarea.addOnFailureListener { error ->
            onError(error.localizedMessage ?: texto(R.string.gamora_movement_no_se_pudo_guardar))
        }
    }

    fun eliminar(
        id: String,
        onError: (String) -> Unit
    ) {
        val uid = uidActual ?: return onError(texto(R.string.auth_repository_no_hay_una_sesion))

        coleccion(uid).document(id).delete()
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.gamora_movement_no_se_pudo_eliminar))
            }
    }
}

/** Ejecuta [alCambiar] cada vez que cambia [flujo] (o la moneda) mientras la vista está visible. */
fun Fragment.observar(flujo: Flow<*>, alCambiar: (View) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(flujo, Moneda.flow) { _, _ -> Unit }.collect {
                view?.let(alCambiar)
            }
        }
    }
}

fun Fragment.observarMovimientos(alCambiar: (View) -> Unit) =
    observar(GamoraTransactionStore.movementsFlow, alCambiar)

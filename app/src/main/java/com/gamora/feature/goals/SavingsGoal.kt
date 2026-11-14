package com.gamora.feature.goals

import com.gamora.R
import com.gamora.core.texto
import com.gamora.core.Categoria
import com.gamora.feature.transactions.GamoraTransactionStore
import com.gamora.feature.transactions.GamoraMovement
import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SavingsGoal(
    val id: String,
    val name: String,
    val targetCents: Long,
    val savedCents: Long = 0L,
    val deadlineMillis: Long? = null
) {
    val activa: Boolean get() = savedCents < targetCents
}

/** Metas del usuario en sesión, sincronizadas con Firestore en users/{uid}/metas. */
object GamoraGoalStore {

    private val estado = MutableStateFlow<List<SavingsGoal>>(emptyList())

    val goalsFlow: StateFlow<List<SavingsGoal>> = estado

    val goals: List<SavingsGoal>
        get() = estado.value

    private var uidActual: String? = null
    private var registro: ListenerRegistration? = null

    private fun coleccion(uid: String): CollectionReference =
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("metas")

    fun escuchar(uid: String) {
        if (uid == uidActual && registro != null) return

        detener()
        uidActual = uid

        registro = coleccion(uid)
            .orderBy("fechaCreacion", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.w("GamoraGoalStore", "No se pudieron cargar las metas", error)
                    return@addSnapshotListener
                }

                estado.value = snapshot?.documents.orEmpty().map { doc ->
                    SavingsGoal(
                        id = doc.id,
                        name = doc.getString("nombre").orEmpty(),
                        targetCents = doc.getLong("objetivoCentavos") ?: 0L,
                        savedCents = doc.getLong("ahorradoCentavos") ?: 0L,
                        deadlineMillis = doc.getLong("fechaLimite")
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

    private fun conUid(onError: (String) -> Unit, accion: (String) -> Unit) {
        uidActual?.let(accion) ?: onError(texto(R.string.auth_repository_no_hay_una_sesion))
    }

    fun crear(
        nombre: String,
        objetivoCentavos: Long,
        fechaLimite: Long?,
        onError: (String) -> Unit
    ) = conUid(onError) { uid ->
        coleccion(uid)
            .add(
                mapOf(
                    "nombre" to nombre,
                    "objetivoCentavos" to objetivoCentavos,
                    "ahorradoCentavos" to 0L,
                    "fechaLimite" to fechaLimite,
                    "fechaCreacion" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener { onError(it.localizedMessage ?: texto(R.string.savings_goal_no_se_pudo_crear)) }
    }

    /**
     * Suma la aportación a la meta. Con [descontarDelSaldo] también registra un gasto
     * en la categoría "Ahorro"; ambas escrituras van en un batch: se guardan las dos o ninguna.
     * increment evita perder aportaciones si dos dispositivos aportan a la vez.
     */
    fun aportar(
        meta: SavingsGoal,
        montoCentavos: Long,
        descontarDelSaldo: Boolean,
        onError: (String) -> Unit
    ) = conUid(onError) { uid ->
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        batch.update(
            coleccion(uid).document(meta.id),
            "ahorradoCentavos", FieldValue.increment(montoCentavos)
        )

        if (descontarDelSaldo) {
            val gasto = GamoraTransactionStore.nuevaReferencia()
                ?: return@conUid onError(texto(R.string.auth_repository_no_hay_una_sesion))

            batch.set(
                gasto,
                GamoraTransactionStore.datos(
                    GamoraMovement(
                        description = texto(R.string.savings_goal_aportacion_a, meta.name),
                        category = Categoria.AHORRO,
                        amountCents = montoCentavos,
                        isIncome = false
                    )
                )
            )
        }

        batch.commit()
            .addOnFailureListener { onError(it.localizedMessage ?: texto(R.string.savings_goal_no_se_pudo_registrar)) }
    }

    fun eliminar(id: String, onError: (String) -> Unit) = conUid(onError) { uid ->
        coleccion(uid).document(id)
            .delete()
            .addOnFailureListener { onError(it.localizedMessage ?: texto(R.string.savings_goal_no_se_pudo_eliminar)) }
    }
}

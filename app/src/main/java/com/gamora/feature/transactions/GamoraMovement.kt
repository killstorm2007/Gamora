
package com.gamora.feature.transactions

import com.gamora.R
import com.gamora.core.texto
import com.gamora.core.Categoria
import android.content.Context
import com.gamora.core.EstadoDatos
import com.gamora.core.mensajeDeError
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
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

/**
 * Dinero que solo se mueve entre el saldo y las metas: aportar (gasto en "Ahorro") o retirar
 * (ingreso en "Ahorro"). No es consumo ni ingreso nuevo.
 */
val GamoraMovement.esAhorro: Boolean get() = category == Categoria.AHORRO

/**
 * Lo que sobró cada mes; un mes en negativo no resta lo ahorrado en otros meses. Incluye el mes en curso.
 * Lo apartado en la categoría "Ahorro" no cuenta como gasto: sigue siendo ahorro.
 */
fun List<GamoraMovement>.ahorroAcumulado(): Long =
    filterNot { it.esAhorro }.totalesPorMes().values.sumOf { maxOf(it.balance, 0L) }

/**
 * Movimientos del usuario en sesión, sincronizados con Firestore en users/{uid}/movimientos.
 *
 * Para no gastar la cuota de lecturas (cada documento que llega del servidor cuenta), solo los últimos
 * [MESES_EN_VIVO] meses se escuchan en vivo. El historial anterior casi no cambia: se lee del caché del
 * teléfono y se actualiza desde el servidor como mucho una vez por semana. Los cambios que se hacen
 * desde este teléfono a movimientos antiguos se reflejan al instante.
 */
object GamoraTransactionStore {

    /** Mes actual y los 12 anteriores: lo que muestran el inicio, el análisis y los presupuestos. */
    const val MESES_EN_VIVO = 13
    private const val ORIGEN = "movimientos"
    private const val SEMANA_MS = 7L * 24 * 60 * 60 * 1000

    private val estado = MutableStateFlow<List<GamoraMovement>>(emptyList())

    val movementsFlow: StateFlow<List<GamoraMovement>> = estado

    val movements: List<GamoraMovement>
        get() = estado.value

    private var uidActual: String? = null
    private var registro: ListenerRegistration? = null
    private var limite = 0L
    private var recientes: List<GamoraMovement> = emptyList()
    private var antiguos: List<GamoraMovement> = emptyList()

    private fun coleccion(uid: String): CollectionReference =
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("movimientos")

    private fun leer(doc: DocumentSnapshot) = GamoraMovement(
        description = doc.getString("descripcion").orEmpty(),
        category = Categoria.porClave(doc.getString("categoria")) ?: Categoria.OTROS,
        amountCents = doc.getLong("montoCentavos") ?: 0L,
        isIncome = doc.getBoolean("esIngreso") ?: false,
        timestamp = doc.getLong("fecha") ?: 0L,
        id = doc.id
    )

    /** Junta lo reciente y el historial (si un movimiento está en ambos, gana el reciente). */
    private fun publicar() {
        val ids = recientes.mapTo(HashSet()) { it.id }
        estado.value = (recientes + antiguos.filterNot { it.id in ids }).sortedByDescending { it.timestamp }
    }

    /** Primer día del mes, [MESES_EN_VIVO] - 1 meses atrás, a las 00:00. */
    fun inicioEnVivo(ahora: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = ahora
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, -(MESES_EN_VIVO - 1))
    }.timeInMillis

    /** Con [forzar] vuelve a conectar aunque ya esté escuchando (para "Reintentar" tras un error). */
    fun escuchar(context: Context, uid: String, forzar: Boolean = false) {
        if (!forzar && uid == uidActual && registro != null) return

        detener()
        uidActual = uid
        limite = inicioEnVivo()

        registro = coleccion(uid)
            .whereGreaterThanOrEqualTo("fecha", limite)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (uid != uidActual) return@addSnapshotListener

                if (error != null) {
                    EstadoDatos.reportar(ORIGEN, error)
                    return@addSnapshotListener
                }

                EstadoDatos.resuelto(ORIGEN)
                recientes = snapshot?.documents.orEmpty().map(::leer)
                publicar()
            }

        cargarHistorial(context.applicationContext, uid, forzar)
    }

    private fun cargarHistorial(context: Context, uid: String, forzar: Boolean) {
        val preferencias = context.getSharedPreferences("gamora_historial", Context.MODE_PRIVATE)
        val clave = "servidor_$uid"
        val ahora = System.currentTimeMillis()
        val delServidor = forzar || ahora - preferencias.getLong(clave, 0L) > SEMANA_MS

        coleccion(uid)
            .whereLessThan("fecha", limite)
            .get(if (delServidor) Source.DEFAULT else Source.CACHE)
            .addOnSuccessListener { resultado ->
                if (uid != uidActual) return@addOnSuccessListener
                antiguos = resultado.documents.map(::leer)
                publicar()
                if (delServidor && !resultado.metadata.isFromCache) preferencias.edit().putLong(clave, ahora).apply()
            }
            .addOnFailureListener { error ->
                if (uid != uidActual) return@addOnFailureListener
                // Si el caché no responde se intenta con el servidor; si el servidor falla, se avisa.
                if (delServidor) EstadoDatos.reportar(ORIGEN, error)
                else cargarHistorial(context, uid, forzar = true)
            }
    }

    fun detener() {
        registro?.remove()
        registro = null
        uidActual = null
        recientes = emptyList()
        antiguos = emptyList()
        estado.value = emptyList()
        EstadoDatos.resuelto(ORIGEN)
    }

    /**
     * Refleja en el historial lo escrito desde este teléfono. Lo reciente ya lo trae el listener; lo
     * anterior a [MESES_EN_VIVO] meses no, porque no se escucha en vivo.
     */
    fun registrarLocal(escritos: List<GamoraMovement>, eliminados: Set<String> = emptySet()) {
        if (escritos.isEmpty() && eliminados.isEmpty()) return
        val porId = escritos.associateBy { it.id }
        antiguos = antiguos.filterNot { it.id in porId || it.id in eliminados } +
                escritos.filter { it.timestamp < limite }
        publicar()
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
        val referencia = if (movimiento.id.isBlank()) coleccion(uid).document() else coleccion(uid).document(movimiento.id)

        referencia.set(datos(movimiento)).addOnFailureListener { error ->
            onError(mensajeDeError(error, R.string.gamora_movement_no_se_pudo_guardar))
        }
        registrarLocal(listOf(movimiento.copy(id = referencia.id)))
    }

    fun eliminar(
        id: String,
        onError: (String) -> Unit
    ) {
        val uid = uidActual ?: return onError(texto(R.string.auth_repository_no_hay_una_sesion))

        coleccion(uid).document(id).delete()
            .addOnFailureListener { error ->
                onError(mensajeDeError(error, R.string.gamora_movement_no_se_pudo_eliminar))
            }
        registrarLocal(emptyList(), eliminados = setOf(id))
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

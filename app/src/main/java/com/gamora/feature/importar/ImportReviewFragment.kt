package com.gamora.feature.importar

import com.gamora.core.textoPlural
import com.gamora.core.texto
import com.gamora.core.nombre
import android.os.Bundle
import android.util.Log
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.gamora.R
import com.gamora.core.ui.dinero
import com.gamora.core.ui.dp
import com.gamora.feature.transactions.GamoraTransactionStore
import com.gamora.feature.transactions.fechaCorta
import com.gamora.feature.transactions.mostrarDialogoMovimiento
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

/** Tabla con lo leído de un documento; solo se guarda lo que la persona deja marcado. */
class ImportReviewFragment : Fragment(R.layout.fragment_import_review) {

    private lateinit var tabla: LinearLayout
    private lateinit var resumenTexto: TextView
    private lateinit var botonGuardar: MaterialButton
    private lateinit var casillaTodos: MaterialCheckBox

    private var guardando = false

    private val filas: List<MovimientoImportado>
        get() = ImportacionEnCurso.resultado?.movimientos.orEmpty()

    private fun actualizarFilas(nuevas: List<MovimientoImportado>) {
        ImportacionEnCurso.resultado = ImportacionEnCurso.resultado?.copy(movimientos = nuevas)
        dibujar()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val resultado = ImportacionEnCurso.resultado
        if (resultado == null) {
            // P. ej. Android cerró la app mientras estaba en segundo plano.
            Toast.makeText(requireContext(), texto(R.string.import_review_la_revision_se_perdio), Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
            return
        }

        tabla = view.findViewById(R.id.tablaMovimientos)
        resumenTexto = view.findViewById(R.id.txtResumenRevision)
        botonGuardar = view.findViewById(R.id.btnGuardarRevision)
        casillaTodos = view.findViewById(R.id.cbTodos)

        view.findViewById<TextView>(R.id.txtOrigenRevision).text =
            listOf(resultado.banco, resultado.periodo, "${resultado.movimientos.size} encontrados")
                .filter { it.isNotBlank() }
                .joinToString(" · ")

        casillaTodos.setOnClickListener {
            val marcar = casillaTodos.isChecked
            actualizarFilas(filas.map { it.copy(seleccionado = marcar) })
        }

        botonGuardar.setOnClickListener { guardar() }
        view.findViewById<View>(R.id.btnCancelarRevision).setOnClickListener { salir() }
        view.findViewById<View>(R.id.btnVolverRevision).setOnClickListener { salir() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = salir()
        })

        dibujar()
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)

    private fun dibujar() {
        tabla.removeAllViews()

        filas.forEachIndexed { indice, fila ->
            tabla.addView(crearFila(indice, fila))
        }

        val resumen = resumen(filas)
        resumenTexto.text = texto(R.string.import_review_seleccionados_ingresos_gastos, resumen.seleccionados, dinero(resumen.ingresosCentavos), dinero(resumen.gastosCentavos))

        botonGuardar.text = textoPlural(R.plurals.import_review_guardar_movimientos, resumen.seleccionados, resumen.seleccionados)
        botonGuardar.isEnabled = resumen.seleccionados > 0 && !guardando
        casillaTodos.isChecked = filas.isNotEmpty() && filas.all { it.seleccionado }
        casillaTodos.isEnabled = !guardando
    }

    private fun celda(texto: String, peso: Float, colorTexto: Int, alinearFinal: Boolean = false) =
        TextView(requireContext()).apply {
            text = texto
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(colorTexto)
            setPadding(dp(4), 0, 0, 0)
            gravity = if (alinearFinal) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, peso)
        }

    private fun crearFila(indice: Int, fila: MovimientoImportado): View {
        val principal = color(R.color.gamora_text_primary)
        val secundario = color(R.color.gamora_text_secondary)

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(4), dp(2), dp(10), dp(2))
            setBackgroundColor(color(if (indice % 2 == 0) R.color.gamora_surface else R.color.gamora_background))
            alpha = if (fila.seleccionado) 1f else 0.6f

            addView(MaterialCheckBox(context).apply {
                isChecked = fila.seleccionado
                isEnabled = !guardando
                minWidth = 0
                minimumWidth = 0
                contentDescription = texto(R.string.import_review_incluir, fila.descripcion)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
                setOnClickListener {
                    actualizarFilas(filas.toMutableList().also { it[indice] = fila.copy(seleccionado = isChecked) })
                }
            })

            addView(celda(fechaCorta(fila.fecha), 1f, secundario))
            addView(celda(
                if (fila.repetido) texto(R.string.import_review_repetido, fila.descripcion) else fila.descripcion,
                2.4f,
                if (fila.repetido) color(R.color.gamora_warning) else principal
            ))
            addView(celda(fila.categoria.nombre(requireContext()), 1.4f, secundario))
            addView(celda(
                (if (fila.esIngreso) "+" else "-") + dinero(fila.montoCentavos),
                1.6f,
                color(if (fila.esIngreso) R.color.gamora_income else R.color.gamora_expense),
                alinearFinal = true
            ))

            isClickable = !guardando
            isFocusable = !guardando
            setOnClickListener { if (!guardando) opciones(indice, fila) }
        }
    }

    /** Gemini puede confundir un abono con un cargo: aquí se corrige además de editar. */
    private fun opciones(indice: Int, fila: MovimientoImportado) {
        val cambio = if (fila.esIngreso) texto(R.string.import_review_cambiar_a_gasto) else texto(R.string.import_review_cambiar_a_ingreso)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(fila.descripcion)
            .setItems(arrayOf(texto(R.string.analytics_editar), cambio)) { _, opcion ->
                if (opcion == 0) {
                    editar(indice, fila)
                } else if (indice in filas.indices) {
                    val esIngreso = !fila.esIngreso
                    actualizarFilas(filas.toMutableList().also {
                        it[indice] = fila.copy(
                            esIngreso = esIngreso,
                            categoria = categoriaValida(fila.categoria, esIngreso),
                            seleccionado = true
                        )
                    })
                }
            }
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .show()
    }

    /** Usa el formulario de siempre, pero sin guardar en Firestore. */
    private fun editar(indice: Int, fila: MovimientoImportado) {
        mostrarDialogoMovimiento(fila.esIngreso, fila.aMovimiento(), alGuardarLocal = { editado ->
            // La revisión pudo cerrarse mientras el formulario seguía abierto.
            if (!isAdded || guardando || indice !in filas.indices) return@mostrarDialogoMovimiento
            actualizarFilas(filas.toMutableList().also {
                it[indice] = fila.copy(
                    descripcion = editado.description,
                    categoria = editado.category,
                    montoCentavos = editado.amountCents,
                    fecha = editado.timestamp,
                    // Al corregirla a mano, la persona ya decidió que la quiere.
                    seleccionado = true
                )
            })
        })
    }

    private fun salir() {
        if (guardando) return

        if (resumen(filas).seleccionados == 0) {
            terminar()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(texto(R.string.import_review_descartar_estos_movimientos))
            .setMessage(texto(R.string.import_review_no_se_guardara_ninguno))
            .setNegativeButton(texto(R.string.import_review_seguir_revisando), null)
            .setPositiveButton(texto(R.string.import_review_descartar)) { _, _ -> terminar() }
            .show()
    }

    private fun terminar() {
        ImportacionEnCurso.limpiar()
        findNavController().popBackStack()
    }

    private fun guardar() {
        val elegidos = filas.filter { it.seleccionado }
        if (elegidos.isEmpty() || guardando) return

        guardando = true
        botonGuardar.isEnabled = false
        botonGuardar.text = texto(R.string.import_review_guardando)

        try {
            val db = FirebaseFirestore.getInstance()
            val app = requireContext().applicationContext

            // Como el resto de la app: Firestore aplica los cambios al instante (también sin internet)
            // y los sube cuando hay conexión. Esperar al servidor dejaría la pantalla colgada sin red.
            elegidos.enLotes().forEach { lote ->
                val batch = db.batch()
                lote.forEach { fila ->
                    val referencia = GamoraTransactionStore.nuevaReferencia()
                        ?: error(texto(R.string.auth_repository_no_hay_una_sesion))
                    batch.set(referencia, GamoraTransactionStore.datos(fila.aMovimiento()))
                }
                batch.commit().addOnFailureListener { error ->
                    Log.w("ImportReviewFragment", "Lote rechazado: ${error.javaClass.simpleName}")
                    Toast.makeText(app, texto(R.string.import_review_algunos_movimientos_no_se), Toast.LENGTH_LONG).show()
                }
            }

            Toast.makeText(
                app,
                textoPlural(R.plurals.import_review_se_registraron_movimientos, elegidos.size, elegidos.size),
                Toast.LENGTH_LONG
            ).show()
            terminar()
        } catch (error: Exception) {
            guardando = false
            dibujar()
            Toast.makeText(
                requireContext(),
                error.localizedMessage ?: texto(R.string.import_review_no_se_pudieron_guardar),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

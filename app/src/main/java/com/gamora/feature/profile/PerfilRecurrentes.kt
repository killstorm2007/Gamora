package com.gamora.feature.profile

import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gamora.R
import com.gamora.core.Categoria
import com.gamora.core.auth.AuthRepository
import com.gamora.core.mensajeDeError
import com.gamora.core.nombre
import com.gamora.core.texto
import com.gamora.core.ui.IconoDialogo
import com.gamora.core.ui.centavosDe
import com.gamora.core.ui.dialogo
import com.gamora.core.ui.dinero
import com.gamora.core.ui.dp
import com.gamora.core.ui.enCampo
import com.gamora.feature.transactions.MovimientoRecurrente
import com.gamora.feature.transactions.Recurrentes
import com.google.android.material.textfield.TextInputEditText
import java.math.BigDecimal
import kotlinx.coroutines.launch

// Perfil › Movimientos recurrentes: ver, editar y dejar de repetir.

internal fun ProfileFragment.configurarRecurrentes(view: View) {
    view.findViewById<View>(R.id.btnRecurrentes).setOnClickListener {
        val uid = AuthRepository.obtenerUid() ?: return@setOnClickListener

        viewLifecycleOwner.lifecycleScope.launch {
            val lista = try {
                Recurrentes.listar(uid)
            } catch (error: Exception) {
                mostrarToast(mensajeDeError(error, R.string.profile_no_se_pudieron_cargar))
                return@launch
            }
            if (!vistaActiva()) return@launch

            if (lista.isEmpty()) {
                dialogo(requireContext(), IconoDialogo.REPETIR)
                    .setTitle(texto(R.string.profile_movimientos_recurrentes_2))
                    .setMessage(
                        texto(R.string.profile_aun_no_tienes_ninguno)
                    )
                    .setPositiveButton(texto(R.string.profile_entendido), null)
                    .show()
                return@launch
            }

            val etiquetas = lista.map { r ->
                val signo = if (r.esIngreso) "+" else "−"
                texto(R.string.profile_ndia_de_cada_mes, r.descripcion, signo, dinero(r.montoCentavos), r.dia, r.categoria.nombre(requireContext()))
            }

            dialogo(requireContext(), IconoDialogo.REPETIR)
                .setTitle(texto(R.string.profile_movimientos_recurrentes_2))
                .setItems(etiquetas.toTypedArray()) { _, indice ->
                    opcionesRecurrente(uid, lista[indice])
                }
                .setPositiveButton(texto(R.string.main_cerrar), null)
                .show()
        }
    }
}

internal fun ProfileFragment.opcionesRecurrente(uid: String, recurrente: MovimientoRecurrente) {
    dialogo(requireContext(), IconoDialogo.REPETIR)
        .setTitle(recurrente.descripcion)
        .setItems(arrayOf(texto(R.string.analytics_editar), texto(R.string.profile_dejar_de_repetir))) { _, opcion ->
            if (opcion == 0) editarRecurrente(uid, recurrente) else confirmarFinRecurrente(uid, recurrente)
        }
        .setNegativeButton(texto(R.string.main_cerrar), null)
        .show()
}

/** Lo que se registrará de ahora en adelante; lo ya registrado no cambia. */
internal fun ProfileFragment.editarRecurrente(uid: String, recurrente: MovimientoRecurrente) {
    val contexto = requireContext()
    val categorias = if (recurrente.esIngreso) Categoria.DE_INGRESO else Categoria.DE_GASTO

    val descripcion = TextInputEditText(contexto).apply {
        hint = texto(R.string.import_review_descripcion)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        maxLines = 1
        filters = arrayOf(InputFilter.LengthFilter(200))
        setText(recurrente.descripcion)
    }
    val monto = TextInputEditText(contexto).apply {
        hint = texto(R.string.import_review_monto)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        maxLines = 1
        setText(BigDecimal.valueOf(recurrente.montoCentavos, 2).toPlainString())
    }
    val dia = TextInputEditText(contexto).apply {
        hint = texto(R.string.profile_recurrente_dia)
        inputType = InputType.TYPE_CLASS_NUMBER
        maxLines = 1
        filters = arrayOf(InputFilter.LengthFilter(2))
        setText(recurrente.dia.toString())
    }
    val categoria = Spinner(contexto).apply {
        adapter = ArrayAdapter(contexto, android.R.layout.simple_spinner_item, categorias.map { it.nombre(contexto) })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        contentDescription = texto(R.string.import_review_categoria)
        categorias.indexOf(recurrente.categoria).takeIf { it >= 0 }?.let(::setSelection)
    }

    val formulario = LinearLayout(contexto).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(4), dp(24), 0)
        addView(descripcion.enCampo())
        addView(monto.enCampo())
        addView(dia.enCampo())
        addView(categoria)
        addView(TextView(contexto).apply {
            text = texto(R.string.profile_recurrente_solo_futuro)
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
            setTextColor(ContextCompat.getColor(contexto, R.color.gamora_text_secondary))
        })
    }

    val dialogo = dialogo(contexto, IconoDialogo.EDITAR)
        .setTitle(texto(R.string.profile_recurrente_editar_titulo))
        .setView(ScrollView(contexto).apply { addView(formulario) })
        .setNegativeButton(texto(R.string.import_review_cancelar), null)
        .setPositiveButton(texto(R.string.import_review_guardar), null)
        .create()

    dialogo.setOnShowListener {
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val textoDescripcion = descripcion.text?.toString()?.trim().orEmpty()
            val centavos = centavosDe(monto.text?.toString().orEmpty())?.takeIf { it in 1..100_000_000_000L }
            val numeroDia = dia.text?.toString()?.toIntOrNull()?.takeIf { it in 1..28 }

            when {
                textoDescripcion.isEmpty() -> { descripcion.error = texto(R.string.movement_dialog_escribe_una_descripcion); return@setOnClickListener }
                centavos == null -> { monto.error = texto(R.string.analytics_importe_no_valido); return@setOnClickListener }
                numeroDia == null -> { dia.error = texto(R.string.profile_recurrente_dia_invalido); return@setOnClickListener }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    Recurrentes.editar(uid, recurrente, textoDescripcion, categorias[categoria.selectedItemPosition], centavos!!, numeroDia!!)
                    dialogo.dismiss()
                    mostrarToast(texto(R.string.movement_dialog_cambios_guardados))
                } catch (error: Exception) {
                    mostrarToast(mensajeDeError(error, R.string.profile_no_se_pudo_actualizar))
                }
            }
        }
    }
    dialogo.show()
}

internal fun ProfileFragment.confirmarFinRecurrente(uid: String, recurrente: MovimientoRecurrente) {
    dialogo(requireContext(), IconoDialogo.ELIMINAR)
        .setTitle(texto(R.string.profile_dejar_de_repetir))
        .setMessage(
            texto(R.string.profile_ya_no_se_registrara, recurrente.descripcion)
        )
        .setNegativeButton(texto(R.string.import_review_cancelar), null)
        .setPositiveButton(texto(R.string.profile_dejar_de_repetir)) { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    Recurrentes.eliminar(uid, recurrente.id)
                    mostrarToast(texto(R.string.profile_ya_no_se_repetira))
                } catch (error: Exception) {
                    mostrarToast(mensajeDeError(error, R.string.profile_no_se_pudo_actualizar))
                }
            }
        }
        .show()
}

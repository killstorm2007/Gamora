package com.gamora.core.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.EditText
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.gamora.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputLayout

/** Envuelve el campo en un TextInputLayout con contorno; el hint del campo pasa a ser la etiqueta. */
fun EditText.enCampo(): TextInputLayout =
    TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
        val radio = 14 * resources.displayMetrics.density
        setBoxCornerRadii(radio, radio, radio, radio)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (12 * resources.displayMetrics.density).toInt() }
        addView(this@enCampo)
    }

/** Para diálogos con un solo campo: el campo con los márgenes laterales del diálogo. */
fun EditText.enDialogo(): LinearLayout = LinearLayout(context).apply {
    val dp = resources.displayMetrics.density
    setPadding((24 * dp).toInt(), (4 * dp).toInt(), (24 * dp).toInt(), 0)
    addView(enCampo())
}

/** Ícono del encabezado de un diálogo: dibujo, color del ícono y del círculo, y si la acción es peligrosa. */
enum class IconoDialogo(
    @DrawableRes val dibujo: Int,
    @ColorRes val color: Int,
    @ColorRes val fondo: Int,
    val peligro: Boolean = false
) {
    ELIMINAR(R.drawable.ic_dialogo_eliminar, R.color.gamora_expense, R.color.gamora_expense_container, peligro = true),
    CANDADO(R.drawable.ic_dialogo_candado, R.color.gamora_accent, R.color.gamora_accent_light),
    DOCUMENTO(R.drawable.ic_dialogo_documento, R.color.gamora_accent, R.color.gamora_accent_light),
    EXPORTAR(R.drawable.ic_dialogo_exportar, R.color.gamora_accent, R.color.gamora_accent_light),
    EDITAR(R.drawable.ic_dialogo_editar, R.color.gamora_accent, R.color.gamora_accent_light),
    AGREGAR(R.drawable.ic_dialogo_agregar, R.color.gamora_accent, R.color.gamora_accent_light),
    SALIR(R.drawable.ic_dialogo_salir, R.color.gamora_accent, R.color.gamora_accent_light),
    IDIOMA(R.drawable.ic_dialogo_idioma, R.color.gamora_accent, R.color.gamora_accent_light),
    AYUDA(R.drawable.ic_dialogo_ayuda, R.color.gamora_accent, R.color.gamora_accent_light),
    NOTIFICACIONES(R.drawable.ic_dialogo_notificaciones, R.color.gamora_accent, R.color.gamora_accent_light),
    REPETIR(R.drawable.ic_dialogo_repetir, R.color.gamora_accent, R.color.gamora_accent_light),
    GRAFICA(R.drawable.ic_analytics, R.color.gamora_accent, R.color.gamora_accent_light),
    AHORRO(R.drawable.ic_goals, R.color.gamora_accent, R.color.gamora_accent_light),
    MOVIMIENTO(R.drawable.ic_movements, R.color.gamora_accent, R.color.gamora_accent_light),
    PERFIL(R.drawable.ic_profile, R.color.gamora_accent, R.color.gamora_accent_light),
    IA(R.drawable.ic_sparkle, R.color.gamora_accent, R.color.gamora_accent_light),
    BIENVENIDA(R.drawable.ic_home, R.color.gamora_accent, R.color.gamora_accent_light)
}

/**
 * Diálogo con el estilo de Gamora. Con [icono]: ícono en un círculo de color arriba y título centrado;
 * si la acción es peligrosa (eliminar), el botón principal va en rojo.
 */
fun dialogo(context: Context, icono: IconoDialogo? = null): MaterialAlertDialogBuilder {
    val tema = when {
        icono == null -> R.style.ThemeOverlay_Gamora_Dialog
        icono.peligro -> R.style.ThemeOverlay_Gamora_Dialog_Peligro
        else -> R.style.ThemeOverlay_Gamora_Dialog_Icono
    }
    val constructor = MaterialAlertDialogBuilder(context, tema)
    if (icono != null) constructor.setIcon(iconoEnCirculo(context, icono))
    return constructor
}

/** Círculo de 48 dp con el ícono de 24 dp al centro. */
private fun iconoEnCirculo(context: Context, icono: IconoDialogo): LayerDrawable {
    val dp = context.resources.displayMetrics.density
    val circulo = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ContextCompat.getColor(context, icono.fondo))
    }
    val dibujo = ContextCompat.getDrawable(context, icono.dibujo)!!.mutate().apply {
        setTint(ContextCompat.getColor(context, icono.color))
    }
    return LayerDrawable(arrayOf(circulo, dibujo)).apply {
        val lado = (48 * dp).toInt()
        val margen = (12 * dp).toInt()
        setLayerSize(0, lado, lado)
        setLayerInset(1, margen, margen, margen, margen)
    }
}

/**
 * Contenido de un diálogo con varios campos: el [mensaje] va dentro del mismo desplazamiento que los
 * campos y con los márgenes del diálogo (24 dp). Así los botones Guardar/Cancelar siempre quedan a la
 * vista, aunque la pantalla sea chica o el teclado esté abierto. Se usa en lugar de setMessage().
 */
fun contenidoDesplazable(context: Context, mensaje: CharSequence, campos: LinearLayout): android.widget.ScrollView {
    val dp = context.resources.displayMetrics.density
    campos.setPadding(0, 0, 0, 0)
    val columna = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * dp).toInt(), (4 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        addView(android.widget.TextView(context).apply {
            text = mensaje
            textSize = 15f
            setLineSpacing(0f, 1.25f)
            setTextColor(ContextCompat.getColor(context, R.color.gamora_text_secondary))
        })
        addView(campos)
    }
    return android.widget.ScrollView(context).apply { addView(columna) }
}

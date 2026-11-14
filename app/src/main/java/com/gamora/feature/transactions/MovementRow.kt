package com.gamora.feature.transactions

import com.gamora.core.texto
import com.gamora.core.nombre
import com.gamora.core.ui.dp
import com.gamora.core.ui.dinero
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gamora.R
import java.text.NumberFormat
import java.util.Locale

/** Fila de un movimiento: ícono, descripción, categoría (y detalle opcional) y monto. */
fun crearFilaMovimiento(
    context: Context,
    movimiento: GamoraMovement,
    detalle: String? = null,
    alTocar: (() -> Unit)? = null
): View {
    fun color(id: Int) = ContextCompat.getColor(context, id)

    val colorMonto = color(if (movimiento.isIncome) R.color.gamora_income else R.color.gamora_expense)
    val fondoIcono = color(
        if (movimiento.isIncome) R.color.gamora_income_container else R.color.gamora_expense_container
    )

    val fila = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(14), context.dp(12), context.dp(16), context.dp(12))
        setBackgroundResource(R.drawable.bg_dashboard_card)
        alTocar?.let { accion ->
            isClickable = true
            isFocusable = true
            val ripple = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            foreground = ContextCompat.getDrawable(context, ripple.resourceId)
            setOnClickListener { accion() }
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(10) }
        contentDescription = "${if (movimiento.isIncome) "Ingreso" else "Gasto"}: " +
                "${movimiento.description}, ${movimiento.category.nombre(context)}, ${dinero(movimiento.amountCents)}"
    }

    val icono = TextView(context).apply {
        text = movimiento.category.emoji
        textSize = 20f
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bg_icon_circle)
        backgroundTintList = ColorStateList.valueOf(fondoIcono)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    val textos = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(14), 0, context.dp(8), 0)

        addView(TextView(context).apply {
            text = movimiento.description
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.gamora_text_primary))
        })

        addView(TextView(context).apply {
            text = listOfNotNull(movimiento.category.nombre(context), detalle).joinToString(" · ")
            textSize = 12f
            setPadding(0, context.dp(2), 0, 0)
            setTextColor(color(R.color.gamora_text_secondary))
        })
    }

    val monto = TextView(context).apply {
        text = texto(
            if (movimiento.isIncome) R.string.movimiento_monto_ingreso else R.string.movimiento_monto_gasto,
            dinero(movimiento.amountCents)
        )
        textSize = 15f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(colorMonto)
    }

    fila.addView(icono, LinearLayout.LayoutParams(context.dp(44), context.dp(44)))
    fila.addView(textos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    fila.addView(monto)
    return fila
}

/** Mensaje para listas vacías, con un ícono grande. */
fun crearEstadoVacio(context: Context, icono: String, mensaje: String): View {

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(24), context.dp(28), context.dp(24), context.dp(28))
        setBackgroundResource(R.drawable.bg_dashboard_card)

        addView(TextView(context).apply {
            text = icono
            textSize = 34f
            gravity = Gravity.CENTER
        })

        addView(TextView(context).apply {
            text = mensaje
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, context.dp(10), 0, 0)
            setTextColor(ContextCompat.getColor(context, R.color.gamora_text_secondary))
        })
    }
}

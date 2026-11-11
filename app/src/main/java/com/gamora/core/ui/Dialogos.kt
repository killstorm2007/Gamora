package com.gamora.core.ui

import android.widget.EditText
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

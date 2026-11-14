package com.gamora.core

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Textos de strings.xml desde cualquier parte (también lógica sin Context, como los consejos).
 * La app instala el lector en [com.gamora.GamoraApp]; las pruebas instalan uno que lee strings.xml.
 */
object Textos {
    var texto: (id: Int, args: Array<out Any>) -> String = { _, _ -> error("Textos sin instalar") }
    var plural: (id: Int, cantidad: Int, args: Array<out Any>) -> String = { _, _, _ -> error("Textos sin instalar") }
}

fun texto(@StringRes id: Int, vararg args: Any): String = Textos.texto(id, args)

fun textoPlural(@PluralsRes id: Int, cantidad: Int, vararg args: Any): String = Textos.plural(id, cantidad, args)

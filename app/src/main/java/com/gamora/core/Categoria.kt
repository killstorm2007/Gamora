package com.gamora.core

import android.content.Context
import androidx.annotation.StringRes
import com.gamora.R
import com.gamora.core.ui.paraBuscar

/**
 * Categorías de movimientos. En Firestore se guarda [clave] (igual en todos los idiomas);
 * en pantalla se muestra [nombre], que sale de strings.xml.
 */
enum class Categoria(
    val clave: String,
    @StringRes val nombre: Int,
    val emoji: String,
    val ingreso: Boolean,
    val gasto: Boolean,
    // Nombre en español: para pedirle y entenderle a Gemini; en pantalla se usa [nombre].
    val nombreEs: String
) {
    SALARIO("salario", R.string.categoria_salario, "💼", true, false, "Salario"),
    VENTAS("ventas", R.string.categoria_ventas, "🏷️", true, false, "Ventas"),
    NEGOCIO("negocio", R.string.categoria_negocio, "🏪", true, false, "Negocio"),
    INVERSIONES("inversiones", R.string.categoria_inversiones, "📈", true, false, "Inversiones"),
    REGALO("regalo", R.string.categoria_regalo, "🎁", true, false, "Regalo"),
    COMIDA("comida", R.string.categoria_comida, "🍽️", false, true, "Comida"),
    TRANSPORTE("transporte", R.string.categoria_transporte, "🚗", false, true, "Transporte"),
    VIVIENDA("vivienda", R.string.categoria_vivienda, "🏠", false, true, "Vivienda"),
    SERVICIOS("servicios", R.string.categoria_servicios, "💡", false, true, "Servicios"),
    SALUD("salud", R.string.categoria_salud, "🩺", false, true, "Salud"),
    EDUCACION("educacion", R.string.categoria_educacion, "🎓", false, true, "Educación"),
    ENTRETENIMIENTO("entretenimiento", R.string.categoria_entretenimiento, "🎬", false, true, "Entretenimiento"),
    COMPRAS("compras", R.string.categoria_compras, "🛍️", false, true, "Compras"),
    AHORRO("ahorro", R.string.categoria_ahorro, "🐷", false, true, "Ahorro"),
    OTROS("otros", R.string.categoria_otros, "💸", true, true, "Otros");

    companion object {
        /** Categorías de ingreso, en el orden del selector ("Otros" al final). */
        val DE_INGRESO: List<Categoria> = entries.filter { it.ingreso && it != OTROS } + OTROS

        /** Categorías de gasto, en el orden del selector ("Otros" al final). */
        val DE_GASTO: List<Categoria> = entries.filter { it.gasto && it != OTROS } + OTROS

        /** Todas, sin repetir, para los filtros (primero las de ingreso). */
        val TODAS: List<Categoria> = (DE_INGRESO + DE_GASTO).distinct()

        /** La categoría guardada con esa clave; null si no es una clave conocida. */
        fun porClave(clave: String?): Categoria? = entries.firstOrNull { it.clave == clave }

        /** Clave o nombre en español (sin importar mayúsculas ni acentos); si no se reconoce, [OTROS]. */
        fun de(texto: String?): Categoria {
            val buscado = texto?.trim()?.paraBuscar().orEmpty()
            if (buscado.isEmpty()) return OTROS
            return entries.firstOrNull { it.clave == buscado || it.nombreEs.paraBuscar() == buscado } ?: OTROS
        }
    }
}

fun Categoria.nombre(context: Context): String = context.getString(nombre)

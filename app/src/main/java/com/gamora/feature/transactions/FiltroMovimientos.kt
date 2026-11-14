package com.gamora.feature.transactions

import com.gamora.core.Categoria
import com.gamora.core.ui.paraBuscar
import java.math.BigDecimal

/**
 * Movimientos que coinciden con la búsqueda (descripción, nombre de la categoría o importe; sin acentos)
 * y con [categoria] (null = todas). [nombre] da el nombre de la categoría tal como se muestra.
 */
fun filtrarMovimientos(
    movimientos: List<GamoraMovement>,
    busqueda: String,
    categoria: Categoria?,
    nombre: (Categoria) -> String
): List<GamoraMovement> {
    val buscado = busqueda.paraBuscar()
    return movimientos.filter { movimiento ->
        val coincideBusqueda = buscado.isEmpty() ||
                movimiento.description.paraBuscar().contains(buscado) ||
                nombre(movimiento.category).paraBuscar().contains(buscado) ||
                BigDecimal.valueOf(movimiento.amountCents, 2).toPlainString().startsWith(buscado)
        coincideBusqueda && (categoria == null || movimiento.category == categoria)
    }
}

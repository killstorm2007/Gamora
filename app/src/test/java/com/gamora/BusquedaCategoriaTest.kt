package com.gamora

import com.gamora.core.Categoria
import com.gamora.feature.transactions.GamoraMovement
import com.gamora.feature.transactions.filtrarMovimientos
import org.junit.Assert.assertEquals
import org.junit.Test

class BusquedaCategoriaTest {

    private val colegiatura = GamoraMovement("Colegiatura", Categoria.EDUCACION, 100, false)
    private val tacos = GamoraMovement("Tacos", Categoria.COMIDA, 250_00, false)
    private val nombre: (Categoria) -> String = { if (it == Categoria.EDUCACION) "Educación" else "Comida" }

    @Test
    fun buscaPorNombreSinAcento() {
        assertEquals(listOf(colegiatura), filtrarMovimientos(listOf(colegiatura, tacos), "educacion", null, nombre))
    }

    @Test
    fun filtraPorCategoriaYPorImporte() {
        assertEquals(listOf(tacos), filtrarMovimientos(listOf(colegiatura, tacos), "", Categoria.COMIDA, nombre))
        assertEquals(listOf(tacos), filtrarMovimientos(listOf(colegiatura, tacos), "250", null, nombre))
    }
}

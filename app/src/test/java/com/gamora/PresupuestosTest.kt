package com.gamora

import com.gamora.core.Categoria
import com.gamora.core.ui.paraBuscar
import com.gamora.feature.budget.PresupuestoStore
import com.gamora.feature.budget.avancePresupuestos
import com.gamora.feature.transactions.GamoraMovement
import org.junit.Assert.assertEquals
import org.junit.Test

class PresupuestosTest {

    private fun gasto(categoria: Categoria, centavos: Long, fecha: Long = 2_000L) =
        GamoraMovement("x", categoria, centavos, false, fecha)

    @Test
    fun soloCuentaGastosDelPeriodoYOrdenaPorUso() {
        val movimientos = listOf(
            gasto(Categoria.COMIDA, 90_00),
            gasto(Categoria.COMIDA, 50_00, fecha = 500L),          // antes del periodo
            gasto(Categoria.TRANSPORTE, 20_00),
            GamoraMovement("Nómina", Categoria.COMIDA, 999_00, true, 2_000L)  // ingreso, no cuenta
        )

        val avances = avancePresupuestos(
            movimientos,
            mapOf(Categoria.COMIDA to 100_00L, Categoria.TRANSPORTE to 100_00L, Categoria.SALUD to 0L),
            inicioPeriodo = 1_000L
        )

        assertEquals(listOf(Categoria.COMIDA, Categoria.TRANSPORTE), avances.map { it.categoria })
        assertEquals(90, avances[0].porcentaje)
        assertEquals(10_00L, avances[0].restanteCentavos)
    }

    @Test
    fun limitesIgnoranClavesDesconocidas() {
        val datos = mapOf<String, Any>("categorias" to mapOf("comida" to 300000L, "xyz" to 5000L, "Comida" to 1L))
        assertEquals(mapOf(Categoria.COMIDA to 300000L), PresupuestoStore.leer(datos))
    }

    @Test
    fun laBusquedaIgnoraAcentosYMayusculas() {
        assertEquals("cafe educacion", "  Café EDUCACIÓN ".paraBuscar())
    }
}

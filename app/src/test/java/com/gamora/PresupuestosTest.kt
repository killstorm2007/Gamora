package com.gamora

import com.gamora.core.ui.paraBuscar
import com.gamora.feature.budget.avancePresupuestos
import com.gamora.feature.transactions.GamoraMovement
import org.junit.Assert.assertEquals
import org.junit.Test

class PresupuestosTest {

    private fun gasto(categoria: String, centavos: Long, fecha: Long = 2_000L) =
        GamoraMovement("x", categoria, centavos, false, fecha)

    @Test
    fun soloCuentaGastosDelPeriodoYOrdenaPorUso() {
        val movimientos = listOf(
            gasto("Comida", 90_00),
            gasto("Comida", 50_00, fecha = 500L),          // antes del periodo
            gasto("Transporte", 20_00),
            GamoraMovement("Nómina", "Comida", 999_00, true, 2_000L)  // ingreso, no cuenta
        )

        val avances = avancePresupuestos(
            movimientos,
            mapOf("Comida" to 100_00, "Transporte" to 100_00, "Salud" to 0L),
            inicioPeriodo = 1_000L
        )

        assertEquals(listOf("Comida", "Transporte"), avances.map { it.categoria })
        assertEquals(90, avances[0].porcentaje)
        assertEquals(10_00L, avances[0].restanteCentavos)
    }

    @Test
    fun laBusquedaIgnoraAcentosYMayusculas() {
        assertEquals("cafe educacion", "  Café EDUCACIÓN ".paraBuscar())
    }
}

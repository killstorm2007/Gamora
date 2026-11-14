package com.gamora

import com.gamora.core.Categoria
import com.gamora.feature.transactions.GamoraMovement
import com.gamora.feature.transactions.ahorroAcumulado
import com.gamora.feature.transactions.totalesPorMes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AhorroAcumuladoTest {

    private fun fecha(anio: Int, mes: Int) =
        Calendar.getInstance().apply { set(anio, mes, 15, 12, 0) }.timeInMillis

    private fun mov(centavos: Long, ingreso: Boolean, anio: Int, mes: Int) =
        GamoraMovement("x", Categoria.OTROS, centavos, ingreso, fecha(anio, mes))

    @Test
    fun sumaSoloLosMesesEnPositivo() {
        val movimientos = listOf(
            mov(10_000, true, 2026, Calendar.JANUARY),   // enero: +60
            mov(4_000, false, 2026, Calendar.JANUARY),
            mov(5_000, true, 2026, Calendar.FEBRUARY),   // febrero: -30, no resta
            mov(8_000, false, 2026, Calendar.FEBRUARY),
            mov(2_000, true, 2026, Calendar.MARCH)       // marzo: +20
        )

        assertEquals(8_000L, movimientos.ahorroAcumulado())
        assertEquals(3, movimientos.totalesPorMes().size)
    }

    @Test
    fun aportarAUnaMetaNoReduceElAhorro() {
        val movimientos = listOf(
            mov(10_000, true, 2026, Calendar.JANUARY),
            GamoraMovement("Aportación a «Viaje»", Categoria.AHORRO, 5_000, false, fecha(2026, Calendar.JANUARY))
        )

        assertEquals(10_000L, movimientos.ahorroAcumulado())
    }

    @Test
    fun sinMovimientosNoHayAhorro() {
        assertEquals(0L, emptyList<GamoraMovement>().ahorroAcumulado())
    }
}

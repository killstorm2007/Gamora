package com.gamora

import com.gamora.feature.chatbot.construirContextoFinanciero
import com.gamora.feature.goals.SavingsGoal
import com.gamora.feature.transactions.GamoraMovement
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ContextoFinancieroTest {

    private val ahora = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 10, 12, 0) }

    @Test
    fun incluyeMovimientosMetasYPresupuesto() {
        val contexto = construirContextoFinanciero(
            movimientos = listOf(
                GamoraMovement("Nómina", "Salario", 2_000_000, true, ahora.timeInMillis),
                GamoraMovement("Tacos", "Comida", 35_000, false, ahora.timeInMillis)
            ),
            metas = listOf(SavingsGoal("1", "Viaje", 1_000_000, 250_000)),
            presupuestoCentavos = 1_500_000,
            ahora = ahora
        )

        assertTrue(contexto.contains("\"Tacos\""))
        assertTrue(contexto.contains("Comida"))
        assertTrue(contexto.contains("\"Viaje\""))
        assertTrue(contexto.contains("Presupuesto mensual"))
        assertTrue(contexto.contains("marzo 2026"))
    }

    @Test
    fun sinDatosLoDiceExplicitamente() {
        val contexto = construirContextoFinanciero(emptyList(), emptyList(), 0L, ahora)

        assertTrue(contexto.contains("Aún no ha registrado movimientos"))
        assertTrue(contexto.contains("No ha definido presupuesto"))
    }
}

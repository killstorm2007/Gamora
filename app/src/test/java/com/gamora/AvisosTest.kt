package com.gamora

import com.gamora.core.notifications.inicioPeriodo
import com.gamora.core.notifications.umbralPresupuesto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AvisosTest {

    @Test
    fun umbralesDelPresupuesto() {
        assertNull(umbralPresupuesto(79_00, 100_00))
        assertEquals(80, umbralPresupuesto(80_00, 100_00))
        assertEquals(100, umbralPresupuesto(100_00, 100_00))
        assertNull(umbralPresupuesto(500_00, 0))
    }

    @Test
    fun elPeriodoEmpiezaElDiaElegido() {
        val ahora = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 10, 12, 0) }.timeInMillis
        val inicio = Calendar.getInstance().apply { timeInMillis = inicioPeriodo(ahora, 15) }

        // Antes del día 15 el periodo empezó el 15 del mes anterior.
        assertEquals(Calendar.FEBRUARY, inicio.get(Calendar.MONTH))
        assertEquals(15, inicio.get(Calendar.DAY_OF_MONTH))
    }
}

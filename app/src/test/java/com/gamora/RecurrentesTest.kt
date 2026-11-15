package com.gamora

import com.gamora.feature.transactions.cambiarDia
import com.gamora.feature.transactions.fechasPendientes
import com.gamora.feature.transactions.siguienteFecha
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RecurrentesTest {

    private fun fecha(anio: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { set(anio, mes, dia, 9, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test
    fun cambiarElDiaConservaElMesPendienteYLaHora() {
        // La próxima era el 5 de marzo; al pasar a día 20 sigue tocando en marzo, sin saltarse ni repetir mes.
        assertEquals(fecha(2026, Calendar.MARCH, 20), cambiarDia(fecha(2026, Calendar.MARCH, 5), 20))
        assertEquals(fecha(2026, Calendar.FEBRUARY, 28), cambiarDia(fecha(2026, Calendar.FEBRUARY, 3), 31))
        assertEquals(fecha(2026, Calendar.APRIL, 1), cambiarDia(fecha(2026, Calendar.APRIL, 15), 0))
    }

    @Test
    fun siguienteFechaPasaAlMesSiguienteAunqueSeaFebrero() {
        assertEquals(fecha(2026, Calendar.FEBRUARY, 28), siguienteFecha(fecha(2026, Calendar.JANUARY, 28), 28))
        assertEquals(fecha(2027, Calendar.JANUARY, 5), siguienteFecha(fecha(2026, Calendar.DECEMBER, 5), 5))
    }

    @Test
    fun registraCadaMesQueQuedoPendiente() {
        val pendientes = fechasPendientes(
            proxima = fecha(2026, Calendar.JANUARY, 15),
            dia = 15,
            ahora = fecha(2026, Calendar.MARCH, 20)
        )
        assertEquals(
            listOf(fecha(2026, Calendar.JANUARY, 15), fecha(2026, Calendar.FEBRUARY, 15), fecha(2026, Calendar.MARCH, 15)),
            pendientes
        )
    }

    @Test
    fun nadaPendienteAntesDeLaFecha() {
        assertEquals(0, fechasPendientes(fecha(2026, Calendar.APRIL, 1), 1, fecha(2026, Calendar.MARCH, 31)).size)
    }
}

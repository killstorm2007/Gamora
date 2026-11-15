package com.gamora

import com.gamora.feature.transactions.GamoraTransactionStore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class VentanaEnVivoTest {

    @Test
    fun incluyeElMesActualYLosDoceAnteriores() {
        val ahora = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 14, 15, 30) }.timeInMillis
        val inicio = Calendar.getInstance().apply { timeInMillis = GamoraTransactionStore.inicioEnVivo(ahora) }

        assertEquals(2025, inicio.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, inicio.get(Calendar.MONTH))
        assertEquals(1, inicio.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, inicio.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, inicio.get(Calendar.MINUTE))
    }

    @Test
    fun enEneroLlegaAlEneroDelAnioAnterior() {
        val ahora = Calendar.getInstance().apply { set(2027, Calendar.JANUARY, 31, 23, 59) }.timeInMillis
        val inicio = Calendar.getInstance().apply { timeInMillis = GamoraTransactionStore.inicioEnVivo(ahora) }

        assertEquals(2026, inicio.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, inicio.get(Calendar.MONTH))
        assertEquals(1, inicio.get(Calendar.DAY_OF_MONTH))
    }
}

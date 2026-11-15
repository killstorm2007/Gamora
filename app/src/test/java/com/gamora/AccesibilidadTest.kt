package com.gamora

import com.gamora.core.ui.textoParaLector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccesibilidadTest {

    @Test
    fun quitaEmojisYSimbolosDecorativosDelInicio() {
        assertEquals("Información personal", textoParaLector("👤     Información personal"))
        assertEquals("Ingreso", textoParaLector("+  Ingreso"))
        assertEquals("Gasto", textoParaLector("−  Gasto"))
        assertEquals("Todavía no hay gastos.", textoParaLector("📊\n\nTodavía no hay gastos."))
        assertEquals("Ingresos", textoParaLector("● Ingresos"))
    }

    @Test
    fun dejaIgualLoQueNoTieneDecoracion() {
        assertNull(textoParaLector("Movimientos"))
        assertNull(textoParaLector("$1,250.00"))
        assertNull(textoParaLector("−$12.90"))
        assertNull(textoParaLector("1 meta activa"))
        assertNull(textoParaLector(""))
        assertNull(textoParaLector(null))
    }
}

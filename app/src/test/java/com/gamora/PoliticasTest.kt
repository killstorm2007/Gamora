package com.gamora

import com.gamora.core.legal.VERSION_POLITICAS
import com.gamora.core.legal.compararVersiones
import com.gamora.core.legal.requiereAceptar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticasTest {

    @Test
    fun versionVigente() {
        assertEquals("1.1", VERSION_POLITICAS)
    }

    @Test
    fun pideAceptarSiNoHayVersionOEsVieja() {
        assertTrue(requiereAceptar(null))
        assertTrue(requiereAceptar(""))
        assertTrue(requiereAceptar("1.0"))
        assertFalse(requiereAceptar("1.1"))
        assertFalse(requiereAceptar("2.0"))
    }

    @Test
    fun comparaPorNumeros() {
        assertTrue(compararVersiones("1.10", "1.9") > 0)
        assertTrue(compararVersiones("1.0", "1.1") < 0)
        assertEquals(0, compararVersiones("1.1", "1.1.0"))
    }
}

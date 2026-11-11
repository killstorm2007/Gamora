package com.gamora

import com.gamora.core.ui.dinero
import org.junit.Assert.assertEquals
import org.junit.Test

class MonedaTest {

    @Test
    fun cadaMonedaUsaSuSimbolo() {
        assertEquals("$1,234.50", dinero(123_450, "MXN"))
        assertEquals("US$1,234.50", dinero(123_450, "USD"))
        assertEquals("€1,234.50", dinero(123_450, "EUR"))
    }
}

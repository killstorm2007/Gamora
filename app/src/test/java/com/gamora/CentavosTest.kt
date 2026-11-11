package com.gamora

import com.gamora.core.ui.centavosDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CentavosTest {

    @Test
    fun convierteFormatosComunes() {
        assertEquals(125_050L, centavosDe("1250.50"))
        assertEquals(125_050L, centavosDe("1250,5"))
        assertEquals(125_050L, centavosDe("1,250.50"))
        assertEquals(30_000L, centavosDe(" 300 "))
        assertEquals(0L, centavosDe("0"))
    }

    @Test
    fun rechazaValoresInvalidos() {
        assertNull(centavosDe(""))
        assertNull(centavosDe("abc"))
        assertNull(centavosDe("-5"))
        assertNull(centavosDe("10.555"))
        assertNull(centavosDe("999999999999999999999"))
    }
}

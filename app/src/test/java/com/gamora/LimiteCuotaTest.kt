package com.gamora

import com.gamora.feature.importar.esLimiteDeCuota
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiteCuotaTest {

    @Test
    fun reconoceElLimiteAunqueVengaEnvuelto() {
        assertTrue(esLimiteDeCuota(Exception("429 RESOURCE_EXHAUSTED: quota")))
        assertTrue(esLimiteDeCuota(RuntimeException("falló", Exception("Quota exceeded for metric"))))
    }

    @Test
    fun otrosErroresNoSonLimite() {
        assertFalse(esLimiteDeCuota(Exception("500 INTERNAL")))
        assertFalse(esLimiteDeCuota(java.io.IOException("Unable to resolve host")))
    }
}

package com.gamora

import com.gamora.feature.importar.TipoDocumento
import com.gamora.feature.importar.detectarTipo
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectarTipoTest {

    @Test
    fun reconocePdfXmlEImagen() {
        assertEquals(TipoDocumento.PDF, detectarTipo("%PDF-1.7 ...".toByteArray(), "application/octet-stream"))
        assertEquals(TipoDocumento.XML, detectarTipo("\uFEFF<?xml version=\"1.0\"?>".toByteArray(), null))
        assertEquals(TipoDocumento.IMAGEN, detectarTipo(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0, 0), null))
        assertEquals(TipoDocumento.IMAGEN, detectarTipo(byteArrayOf(1, 2, 3), "image/png"))
        assertEquals(TipoDocumento.OTRO, detectarTipo("hola".toByteArray(), "text/plain"))
    }
}

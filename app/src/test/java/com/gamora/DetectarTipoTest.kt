package com.gamora

import com.gamora.feature.importar.TipoDocumento
import com.gamora.feature.importar.detectarTipo
import com.gamora.feature.importar.mimeDeImagen
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

    private val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)
    private val heic = "\u0000\u0000\u0000\u0018ftypheic".toByteArray(Charsets.ISO_8859_1)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0)
    private val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0)

    @Test
    fun webpYHeicSeReconocenAunqueAndroidDigaArchivoGenerico() {
        assertEquals(TipoDocumento.IMAGEN, detectarTipo(webp, "application/octet-stream"))
        assertEquals(TipoDocumento.IMAGEN, detectarTipo(heic, "application/octet-stream"))
    }

    @Test
    fun elTipoDeImagenSaleDelContenido() {
        assertEquals("image/png", mimeDeImagen(png, "application/octet-stream"))
        assertEquals("image/jpeg", mimeDeImagen(jpg, null))
        assertEquals("image/webp", mimeDeImagen(webp, "image/jpeg"))
        assertEquals("image/heic", mimeDeImagen(heic, null))
        assertEquals("image/gif", mimeDeImagen(byteArrayOf(1, 2, 3), "image/gif"))
        assertEquals("image/jpeg", mimeDeImagen(byteArrayOf(1, 2, 3), null))
    }
}

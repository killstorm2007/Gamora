package com.gamora

import com.gamora.core.Categoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriaTest {

    @Test
    fun porClaveEsEstricta() {
        assertEquals(Categoria.COMIDA, Categoria.porClave("comida"))
        assertNull(Categoria.porClave("Comida"))
        assertNull(Categoria.porClave(null))
    }

    @Test
    fun deAceptaNombreEnEspanol() {
        assertEquals(Categoria.EDUCACION, Categoria.de("Educación"))
        assertEquals(Categoria.EDUCACION, Categoria.de("EDUCACION"))
        assertEquals(Categoria.COMIDA, Categoria.de(" comida "))
        assertEquals(Categoria.OTROS, Categoria.de("Food"))
        assertEquals(Categoria.OTROS, Categoria.de(""))
        assertEquals(Categoria.OTROS, Categoria.de(null))
    }

    @Test
    fun listasEnElOrdenDeHoy() {
        assertEquals(
            listOf("salario", "ventas", "negocio", "inversiones", "regalo", "otros"),
            Categoria.DE_INGRESO.map { it.clave }
        )
        assertEquals(
            listOf("comida", "transporte", "vivienda", "servicios", "salud", "educacion", "entretenimiento", "compras", "ahorro", "otros"),
            Categoria.DE_GASTO.map { it.clave }
        )
    }

    @Test
    fun clavesCumplenLasReglas() {
        Categoria.entries.forEach { assertTrue(it.clave.length in 1..40 && it.clave == it.clave.lowercase()) }
    }
}

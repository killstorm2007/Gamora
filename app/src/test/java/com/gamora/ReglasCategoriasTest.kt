package com.gamora

import com.gamora.core.Categoria
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Las reglas de Firestore deben aceptar exactamente las claves de [Categoria]. */
class ReglasCategoriasTest {

    @Test
    fun reglasYCategoriasCoinciden() {
        val reglas = listOf("../firestore.rules", "firestore.rules").map(::File).first { it.exists() }.readText()
        val lista = Regex("""function categorias\(\) \{\s*return \[(.*?)\];""", RegexOption.DOT_MATCHES_ALL)
            .find(reglas)!!.groupValues[1]
        val enReglas = Regex("""'([a-z]+)'""").findAll(lista).map { it.groupValues[1] }.toSet()
        assertEquals(Categoria.entries.map { it.clave }.toSet(), enReglas)
    }
}

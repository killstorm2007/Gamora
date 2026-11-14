package com.gamora

import com.gamora.core.textoPlural
import org.junit.Assert.assertEquals
import org.junit.Test

class PluralesTest {

    init {
        TextosDePrueba.instalar()
    }

    @Test
    fun metasActivasEnCeroUnoYVarias() {
        assertEquals("0 metas activas", textoPlural(R.plurals.goals_metas_activas, 0, 0))
        assertEquals("1 meta activa", textoPlural(R.plurals.goals_metas_activas, 1, 1))
        assertEquals("3 metas activas", textoPlural(R.plurals.goals_metas_activas, 3, 3))
    }

    @Test
    fun renglonesNoLeidos() {
        assertEquals("1 renglón no se pudo leer; revisa que no falte nada.", textoPlural(R.plurals.importar_documento_renglones_no_leidos, 1, 1))
        assertEquals("2 renglones no se pudieron leer; revisa que no falte nada.", textoPlural(R.plurals.importar_documento_renglones_no_leidos, 2, 2))
    }
}

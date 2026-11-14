package com.gamora

import com.gamora.core.Categoria
import com.gamora.feature.importar.NoEsNominaException
import com.gamora.feature.importar.leerNominaCfdi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NominaCfdiTest {

    init {
        TextosDePrueba.instalar()
    }

    private fun cfdi(complemento: String, fecha: String = "2026-10-15T10:00:00") = """
        <?xml version="1.0" encoding="UTF-8"?>
        <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4" xmlns:nomina12="http://www.sat.gob.mx/nomina12"
            Version="4.0" Fecha="$fecha" Total="12500.00" TipoDeComprobante="N">
          <cfdi:Emisor Rfc="EMP010101AAA" Nombre="EMPRESA SA" RegimenFiscal="601"/>
          <cfdi:Receptor Rfc="XAXX010101000" Nombre="PERSONA"/>
          <cfdi:Complemento>$complemento</cfdi:Complemento>
        </cfdi:Comprobante>
    """.trimIndent().toByteArray()

    private val nomina = """<nomina12:Nomina Version="1.2" TipoNomina="O" FechaPago="2026-10-15" FechaInicialPago="2026-10-01" FechaFinalPago="2026-10-15" NumDiasPagados="15"/>"""

    @Test
    fun leeNominaValida() {
        val r = leerNominaCfdi(cfdi(nomina))

        assertEquals(1, r.movimientos.size)
        val m = r.movimientos.single()
        assertEquals(1250000L, m.montoCentavos)
        assertTrue(m.esIngreso)
        assertEquals(Categoria.SALARIO, m.categoria)
        assertEquals("Nómina · EMPRESA SA", m.descripcion)
        assertEquals(15, Calendar.getInstance().apply { timeInMillis = m.fecha }.get(Calendar.DAY_OF_MONTH))
        assertEquals("EMPRESA SA", r.banco)
        assertEquals("2026-10-01 al 2026-10-15", r.periodo)
    }

    @Test
    fun facturaSinNominaLanzaNoEsNomina() {
        assertThrows(NoEsNominaException::class.java) { leerNominaCfdi(cfdi("")) }
    }

    @Test
    fun xmlMalFormadoLanzaNoEsNomina() {
        assertThrows(NoEsNominaException::class.java) { leerNominaCfdi("<cfdi:Comprobante".toByteArray()) }
    }

    @Test
    fun nominaSinFechaPagoUsaFechaDelComprobante() {
        val sinFecha = """<nomina12:Nomina Version="1.2" TipoNomina="O"/>"""
        val m = leerNominaCfdi(cfdi(sinFecha, fecha = "2026-10-20T10:00:00")).movimientos.single()

        assertEquals(20, Calendar.getInstance().apply { timeInMillis = m.fecha }.get(Calendar.DAY_OF_MONTH))
    }
}

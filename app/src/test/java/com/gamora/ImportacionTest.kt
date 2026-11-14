package com.gamora

import com.gamora.feature.importar.MovimientoImportado
import com.gamora.feature.importar.Resumen
import com.gamora.feature.importar.enLotes
import com.gamora.feature.importar.leerRespuestaGemini
import com.gamora.feature.importar.marcarRepetidos
import com.gamora.feature.importar.resumen
import com.gamora.feature.transactions.GamoraMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ImportacionTest {

    private fun dia(anio: Int, mes: Int, dia: Int): Long =
        Calendar.getInstance().apply { clear(); set(anio, mes, dia, 12, 0) }.timeInMillis

    private fun fila(fecha: String, descripcion: String, monto: Any, esIngreso: Boolean, categoria: String = "Comida") =
        """{"fecha":"$fecha","descripcion":"$descripcion","montoCentavos":${if (monto is String) "\"$monto\"" else monto},"esIngreso":$esIngreso,"categoria":"$categoria"}"""

    private fun respuesta(vararg filas: String) =
        """{"banco":"BBVA","periodo":"1 al 31 oct 2026","movimientos":[${filas.joinToString(",")}]}"""

    @Test
    fun leeMovimientosValidos() {
        val r = leerRespuestaGemini(
            respuesta(
                fila("2026-10-03", "OXXO REFORMA", 8400, false),
                fila("2026-10-05", "NÓMINA EMPRESA", 1250000, true, "Salario")
            )
        )

        assertEquals("BBVA", r.banco)
        assertEquals(0, r.descartados)
        assertEquals(listOf(8400L, 1250000L), r.movimientos.map { it.montoCentavos })
        assertEquals(listOf(false, true), r.movimientos.map { it.esIngreso })

        val fecha = Calendar.getInstance().apply { timeInMillis = r.movimientos[0].fecha }
        assertEquals(2026, fecha.get(Calendar.YEAR))
        assertEquals(9, fecha.get(Calendar.MONTH))
        assertEquals(3, fecha.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun descartaFilasInvalidas() {
        val r = leerRespuestaGemini(
            respuesta(
                fila("2026-10-03", "Cero", 0, false),
                fila("2026-10-03", "Negativo", -5, false),
                fila("2026-10-03", "Enorme", 100_000_000_001, false),
                fila("2026-10-03", "  ", 100, false),
                fila("03/10", "Fecha rara", 100, false),
                fila("2026-10-03", "Texto", "1,234.50", false)
            )
        )

        assertEquals(0, r.movimientos.size)
        assertEquals(6, r.descartados)
    }

    @Test
    fun categoriaDesconocidaPasaAOtros() {
        val r = leerRespuestaGemini(
            respuesta(
                fila("2026-10-03", "PEMEX", 50000, false, "Gasolina"),
                fila("2026-10-04", "Tacos", 9000, false, "Comida")
            )
        )

        assertEquals(listOf("Otros", "Comida"), r.movimientos.map { it.categoria })
    }

    @Test
    fun jsonInvalidoLanzaError() {
        assertThrows(IllegalArgumentException::class.java) { leerRespuestaGemini("no es json") }
    }

    private fun importado(fecha: Long, monto: Long, esIngreso: Boolean = false) =
        MovimientoImportado(fecha, "Arizona", monto, esIngreso, "Comida")

    @Test
    fun marcaRepetidoContraExistente() {
        val existentes = listOf(GamoraMovement("Arizona", "Comida", 3000, false, dia(2026, 9, 7)))

        val r = marcarRepetidos(
            listOf(importado(dia(2026, 9, 8), 3000), importado(dia(2026, 9, 11), 3000)),
            existentes
        )

        assertTrue(r[0].repetido)
        assertFalse(r[0].seleccionado)
        assertFalse(r[1].repetido)
        assertTrue(r[1].seleccionado)
    }

    @Test
    fun marcaRepetidoDentroDelDocumento() {
        val r = marcarRepetidos(
            listOf(importado(dia(2026, 9, 8), 3000), importado(dia(2026, 9, 8), 3000)),
            emptyList()
        )

        assertEquals(listOf(false, true), r.map { it.repetido })
    }

    @Test
    fun ingresoYGastoMismoMontoNoSonRepetidos() {
        val r = marcarRepetidos(
            listOf(importado(dia(2026, 9, 8), 3000, esIngreso = true), importado(dia(2026, 9, 8), 3000)),
            emptyList()
        )

        assertEquals(listOf(false, false), r.map { it.repetido })
    }

    @Test
    fun enLotesParte1201En3() {
        assertEquals(listOf(500, 500, 201), (1..1201).toList().enLotes().map { it.size })
    }

    @Test
    fun resumenSoloCuentaSeleccionados() {
        val lista = listOf(
            MovimientoImportado(0, "Sueldo", 100000, true, "Salario"),
            MovimientoImportado(0, "Oxxo", 8400, false, "Comida"),
            MovimientoImportado(0, "Arizona", 3000, false, "Comida", seleccionado = false)
        )

        assertEquals(Resumen(2, 100000, 8400), resumen(lista))
    }
}

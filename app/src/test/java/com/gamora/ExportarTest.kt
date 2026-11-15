package com.gamora

import com.gamora.core.Categoria
import com.gamora.feature.exportar.Periodo
import com.gamora.feature.exportar.csvDeMovimientos
import com.gamora.feature.exportar.enPeriodo
import com.gamora.feature.transactions.GamoraMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExportarTest {

    private fun fecha(anio: Int, mes: Int, dia: Int) =
        Calendar.getInstance().apply { clear(); set(anio, mes, dia, 12, 0) }.timeInMillis

    private val nombre: (Categoria) -> String = { it.clave.replaceFirstChar(Char::uppercase) }

    @Test
    fun csvConEncabezadoComillasYOrden() {
        val movimientos = listOf(
            GamoraMovement("Tacos \"El Güero\", centro", Categoria.COMIDA, 25_050, false, fecha(2026, Calendar.MARCH, 2)),
            GamoraMovement("Nómina", Categoria.SALARIO, 1_500_000, true, fecha(2026, Calendar.MARCH, 1))
        )

        val csv = csvDeMovimientos(movimientos, "MXN", nombre, encabezados = listOf("Fecha", "Tipo", "Categoría", "Descripción", "Monto", "Moneda"),
            ingreso = "Ingreso", gasto = "Gasto")
        val lineas = csv.removePrefix("\uFEFF").trimEnd().lines()

        assertTrue("Excel necesita BOM para leer acentos", csv.startsWith("\uFEFF"))
        assertEquals("Fecha,Tipo,Categoría,Descripción,Monto,Moneda", lineas[0])
        assertEquals("2026-03-01,Ingreso,Salario,Nómina,15000.00,MXN", lineas[1])
        assertEquals("2026-03-02,Gasto,Comida,\"Tacos \"\"El Güero\"\", centro\",-250.50,MXN", lineas[2])
    }

    @Test
    fun periodosFiltranPorFecha() {
        val hoy = fecha(2026, Calendar.MARCH, 15)
        val movimientos = listOf(
            GamoraMovement("a", Categoria.OTROS, 1, false, fecha(2026, Calendar.MARCH, 1)),
            GamoraMovement("b", Categoria.OTROS, 1, false, fecha(2026, Calendar.JANUARY, 10)),
            GamoraMovement("c", Categoria.OTROS, 1, false, fecha(2025, Calendar.DECEMBER, 31))
        )

        assertEquals(listOf("a"), movimientos.enPeriodo(Periodo.ESTE_MES, hoy).map { it.description })
        assertEquals(listOf("b", "a"), movimientos.enPeriodo(Periodo.TRES_MESES, hoy).map { it.description })
        assertEquals(listOf("b", "a"), movimientos.enPeriodo(Periodo.ESTE_ANIO, hoy).map { it.description })
        assertEquals(3, movimientos.enPeriodo(Periodo.TODO, hoy).size)
    }
}

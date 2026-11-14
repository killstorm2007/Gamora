package com.gamora

import com.gamora.feature.importar.sinia.Palabra
import com.gamora.feature.importar.sinia.decodificarEbcdic
import com.gamora.feature.importar.sinia.leerTablas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Formatos imitados de estados de cuenta reales, con datos inventados.
 * Cada fila se arma con palabras y su posición (x inicial) en la página.
 */
class LectorTablasTest {

    // 12 de noviembre de 2026: ninguna fecha de prueba es futura.
    private val hoy = Calendar.getInstance().apply { clear(); set(2026, 10, 12) }.timeInMillis

    private class Hoja {
        val palabras = mutableListOf<Palabra>()
        var y = 100f
        var pagina = 1

        /** Una fila: pares (texto, x0). El ancho se estima con 5 puntos por letra. */
        fun fila(vararg celdas: Pair<String, Float>) {
            celdas.forEach { (texto, x0) ->
                texto.split(" ").fold(x0) { x, palabra ->
                    palabras += Palabra(palabra, x, x + palabra.length * 5f, y, pagina, 8f)
                    x + palabra.length * 5f + 3f
                }
            }
            y += 12f
        }

        /** Monto alineado a la derecha en [derecha] (como en los PDF de bancos). */
        fun monto(texto: String, derecha: Float) = texto to (derecha - texto.length * 5f)

        fun nuevaPagina() {
            pagina++; y = 100f
        }
    }

    private fun dia(movimientoFecha: Long) = Calendar.getInstance().apply { timeInMillis = movimientoFecha }

    @Test
    fun columnasDeCargosYAbonosConDosFechas() {
        // Como BBVA: OPER y LIQ, cargos y abonos en columnas, saldo a la derecha.
        val h = Hoja()
        h.fila("Periodo DEL 09/09/2026 AL 08/10/2026" to 20f)
        h.fila("OPER" to 20f, "LIQ" to 70f, "DESCRIPCION" to 105f, "CARGOS" to 380f, "ABONOS" to 430f, "SALDO" to 520f)
        h.fila("10/SEP" to 15f, "10/SEP" to 60f, "PAGO TARJETA DE TERCEROS" to 106f, h.monto("108.00", 416f))
        h.fila("CUENTA: 4772000011112222 MBAN" to 106f)
        h.fila("11/SEP" to 15f, "11/SEP" to 60f, "SPEI RECIBIDO" to 106f, h.monto("1,500.00", 463f), h.monto("1,392.00", 560f))
        h.fila("Pago de renta" to 106f)

        val r = leerTablas(h.palabras, hoy)!!

        assertEquals(2, r.movimientos.size)
        assertEquals(10800L, r.movimientos[0].montoCentavos)
        assertEquals(false, r.movimientos[0].esIngreso)
        assertEquals("PAGO TARJETA DE TERCEROS", r.movimientos[0].descripcion)
        assertEquals(150000L, r.movimientos[1].montoCentavos)
        assertTrue(r.movimientos[1].esIngreso)
        assertEquals("SPEI RECIBIDO · Pago de renta", r.movimientos[1].descripcion)
        assertEquals(2026, dia(r.movimientos[0].fecha).get(Calendar.YEAR))
        assertEquals(8, dia(r.movimientos[0].fecha).get(Calendar.MONTH))
    }

    @Test
    fun soloDiaTomaElMesDelPeriodo() {
        // Como Banregio/Afirme: la columna "DIA" trae solo el día.
        val h = Hoja()
        h.fila("Periodo del 01/03/2026 al 31/03/2026" to 20f)
        h.fila("DIA" to 37f, "CONCEPTO" to 175f, "CARGOS" to 366f, "ABONOS" to 443f, "SALDO" to 527f)
        h.fila("02" to 37f, "EFE 4741000011112222-RETIRO CAJERO" to 53f, h.monto("2,000.00", 410f), h.monto("604.39", 574f))

        val m = leerTablas(h.palabras, hoy)!!.movimientos.single()

        assertEquals("EFE RETIRO CAJERO", m.descripcion)
        assertEquals(2, dia(m.fecha).get(Calendar.DAY_OF_MONTH))
        assertEquals(2, dia(m.fecha).get(Calendar.MONTH))
    }

    @Test
    fun montoEnLaPaginaSiguienteNoTomaOtrasTablas() {
        // Como Banamex: el último movimiento de la página trae su monto después del encabezado
        // de la siguiente; y al final hay otra tabla "Cuenta eje para cargos y abonos".
        val h = Hoja()
        h.fila("ESTADO DE CUENTA AL 06 DE ABRIL DE 2026" to 200f)
        h.fila("FECHA" to 19f, "CONCEPTO" to 130f, "RETIROS" to 271f, "DEPOSITOS" to 337f, "SALDO" to 425f)
        h.fila("30 MAR" to 14f, "ABONO/NOMINA" to 58f)
        h.nuevaPagina()
        h.fila("ESTADO DE CUENTA AL 06 DE ABRIL DE 2026" to 200f)
        h.fila("FECHA" to 19f, "CONCEPTO" to 130f, "RETIROS" to 271f, "DEPOSITOS" to 337f, "SALDO" to 425f)
        h.fila("EMPRESA DEMO" to 58f, h.monto("3,000.00", 395f), h.monto("3,100.00", 458f))
        h.fila("06 ABR" to 14f, "DISPOSICIONES EN CAJERO" to 58f)
        h.nuevaPagina()
        h.fila("Cuenta Eje para Cargos y Abonos" to 20f)
        h.fila("Depósitos" to 115f, h.monto("$16,635.34", 395f))

        val r = leerTablas(h.palabras, hoy)!!

        val m = r.movimientos.single()
        assertEquals(300000L, m.montoCentavos)
        assertTrue(m.esIngreso)
        assertEquals("Salario", m.categoria)
    }

    @Test
    fun columnaUnicaConSigno() {
        // Como Spin: "Monto total" con "-" para gastos; los depósitos vienen sin signo.
        val h = Hoja()
        h.fila("Periodo septiembre 2026" to 20f)
        h.fila("Fecha" to 23f, "ID de movimiento" to 98f, "Detalle" to 248f, "Monto total" to 500f)
        h.fila("03-sep-2026" to 23f, "5f00aa11bb22cc33dd44ee55" to 98f, "Compraste con tu tarjeta" to 248f, h.monto("-$28.00", 555f))
        h.fila("01:47:42 pm" to 23f, "TIENDA DEMO CENTRO" to 248f)
        h.fila("21-sep-2026" to 23f, "5f00aa11bb22cc33dd44ee66" to 98f, "Depositaste a tu cuenta en OXXO" to 248f, h.monto("$1200.00", 555f))
        h.fila("Cargos objetados por el cliente" to 175f)

        val r = leerTablas(h.palabras, hoy)!!

        assertEquals(listOf(false, true), r.movimientos.map { it.esIngreso })
        assertEquals("TIENDA DEMO CENTRO", r.movimientos[0].descripcion)
        assertEquals("Depositaste a tu cuenta en OXXO", r.movimientos[1].descripcion)
        assertEquals(listOf(2800L, 120000L), r.movimientos.map { it.montoCentavos })
    }

    @Test
    fun tarjetaDeCreditoOmitePagosYDiferidos() {
        val h = Hoja()
        h.fila("Pago mínimo $500.00 Fecha límite de pago 28-oct-2026" to 20f)
        h.fila("Periodo: 9-sep-2026 al 08-oct-2026" to 20f)
        // Tabla de compras a meses: no se importa (ya se cobra mes a mes en la otra).
        h.fila("Fecha" to 37f, "Descripción" to 156f, "Monto" to 297f, "Saldo" to 355f)
        h.fila("09-sep-2026" to 26f, "TIENDA DEMO" to 77f, h.monto("$24,800.00", 329f), h.monto("$16,533.36", 386f))
        h.fila("Fecha" to 34f, "Descripción del movimiento" to 253f, "Monto" to 502f)
        h.fila("09-sep-2026" to 33f, "TIENDA DEMO 008 de 024" to 151f, "+" to 470f, h.monto("$1,033.33", 519f))
        h.fila("11-sep-2026" to 33f, "SU ABONO...GRACIAS" to 150f, "-" to 470f, h.monto("$200.00", 559f))

        val r = leerTablas(h.palabras, hoy)!!

        val m = r.movimientos.single()
        assertEquals(103333L, m.montoCentavos)
        assertEquals(false, m.esIngreso)
    }

    @Test
    fun tarjetaQuitaElRfcDelComercio() {
        // Como Santander: el RFC del comercio va en su propia columna, entre la descripción y el monto.
        val h = Hoja()
        h.fila("Pago mínimo $500.00 Fecha límite de pago 28-oct-2026" to 20f)
        h.fila("Fecha de la" to 44f, "Fecha de" to 101f)
        h.fila("Descripción del movimiento" to 269f, "Monto" to 527f)
        h.fila("14-Mar-2026" to 40f, "17-Mar-2026" to 95f, "TIENDA DEMO CENTRO" to 150f, "TDE 0007 121K4" to 314f, "+" to 496f, h.monto("$500.00", 582f))
        h.fila("16-Mar-2026" to 40f, "17-Mar-2026" to 95f, "FERRETERIA DEMO" to 150f, "FDM 950714DC2" to 314f, h.monto("$133.50", 582f))
        h.fila("27-Mar-2026" to 40f, "30-Mar-2026" to 95f, "MERCADOPAGO DEMO" to 150f, "+" to 496f, h.monto("$268.00", 582f))

        val r = leerTablas(h.palabras, hoy)!!

        assertEquals(listOf("TIENDA DEMO CENTRO", "FERRETERIA DEMO", "MERCADOPAGO DEMO"), r.movimientos.map { it.descripcion })
    }

    @Test
    fun ebcdicSeDecodificaYLoNormalNo() {
        assertEquals("NETNM P 35", decodificarEbcdic("ÕÅãÕÔ@×@óõ"))
        assertEquals("$ 9,591.60", decodificarEbcdic("[@ùkõùñKöð"))
        assertEquals("Depósito/Abono", decodificarEbcdic("Depósito/Abono"))
        assertEquals("Día", decodificarEbcdic("Día"))
    }

    @Test
    fun documentoSinTablaDevuelveNull() {
        val h = Hoja()
        h.fila("Estimado cliente, le informamos de nuestros nuevos horarios." to 20f)
        h.fila("RESUMEN POR MEDIOS DE ACCESO RETIROS DEPOSITOS" to 14f)
        h.fila("Cheques" to 14f, h.monto("$53,016.00", 366f), h.monto("$53,073.75", 480f))

        assertNull(leerTablas(h.palabras, hoy))
    }
}

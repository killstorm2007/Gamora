package com.gamora

import com.gamora.feature.importar.sinia.Palabra
import com.gamora.feature.importar.sinia.corregirOcr
import com.gamora.feature.importar.sinia.corregirTextoOcr
import com.gamora.feature.importar.sinia.leerTablas
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Confusiones típicas del reconocimiento de texto en estados escaneados (datos inventados). */
class CorregirOcrTest {

    @Test
    fun corrigeFechasYMontos() {
        assertEquals("01-OCT-2026", corregirTextoOcr("01-0CT-2026"))
        assertEquals("30-Mar-2026", corregirTextoOcr("130-Mar-2026"))
        assertEquals("$8,885.05", corregirTextoOcr("S8.885.05"))
        assertEquals("$0.00", corregirTextoOcr("\$O.00"))
        assertEquals("880,103.04", corregirTextoOcr("880,103.04"))
        assertEquals("SORIANA242", corregirTextoOcr("SORIANA242"))
        assertEquals("S.A.", corregirTextoOcr("S.A."))
    }

    @Test
    fun separaBarrasYFechasPegadas() {
        val r = corregirOcr(listOf(Palabra("|14-Mar-202617-Mar-2026|", 36f, 146f, 100f, 1)))
        assertEquals(listOf("14-Mar-2026", "17-Mar-2026"), r.map { it.texto })
    }

    @Test
    fun juntaSignoYMontoPartido() {
        val r = corregirOcr(listOf(
            Palabra("S", 546f, 548f, 100f, 1),
            Palabra("1,756.41", 554f, 583f, 100f, 1),
            Palabra("\$2.499", 546f, 570f, 120f, 1),
            Palabra("93", 573f, 581f, 120f, 1)
        ))
        assertEquals(listOf("$", "1,756.41", "\$2,499.93"), r.map { it.texto })
    }

    @Test
    fun cuentaEscaneadaConFoliosYMesesConfundidos() {
        val hoy = Calendar.getInstance().apply { clear(); set(2026, 10, 12) }.timeInMillis
        val palabras = mutableListOf<Palabra>()
        var y = 100f
        fun fila(vararg celdas: Pair<String, Float>) {
            celdas.forEach { (t, x) ->
                t.split(" ").fold(x) { x0, p -> palabras += Palabra(p, x0, x0 + p.length * 5f, y, 1); x0 + p.length * 5f + 3f }
            }
            y += 11f
        }
        fila("PERIOD0 16-SEP-2026 AL 15-0CT-2026" to 360f)
        fila("FECHA" to 40f, "FOLIO" to 82f, "DESCRIPCION" to 116f, "DEPOSITO" to 375f, "RETIRO" to 448f, "SALDO" to 524f)
        fila("01-0CT-2026" to 30f, "0000000" to 80f, "ABONO PAGO DE NOMINA" to 113f, "1,000.00" to 384f, "2,000.00" to 537f)
        fila("05-0CT-2026" to 30f, "1081707" to 80f, "RETIRO CON FICHA" to 114f, "500.00" to 442f, "1,500.00" to 537f)
        fila("TOTAL" to 121f, "1,000.00" to 384f, "500.00" to 442f)

        val r = leerTablas(corregirOcr(palabras), hoy)!!

        assertEquals(listOf("ABONO PAGO DE NOMINA", "RETIRO CON FICHA"), r.movimientos.map { it.descripcion })
        assertEquals(listOf(true, false), r.movimientos.map { it.esIngreso })
        assertEquals(listOf(100000L, 50000L), r.movimientos.map { it.montoCentavos })
    }
}

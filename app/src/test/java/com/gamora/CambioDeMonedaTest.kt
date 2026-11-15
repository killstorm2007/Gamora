package com.gamora

import com.gamora.core.Categoria
import com.gamora.feature.profile.convertirMonto
import com.gamora.feature.profile.factorDeConversion
import com.gamora.feature.profile.monedasDelTipoDeCambio
import com.gamora.feature.transactions.GamoraMovement
import com.gamora.feature.transactions.ahorroAcumulado
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CambioDeMonedaTest {

    @Test
    fun elTipoDeCambioSePideComoSePublica() {
        assertEquals("USD" to "MXN", monedasDelTipoDeCambio("MXN", "USD"))
        assertEquals("USD" to "MXN", monedasDelTipoDeCambio("USD", "MXN"))
        assertEquals("EUR" to "USD", monedasDelTipoDeCambio("USD", "EUR"))
    }

    @Test
    fun dePesosADolaresDivideYDeDolaresAPesosMultiplica() {
        val tasa = BigDecimal("18.50")
        // $1,850.00 MXN → US$100.00
        assertEquals(10_000L, convertirMonto(185_000L, factorDeConversion("MXN", "USD", tasa)))
        // US$100.00 → $1,850.00 MXN
        assertEquals(185_000L, convertirMonto(10_000L, factorDeConversion("USD", "MXN", tasa)))
    }

    @Test
    fun redondeaAlCentavoYRespetaLosLimites() {
        val factor = factorDeConversion("MXN", "USD", BigDecimal("18.50"))
        // 10 centavos MXN ≈ 0.54 centavos USD → 1 centavo (un movimiento nunca queda en 0).
        assertEquals(1L, convertirMonto(10L, factor))
        // Lo ahorrado en una meta sí puede quedar en 0.
        assertEquals(0L, convertirMonto(0L, factor, minimo = 0))
        // Nunca pasa del máximo de las reglas de Firestore.
        assertEquals(100_000_000_000L, convertirMonto(99_000_000_000L, factorDeConversion("EUR", "MXN", BigDecimal("20"))))
    }

    @Test
    fun retirarDeUnaMetaNoCuentaComoIngresoNuevo() {
        val aportacion = GamoraMovement("Aportación", Categoria.AHORRO, 5_000, isIncome = false, timestamp = 1_000L)
        val retiro = GamoraMovement("Retiro", Categoria.AHORRO, 5_000, isIncome = true, timestamp = 2_000L)
        val salario = GamoraMovement("Salario", Categoria.SALARIO, 20_000, isIncome = true, timestamp = 3_000L)

        // Aportar y luego retirar lo mismo deja el ahorro acumulado igual que sin moverlo.
        assertEquals(20_000L, listOf(salario, aportacion, retiro).ahorroAcumulado())
        assertEquals(20_000L, listOf(salario).ahorroAcumulado())
    }
}

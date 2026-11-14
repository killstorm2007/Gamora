package com.gamora

import com.gamora.feature.analytics.generarConsejos
import com.gamora.feature.goals.SavingsGoal
import com.gamora.feature.transactions.GamoraMovement
import com.gamora.core.Categoria
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ConsejosAhorroTest {

    init {
        TextosDePrueba.instalar()
    }

    private val ahora = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 10, 12, 0) }

    // Nombres como los de strings.xml (las pruebas no tienen Context).
    private val NOMBRE: (Categoria) -> String = { it.clave.replaceFirstChar(Char::uppercase) }

    private fun mov(centavos: Long, ingreso: Boolean, categoria: Categoria = Categoria.OTROS) =
        GamoraMovement("x", categoria, centavos, ingreso, ahora.timeInMillis)

    @Test
    fun avisaCuandoLosGastosSuperanLosIngresos() {
        val consejos = generarConsejos(
            listOf(mov(10_000, true), mov(15_000, false, Categoria.COMIDA)),
            emptyList(), 0L, ahora, nombre = NOMBRE
        )

        assertTrue(consejos.any { it.contains("más de gastos que de ingresos") })
        assertTrue(consejos.any { it.contains("Comida") })
    }

    @Test
    fun calculaElAhorroMensualParaUnaMeta() {
        val limite = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 30) }.timeInMillis
        val meta = SavingsGoal("1", "Laptop", targetCents = 20_000, savedCents = 0, deadlineMillis = limite)

        val consejos = generarConsejos(listOf(mov(50_000, true)), listOf(meta), 0L, ahora, nombre = NOMBRE)

        // Marzo y abril: 200 / 2 = 100 al mes.
        assertTrue(consejos.any { it.contains("Laptop") && it.contains("100.00") })
    }

    @Test
    fun nuncaSugiereRecortarElAhorro() {
        val consejos = generarConsejos(
            listOf(mov(10_000, true), mov(8_000, false, Categoria.AHORRO), mov(500, false, Categoria.COMIDA)),
            emptyList(), 0L, ahora, nombre = NOMBRE
        )

        assertTrue(consejos.none { it.contains("Ahorro (") })
        assertTrue(consejos.none { it.contains("más de gastos que de ingresos") })
    }

    @Test
    fun sinMovimientosPideRegistrarlos() {
        val consejos = generarConsejos(emptyList(), emptyList(), 0L, ahora, nombre = NOMBRE)
        assertTrue(consejos.single().startsWith("Registra"))
    }
}

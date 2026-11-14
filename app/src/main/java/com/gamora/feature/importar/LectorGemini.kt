package com.gamora.feature.importar

import com.gamora.feature.chatbot.conModeloDeRespaldo
import com.gamora.feature.transactions.CATEGORIAS_GASTO
import com.gamora.feature.transactions.CATEGORIAS_INGRESO
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig

private val ESQUEMA = Schema.obj(
    mapOf(
        "banco" to Schema.string(),
        "periodo" to Schema.string(),
        "movimientos" to Schema.array(
            Schema.obj(
                mapOf(
                    "fecha" to Schema.string("Fecha AAAA-MM-DD"),
                    "descripcion" to Schema.string(),
                    "montoCentavos" to Schema.long("Monto positivo en centavos"),
                    "esIngreso" to Schema.boolean(),
                    "categoria" to Schema.string()
                )
            )
        )
    )
)

private val PROMPT_LECTOR = """
    Eres el lector de documentos de Gamora, una app de finanzas personales en México.
    Recibirás un estado de cuenta bancario o de tarjeta, o un recibo de nómina (PDF o foto).
    Extrae TODOS los cargos y abonos del periodo y responde solo con el JSON pedido:
    - banco: nombre del banco o de la empresa que paga la nómina.
    - periodo: el periodo del documento tal como aparece (p. ej. "1 al 31 oct 2026").
    - fecha: AAAA-MM-DD. Si el documento no muestra el año en cada renglón, dedúcelo del periodo.
    - descripcion: el concepto como aparece, sin números de cuenta ni referencias largas.
    - montoCentavos: entero positivo en centavos (p. ej. $1,234.50 → 123450).
    - esIngreso: true si es abono o depósito; false si es cargo, compra o retiro.
    - categoria: si es ingreso, una de: ${CATEGORIAS_INGRESO.joinToString()}.
      Si es gasto, una de: ${CATEGORIAS_GASTO.joinToString()}. Si dudas, "Otros".
    No incluyas saldos iniciales o finales, totales, subtotales ni renglones repetidos de resumen.
    En estados de tarjeta de crédito, omite los pagos a la propia tarjeta: no son ingresos.
    Si es un recibo de nómina, registra un solo ingreso por el neto pagado, categoría "Salario".
    Si no hay movimientos, devuelve la lista vacía.
""".trimIndent()

/** Envía el documento a Gemini y convierte su respuesta. El documento no se guarda en ningún lado. */
suspend fun leerConGemini(bytes: ByteArray, mime: String): ResultadoLectura {
    val texto = conModeloDeRespaldo { modelo ->
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = modelo,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = ESQUEMA
                },
                systemInstruction = content { text(PROMPT_LECTOR) }
            )
            .generateContent(
                content {
                    inlineData(bytes, mime)
                    text("Extrae los movimientos.")
                }
            )
            .text
            ?.ifBlank { null }
            ?: error("Gemini devolvió una respuesta vacía.")
    }

    return leerRespuestaGemini(texto)
}

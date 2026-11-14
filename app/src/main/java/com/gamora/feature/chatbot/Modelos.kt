package com.gamora.feature.chatbot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// Modelos de Firebase AI Logic en orden de preferencia; los siguientes son respaldo
// cuando el primero está saturado. Cámbialos aquí si Google retira alguno.
internal val MODELOS_GEMINI = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite")

internal fun estaSaturado(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { causa ->
        val texto = causa.message.orEmpty()
        texto.contains("high demand", true) ||
                texto.contains("overloaded", true) ||
                texto.contains("UNAVAILABLE", true) ||
                texto.contains("503") ||
                // Fallas internas pasajeras de Gemini: vale la pena reintentar.
                texto.contains("INTERNAL") ||
                texto.contains("500")
    }

internal fun noDisponible(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { causa ->
        val texto = causa.message.orEmpty()
        texto.contains("not found", true) || texto.contains("is not supported", true)
    }

/**
 * Ejecuta [llamada] con cada modelo de [MODELOS_GEMINI]: si uno está saturado reintenta con espera
 * y luego pasa al siguiente. Cualquier otro error se lanza de inmediato.
 */
internal suspend fun <T> conModeloDeRespaldo(
    alSaturarse: (intento: Int) -> Unit = {},
    llamada: suspend (modelo: String) -> T
): T {
    var ultimoError: Throwable? = null

    modelos@ for (modelo in MODELOS_GEMINI) {
        for (intento in 0 until 2) {
            try {
                return llamada(modelo)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ultimoError = error
                when {
                    estaSaturado(error) -> {
                        alSaturarse(intento)
                        delay(1500L * (intento + 1))
                    }
                    modelo != MODELOS_GEMINI.first() && noDisponible(error) -> continue@modelos
                    else -> throw error
                }
            }
        }
    }

    throw ultimoError ?: IllegalStateException("No hay modelos disponibles.")
}

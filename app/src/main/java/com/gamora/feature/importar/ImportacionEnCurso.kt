package com.gamora.feature.importar

/**
 * Lo leído de un documento mientras la persona lo revisa. Solo en memoria: si el sistema
 * cierra la app, se pierde y hay que volver a importar (nunca se guarda en disco).
 */
object ImportacionEnCurso {
    var resultado: ResultadoLectura? = null

    fun limpiar() {
        resultado = null
    }
}

package com.gamora.core.legal

/**
 * Versión vigente del aviso de privacidad y los términos. Al cambiarla, la app pide aceptarlos de
 * nuevo a quien aceptó una anterior (y las reglas de Firestore deben aceptar la nueva).
 */
const val VERSION_POLITICAS = "1.1"

/** Compara versiones por sus números ("1.10" > "1.9"; "1.1" == "1.1.0"). */
fun compararVersiones(a: String, b: String): Int {
    val x = a.split('.').map { it.trim().toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.trim().toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val diferencia = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (diferencia != 0) return diferencia
    }
    return 0
}

/** Sin versión aceptada (o una anterior a la vigente) hay que pedir aceptar la vigente. */
fun requiereAceptar(aceptada: String?): Boolean =
    aceptada.isNullOrBlank() || compararVersiones(aceptada, VERSION_POLITICAS) < 0

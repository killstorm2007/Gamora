package com.gamora.feature.transactions

/**
 * Movimiento de dinero tal como lo consumen las pantallas.
 *
 * [id] es el identificador remoto en Supabase; [timestamp] se guarda en
 * milisegundos para que la interfaz no tenga que parsear fechas.
 */
data class GamoraMovement(
    val id: String? = null,
    val description: String,
    val category: String,
    val amountCents: Long,
    val isIncome: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

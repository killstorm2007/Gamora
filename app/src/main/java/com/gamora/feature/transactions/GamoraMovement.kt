
package com.gamora.feature.transactions

data class GamoraMovement(
    val description: String,
    val category: String,
    val amountCents: Long,
    val isIncome: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object GamoraTransactionStore {
    val movements: MutableList<GamoraMovement> = mutableListOf()
}

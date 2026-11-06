package com.gamora.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fila de la tabla `public.profiles`.
 */
@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("paternal_surname") val paternalSurname: String = "",
    @SerialName("maternal_surname") val maternalSurname: String = "",
    val phone: String = "",
    val currency: String = "MXN"
) {

    /** Nombre completo para mostrar en la interfaz. */
    val displayName: String
        get() = listOf(fullName, paternalSurname, maternalSurname)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

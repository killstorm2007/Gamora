package com.gamora.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Si el teléfono tiene internet; se actualiza solo. Se inicia en Application. */
object Conexion {

    private val estado = MutableStateFlow(true)
    val enLinea: StateFlow<Boolean> = estado

    fun iniciar(context: Context) {
        val conectividad = context.getSystemService(ConnectivityManager::class.java) ?: return

        fun revisar() {
            val capacidades = conectividad.getNetworkCapabilities(conectividad.activeNetwork)
            estado.value = capacidades?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }

        revisar()
        try {
            conectividad.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = revisar()
                override fun onLost(network: Network) = revisar()
                override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) = revisar()
            })
        } catch (error: Exception) {
            // Sin el aviso de conexión la app funciona igual.
            Log.w("Conexion", "No se pudo vigilar la conexión", error)
        }
    }
}

/**
 * Problemas al cargar los datos de la cuenta (movimientos, metas, presupuestos). Sin esto, un error se
 * vería como una lista vacía y la persona pensaría que perdió su información.
 */
object EstadoDatos {

    private val estado = MutableStateFlow<Map<String, Throwable>>(emptyMap())
    val problemas: StateFlow<Map<String, Throwable>> = estado

    fun reportar(origen: String, error: Throwable) {
        Log.w(origen, "No se pudieron cargar los datos", error)
        estado.value = estado.value + (origen to error)
    }

    fun resuelto(origen: String) {
        if (origen in estado.value) estado.value = estado.value - origen
    }

    fun limpiar() {
        estado.value = emptyMap()
    }
}

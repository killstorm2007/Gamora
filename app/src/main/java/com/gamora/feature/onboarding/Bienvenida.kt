package com.gamora.feature.onboarding

import android.content.Context
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gamora.core.auth.AuthRepository
import com.gamora.core.auth.PreferenciasFinancieras
import com.gamora.core.esperar
import com.gamora.core.ui.Moneda
import com.gamora.core.ui.centavosDe
import com.gamora.core.ui.enDialogo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private const val ARCHIVO = "gamora_bienvenida"

/**
 * Tres pasos para quien entra por primera vez: moneda, presupuesto mensual y primer movimiento.
 * Solo aparece si la cuenta no tiene preferencias financieras ni movimientos.
 * [registrarMovimiento] abre el formulario de ingreso (true) o gasto (false).
 */
fun AppCompatActivity.mostrarBienvenidaSiHaceFalta(
    registrarMovimiento: (esIngreso: Boolean) -> Unit,
    alTerminar: () -> Unit
) {
    val uid = AuthRepository.obtenerUid() ?: return alTerminar()
    val vistas = getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
    if (vistas.getBoolean(uid, false)) return alTerminar()

    lifecycleScope.launch {
        val esNueva = try {
            val usuario = FirebaseFirestore.getInstance().collection("users").document(uid)
            val preferencias = usuario.collection("preferencias").document("financieras").get().esperar()
            val movimientos = usuario.collection("movimientos").limit(1).get().esperar()
            !preferencias.exists() && movimientos.isEmpty
        } catch (_: Exception) {
            // Sin conexión: se intenta la próxima vez.
            return@launch alTerminar()
        }

        // Se marca antes de mostrarla: si la persona la cierra, no vuelve a salir.
        vistas.edit().putBoolean(uid, true).apply()

        if (esNueva && !isFinishing) pasoMoneda(registrarMovimiento, alTerminar) else alTerminar()
    }
}

private fun AppCompatActivity.pasoMoneda(
    registrarMovimiento: (Boolean) -> Unit,
    alTerminar: () -> Unit
) {
    var elegida = Moneda.DISPONIBLES.indexOf(Moneda.actual).coerceAtLeast(0)

    MaterialAlertDialogBuilder(this)
        .setTitle("1 de 3 · ¿En qué moneda manejas tu dinero?")
        .setSingleChoiceItems(
            Moneda.DISPONIBLES.map { Moneda.NOMBRES.getValue(it) }.toTypedArray(),
            elegida
        ) { _, indice -> elegida = indice }
        .setCancelable(false)
        .setNegativeButton("Omitir") { _, _ -> alTerminar() }
        .setPositiveButton("Siguiente") { _, _ ->
            pasoPresupuesto(Moneda.DISPONIBLES[elegida], registrarMovimiento, alTerminar)
        }
        .show()
}

private fun AppCompatActivity.pasoPresupuesto(
    moneda: String,
    registrarMovimiento: (Boolean) -> Unit,
    alTerminar: () -> Unit
) {
    val campo = TextInputEditText(this).apply {
        hint = "Presupuesto mensual (ej. 8000)"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        maxLines = 1
    }

    fun guardar(presupuesto: Long) {
        AuthRepository.guardarPreferenciasFinancieras(
            PreferenciasFinancieras(moneda = moneda, presupuestoMensualCentavos = presupuesto),
            onSuccess = {},
            onError = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
        Moneda.cambiar(this, moneda)
        pasoPrimerMovimiento(registrarMovimiento, alTerminar)
    }

    MaterialAlertDialogBuilder(this)
        .setTitle("2 de 3 · ¿Cuánto quieres gastar al mes como máximo?")
        .setMessage("Gamora te avisará cuando vayas en el 80 % y si te pasas. Puedes cambiarlo después en Perfil.")
        .setView(campo.enDialogo())
        .setCancelable(false)
        .setNegativeButton("Omitir") { _, _ -> guardar(0L) }
        .setPositiveButton("Siguiente") { _, _ ->
            val centavos = centavosDe(campo.text?.toString().orEmpty())
            guardar(centavos?.takeIf { it in 1..100_000_000_000L } ?: 0L)
        }
        .show()
}

private fun AppCompatActivity.pasoPrimerMovimiento(
    registrarMovimiento: (Boolean) -> Unit,
    alTerminar: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle("3 de 3 · Registra tu primer movimiento")
        .setMessage(
            "Empieza por tu ingreso del mes o por un gasto de hoy. Si se repite cada mes " +
                    "(salario, renta, suscripciones), marca «Repetir cada mes» y Gamora lo registrará solo."
        )
        .setCancelable(false)
        .setNeutralButton("Después") { _, _ -> alTerminar() }
        // Al registrar no se llama a alTerminar: el permiso de avisos se pedirá la próxima vez
        // para no encimarlo con el formulario.
        .setNegativeButton("Un gasto") { _, _ -> registrarMovimiento(false) }
        .setPositiveButton("Un ingreso") { _, _ -> registrarMovimiento(true) }
        .show()
}

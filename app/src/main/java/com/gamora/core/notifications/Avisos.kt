package com.gamora.core.notifications

import com.gamora.core.texto
import com.gamora.core.nombre
import com.gamora.core.Categoria
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gamora.MainActivity
import com.gamora.R
import com.gamora.core.esperar
import com.gamora.core.ui.Moneda
import com.gamora.core.ui.dinero
import com.gamora.feature.budget.PresupuestoStore
import com.gamora.feature.transactions.Recurrentes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val CANAL = "gamora_avisos"
private const val TRABAJO_DIARIO = "gamora_avisos_diarios"
private const val TRABAJO_AHORA = "gamora_avisos_ahora"
private const val DIA_MS = 24L * 60 * 60 * 1000

fun crearCanalNotificaciones(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CANAL, texto(R.string.avisos_avisos_de_gamora), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = texto(R.string.avisos_presupuesto_metas_y_resumen)
        }
    )
}

fun puedeNotificar(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

/** Revisión diaria; también registra los movimientos recurrentes aunque no se abra la app. */
fun programarAvisos(context: Context) {
    val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        TRABAJO_DIARIO,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<AvisosWorker>(1, TimeUnit.DAYS).setConstraints(conRed).build()
    )
}

/** Revisión inmediata, p. ej. al salir de la app después de registrar gastos. */
fun revisarAvisosAhora(context: Context) {
    val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        TRABAJO_AHORA,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<AvisosWorker>().setConstraints(conRed).build()
    )
}

fun cancelarAvisos(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(TRABAJO_DIARIO)
    WorkManager.getInstance(context).cancelUniqueWork(TRABAJO_AHORA)
}

/** Primer día del periodo actual según el día de inicio elegido en Preferencias financieras. */
fun inicioPeriodo(ahora: Long, diaInicio: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = ahora
        val dia = diaInicio.coerceIn(1, 28)
        if (get(Calendar.DAY_OF_MONTH) < dia) add(Calendar.MONTH, -1)
        set(Calendar.DAY_OF_MONTH, dia)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** 100 si ya se pasó del presupuesto, 80 si va en 80 % o más; null si va bien o no hay presupuesto. */
fun umbralPresupuesto(gastos: Long, presupuesto: Long): Int? = when {
    presupuesto <= 0 -> null
    gastos >= presupuesto -> 100
    gastos * 100 >= presupuesto * 80 -> 80
    else -> null
}

class AvisosWorker(
    context: Context,
    parametros: WorkerParameters
) : CoroutineWorker(context, parametros) {

    // Recuerda qué avisos ya se mostraron para no repetirlos.
    private val enviados = context.getSharedPreferences("gamora_avisos_enviados", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        return try {
            Recurrentes.procesar(uid)
            if (puedeNotificar(applicationContext)) revisar(uid)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun revisar(uid: String) {
        val usuario = FirebaseFirestore.getInstance().collection("users").document(uid)
        val preferencias = usuario.collection("preferencias")

        val avisos = preferencias.document("notificaciones").get().esperar()
        val financieras = preferencias.document("financieras").get().esperar()

        val moneda = financieras.getString("moneda") ?: Moneda.actual
        val ahora = System.currentTimeMillis()

        if (avisos.getBoolean("alertasPresupuesto") != false) {
            val presupuesto = financieras.getLong("presupuestoMensualCentavos") ?: 0L
            val inicio = inicioPeriodo(ahora, (financieras.getLong("diaInicioPeriodo") ?: 1L).toInt())

            val limites = PresupuestoStore.leer(preferencias.document("presupuestos").get().esperar().data)

            val gastosPeriodo = if (presupuesto > 0 || limites.isNotEmpty()) {
                usuario.collection("movimientos")
                    .whereGreaterThanOrEqualTo("fecha", inicio).get().esperar().documents
                    .filter { it.getBoolean("esIngreso") == false }
            } else emptyList()

            // Límites por categoría.
            gastosPeriodo
                .groupBy { Categoria.porClave(it.getString("categoria")) ?: Categoria.OTROS }
                .mapValues { (_, docs) -> docs.sumOf { it.getLong("montoCentavos") ?: 0L } }
                .forEach { (cat, gastado) ->
                    val limite = limites[cat] ?: return@forEach
                    // Los avisos ya enviados se recuerdan por clave; el texto usa el nombre traducido.
                    val categoria = cat.clave
                    val nombre = cat.nombre(applicationContext)
                    when (umbralPresupuesto(gastado, limite)) {
                        100 -> avisarUnaVez(
                            "categoria_${categoria}_${inicio}_100",
                            texto(R.string.avisos_te_pasaste_en, nombre),
                            texto(R.string.avisos_llevas_de_este_periodo, dinero(gastado, moneda), dinero(limite, moneda))
                        ).also { enviados.edit().putBoolean("categoria_${categoria}_${inicio}_80", true).apply() }

                        80 -> avisarUnaVez(
                            "categoria_${categoria}_${inicio}_80",
                            texto(R.string.avisos_vas_en_el, nombre, gastado * 100 / limite),
                            texto(R.string.avisos_te_quedan_en_para, dinero(limite - gastado, moneda), nombre)
                        )
                    }
                }

            if (presupuesto > 0) {
                val gastos = gastosPeriodo
                    .filter { it.getString("categoria") != Categoria.AHORRO.clave }
                    .sumOf { it.getLong("montoCentavos") ?: 0L }

                when (umbralPresupuesto(gastos, presupuesto)) {
                    100 -> avisarUnaVez(
                        "presupuesto_${inicio}_100",
                        texto(R.string.avisos_superaste_tu_presupuesto),
                        texto(R.string.avisos_llevas_de_este_periodo, dinero(gastos, moneda), dinero(presupuesto, moneda))
                    ).also { enviados.edit().putBoolean("presupuesto_${inicio}_80", true).apply() }

                    80 -> avisarUnaVez(
                        "presupuesto_${inicio}_80",
                        texto(R.string.avisos_vas_en_el_de, gastos * 100 / presupuesto),
                        texto(R.string.avisos_te_quedan_para_este, dinero(presupuesto - gastos, moneda))
                    )
                }
            }
        }

        if (avisos.getBoolean("recordatoriosMetas") != false) {
            usuario.collection("metas").get().esperar().documents.forEach { meta ->
                val objetivo = meta.getLong("objetivoCentavos") ?: return@forEach
                val ahorrado = meta.getLong("ahorradoCentavos") ?: 0L
                val limite = meta.getLong("fechaLimite") ?: return@forEach
                if (ahorrado >= objetivo) return@forEach

                val dias = ((limite - ahora + DIA_MS - 1) / DIA_MS).toInt()
                val nombre = meta.getString("nombre").orEmpty()
                val faltan = dinero(objetivo - ahorrado, moneda)

                when {
                    dias in 0..1 -> avisarUnaVez(
                        "meta_${meta.id}_1", texto(R.string.avisos_tu_meta_vence_manana, nombre), texto(R.string.avisos_te_faltan_para_lograrla, faltan)
                    )
                    dias in 2..7 -> avisarUnaVez(
                        "meta_${meta.id}_7", texto(R.string.avisos_quedan_dias_para, dias, nombre), texto(R.string.avisos_te_faltan_para_lograrla, faltan)
                    )
                }
            }
        }

        val calendario = Calendar.getInstance()
        if (avisos.getBoolean("resumenSemanal") == true &&
            calendario.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        ) {
            val semana = usuario.collection("movimientos")
                .whereGreaterThanOrEqualTo("fecha", ahora - 7 * DIA_MS).get().esperar().documents
            val ingresos = semana.filter { it.getBoolean("esIngreso") == true }
                .sumOf { it.getLong("montoCentavos") ?: 0L }
            val gastos = semana.filter { it.getBoolean("esIngreso") == false }
                .sumOf { it.getLong("montoCentavos") ?: 0L }

            avisarUnaVez(
                "resumen_${calendario.get(Calendar.YEAR)}_${calendario.get(Calendar.WEEK_OF_YEAR)}",
                texto(R.string.avisos_tu_semana_en_gamora),
                texto(R.string.avisos_ingresos_gastos_balance, dinero(ingresos, moneda), dinero(gastos, moneda), dinero(ingresos - gastos, moneda))
            )
        }
    }

    private fun avisarUnaVez(clave: String, titulo: String, texto: String) {
        if (enviados.getBoolean(clave, false)) return

        val abrir = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(applicationContext, CANAL)
            .setSmallIcon(R.drawable.ic_sparkle)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(clave.hashCode(), notificacion)
            enviados.edit().putBoolean(clave, true).apply()
        } catch (_: SecurityException) {
            // Se retiró el permiso entre la revisión y el aviso.
        }
    }
}

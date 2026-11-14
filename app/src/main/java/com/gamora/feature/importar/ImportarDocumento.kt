package com.gamora.feature.importar

import com.gamora.core.textoPlural
import com.gamora.core.texto
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gamora.R
import com.gamora.core.ui.dp
import com.gamora.core.ui.enDialogo
import com.gamora.feature.importar.sinia.leerTablas
import com.gamora.feature.importar.sinia.textoDePdf
import com.gamora.feature.importar.sinia.reconocerImagen
import com.gamora.feature.importar.sinia.reconocerPdf
import com.gamora.feature.transactions.GamoraTransactionStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val MAXIMO_BYTES = 15L * 1024 * 1024
private const val PREFERENCIAS = "gamora_importar"
private const val CLAVE_CONSENTIMIENTO = "consentimiento_gemini"

private val TIPOS = arrayOf("application/pdf", "image/*", "text/xml", "application/xml")

/** Qué es el archivo según sus primeros bytes (el tipo que reporta Android no siempre es fiable). */
internal enum class TipoDocumento { PDF, XML, IMAGEN, OTRO }

internal fun detectarTipo(bytes: ByteArray, mimeReportado: String?): TipoDocumento {
    val inicio = bytes.take(64).toByteArray().toString(Charsets.ISO_8859_1).trimStart('\u00EF', '\u00BB', '\u00BF', ' ', '\n', '\r', '\t')
    return when {
        inicio.startsWith("%PDF") -> TipoDocumento.PDF
        inicio.startsWith("<") -> TipoDocumento.XML
        mimeReportado?.startsWith("image/") == true -> TipoDocumento.IMAGEN
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> TipoDocumento.IMAGEN
        bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> TipoDocumento.IMAGEN
        else -> TipoDocumento.OTRO
    }
}

private const val ESPERA_CUOTA_SEGUNDOS = 45

/** Gemini respondió que se agotó la cuota (por minuto o por día) del proyecto. */
fun esLimiteDeCuota(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { causa ->
        val texto = "${causa.javaClass.simpleName} ${causa.message.orEmpty()}"
        listOf("QuotaExceeded", "RESOURCE_EXHAUSTED", "429", "Quota exceeded").any { texto.contains(it, ignoreCase = true) }
    }

/** Explica qué pasó en lugar de culpar siempre a la conexión. */
internal fun mensajeDeError(error: Throwable): String {
    val detalle = generateSequence(error) { it.cause }.joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }

    fun contiene(vararg palabras: String) = palabras.any { detalle.contains(it, ignoreCase = true) }

    return when {
        error is IllegalArgumentException ->
            texto(R.string.importar_documento_gemini_no_pudo_leer)
        esLimiteDeCuota(error) ->
            texto(R.string.importar_documento_llegaste_al_limite_de)
        contiene("ResponseStopped", "MAX_TOKENS", "token count", "exceeds the maximum", "too large", "payload size") ->
            texto(R.string.importar_documento_el_documento_es_muy)
        contiene("RequestTimeout", "DEADLINE_EXCEEDED", "timed out", "timeout") ->
            texto(R.string.importar_documento_gemini_tardo_demasiado_en)
        contiene("UnknownHost", "ConnectException", "SocketException", "Unable to resolve host", "No address associated") ->
            texto(R.string.importar_documento_no_se_pudo_leer)
        error is java.io.IOException ->
            texto(R.string.importar_documento_no_se_pudo_abrir)
        contiene("ServerException", "INTERNAL", "500") ->
            texto(R.string.importar_documento_gemini_tuvo_un_problema)
        else -> texto(R.string.importar_documento_no_se_pudo_leer_2)
    }
}

/**
 * Botón "Importar estado de cuenta": consentimiento, selector de archivos, contraseña del PDF,
 * lectura y paso a la revisión. Se crea como propiedad del fragment (antes de que inicie).
 */
class ImportadorDocumentos(private val fragment: Fragment) {

    private var lectura: Job? = null
    private var canceladaPorUsuario = false

    private val selector = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) leer(uri)
    }

    private val contexto: Context get() = fragment.requireContext()

    fun iniciar() {
        val preferencias = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

        if (preferencias.getBoolean(CLAVE_CONSENTIMIENTO, false)) {
            selector.launch(TIPOS)
            return
        }

        MaterialAlertDialogBuilder(contexto)
            .setTitle(texto(R.string.importar_documento_importar_estado_de_cuenta))
            .setMessage(
                texto(R.string.importar_documento_gamora_lee_tu_estado)
            )
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.importar_documento_aceptar)) { _, _ ->
                preferencias.edit().putBoolean(CLAVE_CONSENTIMIENTO, true).apply()
                selector.launch(TIPOS)
            }
            .show()
    }

    private fun aviso(mensaje: String) {
        if (fragment.isAdded) Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun leer(uri: Uri) {
        val resolver = contexto.contentResolver

        val tamano = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        if (tamano != null && tamano > MAXIMO_BYTES) {
            aviso(texto(R.string.importar_documento_el_archivo_pesa_mas))
            return
        }

        canceladaPorUsuario = false
        val progreso = mostrarProgreso()
        val app = contexto.applicationContext

        lectura = fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: error(texto(R.string.importar_no_se_pudo_abrir))

                if (bytes.size > MAXIMO_BYTES) {
                    aviso(texto(R.string.importar_documento_el_archivo_pesa_mas))
                    return@launch
                }

                val resultado = when (detectarTipo(bytes, resolver.getType(uri))) {
                    TipoDocumento.XML -> withContext(Dispatchers.Default) { leerNominaCfdi(bytes) }
                    TipoDocumento.PDF -> {
                        val pdf = abrirPdf(bytes, progreso) ?: return@launch
                        leerEnTelefono { leerPdfSinIA(pdf) } ?: leerConReintento(pdf, "application/pdf")
                    }
                    TipoDocumento.IMAGEN -> {
                        leerEnTelefono { leerTablas(reconocerImagen(bytes)) }
                            ?: leerConReintento(bytes, resolver.getType(uri) ?: "image/jpeg")
                    }
                    TipoDocumento.OTRO -> {
                        aviso(texto(R.string.importar_documento_ese_tipo_de_archivo))
                        return@launch
                    }
                }

                if (resultado.movimientos.isEmpty()) {
                    aviso(texto(R.string.importar_documento_no_encontramos_movimientos_en))
                    return@launch
                }

                if (resultado.descartados > 0) {
                    aviso(
                        textoPlural(R.plurals.importar_documento_renglones_no_leidos, resultado.descartados, resultado.descartados)
                    )
                }

                ImportacionEnCurso.resultado = resultado.copy(
                    movimientos = marcarRepetidos(resultado.movimientos, GamoraTransactionStore.movements)
                )
                if (fragment.isAdded) fragment.findNavController().navigate(R.id.importReviewFragment)
            } catch (error: CancellationException) {
                // P. ej. la pantalla se recreó a media lectura: avisar en vez de cerrar en silencio.
                if (!canceladaPorUsuario) {
                    Toast.makeText(app, texto(R.string.importar_documento_se_interrumpio_la_lectura), Toast.LENGTH_LONG).show()
                }
                throw error
            } catch (error: NoEsNominaException) {
                aviso(error.message.orEmpty())
            } catch (error: Exception) {
                // Tipo y mensaje del servidor (p. ej. "500 INTERNAL"); nunca el contenido del documento.
                Log.w("ImportadorDocumentos", "Lectura fallida: ${error.javaClass.simpleName}: ${error.message?.take(300)}")
                aviso(mensajeDeError(error))
            } finally {
                progreso.dismiss()
            }
        }
    }

    /** Pide la contraseña las veces que haga falta; null si la persona cancela. */
    private suspend fun abrirPdf(bytes: ByteArray, progreso: AlertDialog): ByteArray? {
        var contrasena: CharArray? = null
        var error: String? = null

        while (true) {
            try {
                return withContext(Dispatchers.Default) { prepararPdf(bytes, contrasena) }
            } catch (_: ContrasenaRequeridaException) {
                error = null
            } catch (_: ContrasenaIncorrectaException) {
                error = texto(R.string.importar_documento_contrasena_incorrecta)
            }

            progreso.hide()
            contrasena = pedirContrasena(error) ?: return null
            progreso.show()
        }
    }

    private suspend fun pedirContrasena(error: String?): CharArray? = suspendCancellableCoroutine { continuacion ->
        val campo = TextInputEditText(contexto).apply {
            hint = texto(R.string.importar_documento_contrasena_del_pdf)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
        }

        fun responder(valor: CharArray?) {
            // El texto del campo se borra en cuanto se lee.
            campo.setText("")
            if (continuacion.isActive) continuacion.resume(valor)
        }

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(texto(R.string.importar_documento_este_pdf_tiene_contrasena))
            .setMessage(texto(R.string.importar_documento_escribela_para_abrirlo_en))
            .setView(campo.enDialogo())
            .setCancelable(false)
            .setNegativeButton(texto(R.string.import_review_cancelar)) { _, _ -> responder(null) }
            .setPositiveButton(texto(R.string.importar_documento_abrir)) { _, _ ->
                val texto = campo.text ?: ""
                responder(CharArray(texto.length) { texto[it] })
            }
            .create()

        campo.error = error
        continuacion.invokeOnCancellation { dialogo.dismiss() }
        dialogo.show()
    }

    /**
     * Primero se intenta leer en el teléfono, sin enviar el documento a ningún lado. Si el formato
     * no se reconoce (o algo falla), devuelve null y el documento se lee con Gemini.
     */
    private suspend fun leerEnTelefono(lectura: suspend () -> ResultadoLectura?): ResultadoLectura? =
        try {
            withContext(Dispatchers.Default) { lectura() }
                ?.also { aviso(texto(R.string.importar_documento_leido_en_tu_telefono)) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("ImportadorDocumentos", "Lectura en el teléfono fallida: ${error.javaClass.simpleName}")
            null
        }

    /** Texto del PDF; si es escaneado, reconocimiento de texto página por página. */
    private suspend fun leerPdfSinIA(pdf: ByteArray): ResultadoLectura? {
        val texto = textoDePdf(pdf)
        leerTablas(texto.palabras)?.let { return it }
        if (!texto.esEscaneo) return null

        val reconocidas = reconocerPdf(pdf) { pagina, total ->
            textoProgreso?.post { textoProgreso?.text = texto(R.string.importar_documento_reconociendo_el_texto_pagina, pagina, total) }
        }
        textoProgreso?.post { textoProgreso?.text = texto(R.string.importar_documento_leyendo_tu_documento) }
        return leerTablas(reconocidas)
    }

    /**
     * Si se agotó la cuota por minuto, espera y lo intenta una vez más mostrando la cuenta regresiva.
     * Si vuelve a fallar (p. ej. se acabó la cuota del día), el error sube y se explica a la persona.
     */
    private suspend fun leerConReintento(bytes: ByteArray, mime: String): ResultadoLectura =
        try {
            leerConGemini(bytes, mime)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!esLimiteDeCuota(error)) throw error

            for (restantes in ESPERA_CUOTA_SEGUNDOS downTo 1) {
                textoProgreso?.text = texto(R.string.importar_documento_hay_muchas_lecturas_en, restantes)
                delay(1000)
            }
            textoProgreso?.text = texto(R.string.importar_documento_leyendo_tu_documento)
            leerConGemini(bytes, mime)
        }

    private var textoProgreso: TextView? = null

    private fun mostrarProgreso(): AlertDialog {
        val contenido = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(contexto.dp(24), contexto.dp(20), contexto.dp(24), contexto.dp(8))
            addView(CircularProgressIndicator(contexto).apply { isIndeterminate = true })
            addView(TextView(contexto).apply {
                textoProgreso = this
                text = texto(R.string.importar_documento_leyendo_tu_documento)
                textSize = 15f
                setPadding(contexto.dp(16), 0, 0, 0)
                setTextColor(contexto.getColor(R.color.gamora_text_primary))
                // Que el aviso de reintento se acomode en varias líneas.
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        return MaterialAlertDialogBuilder(contexto)
            .setView(contenido)
            .setCancelable(false)
            .setNegativeButton(texto(R.string.import_review_cancelar)) { _, _ ->
                canceladaPorUsuario = true
                lectura?.cancel()
            }
            .show()
    }
}

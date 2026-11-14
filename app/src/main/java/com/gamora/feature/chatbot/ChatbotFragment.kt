
package com.gamora.feature.chatbot

import com.gamora.core.ui.Moneda
import com.gamora.core.ui.localeApp
import com.gamora.core.texto
import com.gamora.core.nombre
import com.gamora.core.ui.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gamora.R
import com.gamora.core.auth.AuthRepository
import com.gamora.feature.goals.GamoraGoalStore
import com.gamora.feature.transactions.GamoraTransactionStore
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val SUGERENCIAS = listOf(
    texto(R.string.chatbot_analiza_mis_gastos_de),
    texto(R.string.chatbot_en_que_puedo_recortar),
    texto(R.string.chatbot_como_voy_con_mis),
    texto(R.string.chatbot_hazme_un_plan_de)
)

class ChatbotFragment : Fragment(R.layout.fragment_chatbot) {

    // Lo que se envía a Gemini como memoria de la conversación.
    private val historial = mutableListOf<MensajeChat>()

    private var iaAutorizada = false
    private var presupuestoCentavos = 0L
    private var esperando = false

    private lateinit var contenedor: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var botonEnviar: View
    private lateinit var sugerencias: ChipGroup

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        contenedor = view.findViewById(R.id.layoutChatMessages)
        scroll = view.findViewById(R.id.scrollChat)
        input = view.findViewById(R.id.txtChatMessage)
        botonEnviar = view.findViewById(R.id.btnSendMessage)
        sugerencias = view.findViewById(R.id.groupSugerencias)

        view.findViewById<View>(R.id.btnBackChat).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnBorrarChat).setOnClickListener {
            confirmarBorrado()
        }

        botonEnviar.setOnClickListener { enviar(input.text.toString()) }

        input.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEND) {
                enviar(input.text.toString())
                true
            } else false
        }

        SUGERENCIAS.forEach { texto ->
            sugerencias.addView(
                Chip(requireContext()).apply {
                    text = texto
                    setOnClickListener { enviar(texto) }
                }
            )
        }

        habilitar(false)
        cargarConversacion()
    }

    private fun cargarConversacion() {
        AuthRepository.obtenerConsentimiento(
            onSuccess = { consentimiento ->
                iaAutorizada = consentimiento.iaAutorizada

                ChatRepository.cargar(
                    onSuccess = { mensajes ->
                        if (!isAdded || view == null) return@cargar

                        historial.clear()
                        historial += mensajes
                        mensajes.forEach { agregarBurbuja(it.texto, it.esUsuario) }

                        if (!iaAutorizada && mensajes.isEmpty()) {
                            agregarBurbuja(
                                texto(R.string.chatbot_puedo_darte_consejos_generales),
                                false
                            )
                        }

                        sugerencias.visibility = if (mensajes.isEmpty()) View.VISIBLE else View.GONE
                        habilitar(true)
                        bajar(false)
                    },
                    onError = ::mostrarErrorInicial
                )
            },
            onError = ::mostrarErrorInicial
        )

        AuthRepository.obtenerPreferenciasFinancieras(
            onSuccess = { presupuestoCentavos = it.presupuestoMensualCentavos },
            onError = {}
        )
    }

    private fun mostrarErrorInicial(mensaje: String) {
        if (!isAdded || view == null) return
        agregarBurbuja(texto(R.string.chatbot_no_pude_cargar_la, mensaje), false)
        habilitar(true)
    }

    private fun enviar(texto: String) {
        val mensaje = texto.trim()
        if (mensaje.isBlank() || esperando) return

        input.text.clear()
        sugerencias.visibility = View.GONE

        val pregunta = MensajeChat(esUsuario = true, texto = mensaje)
        agregarBurbuja(mensaje, true)
        ChatRepository.guardar(pregunta)

        val burbujaRespuesta = agregarBurbuja(texto(R.string.chatbot_escribiendo), false)
        habilitar(false)
        bajar()

        // Copia del historial previo: la pregunta va aparte, después de él.
        val memoria = historial.toList()
        historial += pregunta

        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = runCatching {
                val conversacion = aContenido(memoria) + content(role = "user") { text(mensaje) }
                generarConRespaldo(conversacion.toList(), burbujaRespuesta)
            }

            resultado.onSuccess { respuesta ->
                burbujaRespuesta.text = respuesta
                MensajeChat(esUsuario = false, texto = respuesta).also {
                    historial += it
                    ChatRepository.guardar(it)
                }
            }

            resultado.onFailure { error ->
                Log.w("ChatbotFragment", "Error al consultar Gemini", error)
                burbujaRespuesta.text = mensajeDeError(error)
            }

            habilitar(true)
            bajar()
        }
    }

    /** Gemini espera que la conversación empiece con un mensaje del usuario. */
    private fun aContenido(mensajes: List<MensajeChat>): Array<Content> =
        mensajes.dropWhile { !it.esUsuario }
            .map { mensaje ->
                content(role = if (mensaje.esUsuario) "user" else "model") { text(mensaje.texto) }
            }
            .toTypedArray()

    /**
     * Prueba cada modelo de [MODELOS_GEMINI]; si uno está saturado reintenta con espera
     * y luego pasa al siguiente. Cualquier otro error se lanza de inmediato.
     */
    private suspend fun generarConRespaldo(
        conversacion: List<Content>,
        burbuja: TextView
    ): String {
        var ultimoError: Throwable? = null

        modelos@ for (modelo in MODELOS_GEMINI) {
            for (intento in 0 until 2) {
                try {
                    return crearModelo(modelo)
                        .generateContent(conversacion.first(), *conversacion.drop(1).toTypedArray())
                        .text
                        ?.trim()
                        ?.ifBlank { null }
                        ?: error("Gemini devolvió una respuesta vacía.")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    ultimoError = error

                    when {
                        estaSaturado(error) -> {
                            Log.w("ChatbotFragment", "$modelo saturado (intento ${intento + 1})", error)
                            burbuja.text = texto(R.string.chatbot_escribiendo_hay_mucha_demanda)
                            delay(1500L * (intento + 1))
                        }

                        // Un modelo de respaldo que no existe en el proyecto: probar el siguiente.
                        modelo != MODELOS_GEMINI.first() && noDisponible(error) -> continue@modelos

                        else -> throw error
                    }
                }
            }
        }

        throw ultimoError ?: IllegalStateException("No hay modelos disponibles.")
    }

    /** Se crea en cada mensaje para que las instrucciones lleven los datos al día. */
    // Tokens de App Check de un solo uso: un token interceptado no sirve para hacer más llamadas.
    private fun crearModelo(modelo: String) = Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true)
        .generativeModel(
            modelName = modelo,
            systemInstruction = content { text(instrucciones()) }
        )

    private fun instrucciones(): String = buildString {
        appendLine("Eres Gamora AI, el asistente financiero de Gamora, una app de finanzas personales para personas en México y Estados Unidos.")
        // Responde en el idioma de la app (Perfil › Idioma), aunque estas instrucciones estén en español.
        val idioma = if (localeApp().language == "en") "inglés" else "español"
        appendLine("Responde siempre en $idioma, con un tono cercano y claro, en respuestas breves (máximo 2 o 3 párrafos o una lista corta).")
        appendLine("No uses formato Markdown (sin asteriscos ni #); para listas usa guiones simples.")
        appendLine("Usa la moneda de la persona (${Moneda.actual}).")
        appendLine("Recuerda lo que la persona te ha contado en la conversación (planes, situación, gustos) y úsalo en tus respuestas.")
        appendLine("No des asesoría de inversión personalizada ni recomiendes productos financieros específicos; sugiere consultar a un profesional cuando aplique.")
        appendLine("Si te preguntan algo ajeno a las finanzas personales, redirige la conversación amablemente.")

        if (iaAutorizada) {
            appendLine()
            appendLine("Basa tus recomendaciones en los datos reales de la persona: cita categorías, montos y movimientos concretos, y propone acciones con cifras.")
            appendLine("Datos financieros actuales:")
            append(
                construirContextoFinanciero(
                    GamoraTransactionStore.movements,
                    GamoraGoalStore.goals,
                    presupuestoCentavos,
                    nombre = { it.nombre(requireContext()) }
                )
            )
        } else {
            appendLine("No tienes acceso a sus datos financieros porque no lo ha autorizado; da consejos generales y, si pide un análisis de sus gastos, explícale que puede activarlo en Perfil › Preferencias de inteligencia artificial.")
        }
    }

    /** Explica el fallo en palabras simples; en la versión de depuración agrega el error técnico. */
    private fun mensajeDeError(error: Throwable): String {
        val tecnico = generateSequence(error) { it.cause }
            .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message}" }

        val amigable = when {
            tecnico.contains("ServiceDisabled", true) ||
                    tecnico.contains("API has not been used", true) ||
                    tecnico.contains("is disabled", true) ||
                    tecnico.contains("SERVICE_DISABLED", true) ->
                texto(R.string.chatbot_el_asistente_todavia_no)

            tecnico.contains("not found", true) ||
                    tecnico.contains("NOT_FOUND", true) ||
                    tecnico.contains("is not supported", true) ->
                texto(R.string.chatbot_el_modelo_de_ia)

            estaSaturado(error) ->
                texto(R.string.chatbot_el_asistente_tiene_mucha)

            tecnico.contains("quota", true) || tecnico.contains("RESOURCE_EXHAUSTED", true) ->
                texto(R.string.chatbot_se_alcanzo_el_limite)

            tecnico.contains("App Check", true) ->
                texto(R.string.chatbot_la_app_no_paso)

            tecnico.contains("Unable to resolve host", true) || tecnico.contains("timeout", true) ->
                texto(R.string.chatbot_no_hay_conexion_a)

            else -> texto(R.string.chatbot_no_pude_responder_en)
        }

        val depurable = (requireContext().applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        return if (depurable) texto(R.string.chatbot_n_n_detalle_tecnico, amigable, tecnico) else amigable
    }

    private fun confirmarBorrado() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(texto(R.string.chatbot_borrar_conversacion))
            .setMessage(texto(R.string.chatbot_gamora_ai_olvidara_todo))
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.chatbot_borrar)) { _, _ ->
                ChatRepository.borrarTodo(
                    onSuccess = {
                        if (!isAdded || view == null) return@borrarTodo
                        historial.clear()
                        // El saludo inicial (primer hijo del XML) se conserva.
                        contenedor.removeViews(1, contenedor.childCount - 1)
                        sugerencias.visibility = View.VISIBLE
                    },
                    onError = { mensaje ->
                        if (isAdded) agregarBurbuja(mensaje, false)
                    }
                )
            }
            .show()
    }

    private fun habilitar(activo: Boolean) {
        esperando = !activo
        botonEnviar.isEnabled = activo
        botonEnviar.alpha = if (activo) 1f else 0.5f
    }

    private fun bajar(animado: Boolean = true) = scroll.post {
        if (animado) scroll.fullScroll(View.FOCUS_DOWN)
        else scroll.scrollTo(0, contenedor.height)
    }

    private fun agregarBurbuja(mensaje: String, esUsuario: Boolean): TextView {
        val contexto = requireContext()

        val burbuja = TextView(contexto).apply {
            text = mensaje
            textSize = 15f
            setLineSpacing(dp(3).toFloat(), 1f)
            maxWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()
            setTextIsSelectable(true)
            setTextColor(
                ContextCompat.getColor(
                    contexto,
                    if (esUsuario) R.color.gamora_on_accent else R.color.gamora_text_primary
                )
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = ContextCompat.getDrawable(
                contexto,
                if (esUsuario) R.drawable.bg_chat_user_message else R.drawable.bg_chat_message
            )
        }

        contenedor.addView(
            burbuja,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                gravity = if (esUsuario) Gravity.END else Gravity.START
            }
        )

        return burbuja
    }

}

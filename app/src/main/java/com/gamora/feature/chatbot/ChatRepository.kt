package com.gamora.feature.chatbot

import com.gamora.core.auth.AuthRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class MensajeChat(
    val esUsuario: Boolean,
    val texto: String,
    val fecha: Long = System.currentTimeMillis()
)

/** Conversación con el asistente en users/{uid}/chat. */
object ChatRepository {

    // Mensajes que se recargan y se envían a Gemini como memoria.
    const val LIMITE_HISTORIAL = 60L

    private fun coleccion(): CollectionReference? =
        AuthRepository.obtenerUid()?.let { uid ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("chat")
        }

    fun cargar(
        onSuccess: (List<MensajeChat>) -> Unit,
        onError: (String) -> Unit
    ) {
        val chat = coleccion() ?: return onError("No hay una sesión activa.")

        chat.orderBy("fecha", Query.Direction.DESCENDING)
            .limit(LIMITE_HISTORIAL)
            .get()
            .addOnSuccessListener { resultado ->
                onSuccess(
                    resultado.documents.map { doc ->
                        MensajeChat(
                            esUsuario = doc.getString("rol") == "usuario",
                            texto = doc.getString("texto").orEmpty(),
                            fecha = doc.getLong("fecha") ?: 0L
                        )
                    }.reversed()
                )
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo cargar la conversación.") }
    }

    /** Si falla, el mensaje solo se pierde del historial; la conversación en pantalla sigue. */
    fun guardar(mensaje: MensajeChat) {
        coleccion()?.add(
            mapOf(
                "rol" to if (mensaje.esUsuario) "usuario" else "modelo",
                "texto" to mensaje.texto.take(8000),
                "fecha" to mensaje.fecha
            )
        )
    }

    fun borrarTodo(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val chat = coleccion() ?: return onError("No hay una sesión activa.")
        val db = FirebaseFirestore.getInstance()

        chat.get()
            .continueWithTask { consulta ->
                consulta.exception?.let { throw it }

                // Un batch admite como máximo 500 escrituras.
                Tasks.whenAll(
                    consulta.result.documents.chunked(500).map { grupo ->
                        db.batch().apply { grupo.forEach { delete(it.reference) } }.commit()
                    }
                )
            }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo borrar la conversación.") }
    }
}

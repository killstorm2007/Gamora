
package com.gamora.feature.chatbot

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gamora.R

class ChatbotFragment : Fragment(R.layout.fragment_chatbot) {

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<EditText>(R.id.txtChatMessage)
        val sendButton = view.findViewById<Button>(R.id.btnSendMessage)
        val messages = view.findViewById<LinearLayout>(R.id.layoutChatMessages)

        sendButton.setOnClickListener {
            val message = input.text.toString().trim()

            if (message.isBlank()) return@setOnClickListener

            addMessage(messages, message, true)
            input.text.clear()

            addMessage(
                messages,
                "Todavía estoy en configuración. Pronto podré ayudarte a analizar tus finanzas y planificar tus ahorros.",
                false
            )
        }
    }

    private fun addMessage(
        container: LinearLayout,
        message: String,
        isUser: Boolean
    ) {
        val text = TextView(requireContext()).apply {
            this.text = message
            textSize = 14f
            setTextColor(
                resources.getColor(
                    R.color.gamora_text_primary,
                    null
                )
            )
            setPadding(16, 12, 16, 12)
            background = resources.getDrawable(
                R.drawable.bg_dashboard_card,
                null
            )
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12
            if (isUser) {
                gravity = android.view.Gravity.END
            } else {
                gravity = android.view.Gravity.START
            }
        }

        container.addView(text, params)
    }
}

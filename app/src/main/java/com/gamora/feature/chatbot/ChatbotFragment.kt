
package com.gamora.feature.chatbot

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.gamora.R

class ChatbotFragment : Fragment(R.layout.fragment_chatbot) {

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<EditText>(R.id.txtChatMessage)
        val sendButton = view.findViewById<Button>(R.id.btnSendMessage)
        val messages = view.findViewById<LinearLayout>(
            R.id.layoutChatMessages
        )
        val scrollChat = view.findViewById<ScrollView>(R.id.scrollChat)
        val backButton = view.findViewById<TextView>(R.id.btnBackChat)

        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

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

            scrollChat.post {
                scrollChat.fullScroll(View.FOCUS_DOWN)
            }
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

            setPadding(dp(16), dp(12), dp(16), dp(12))

            background = resources.getDrawable(
                if (isUser) {
                    R.drawable.bg_chat_user_message
                } else {
                    R.drawable.bg_chat_message
                },
                null
            )
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
            bottomMargin = dp(4)
            gravity = if (isUser) Gravity.END else Gravity.START
            marginStart = dp(4)
            marginEnd = dp(4)
        }

        container.addView(text, params)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

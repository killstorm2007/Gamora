
package com.gamora.feature.goals

import com.gamora.core.ui.formatoFecha
import com.gamora.core.textoPlural
import com.gamora.core.texto
import com.google.android.material.checkbox.MaterialCheckBox
import com.gamora.core.ui.centavosDe
import com.gamora.core.ui.LOCALE_MX
import com.gamora.core.ui.dp
import com.gamora.core.ui.dinero
import com.google.android.material.textfield.TextInputEditText
import com.gamora.core.ui.enDialogo
import com.gamora.core.ui.enCampo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gamora.R
import com.gamora.feature.transactions.observar
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GoalsFragment : Fragment(R.layout.fragment_goals) {

    private val goals
        get() = GamoraGoalStore.goals

    private val mostrarError: (String) -> Unit = { mensaje ->
        context?.let { Toast.makeText(it, mensaje, Toast.LENGTH_LONG).show() }
    }

    private val primaryColor: Int
        get() = ContextCompat.getColor(
            requireContext(),
            R.color.gamora_text_primary
        )

    private val secondaryColor: Int
        get() = ContextCompat.getColor(
            requireContext(),
            R.color.gamora_text_secondary
        )

    private val accentColor: Int
        get() = ContextCompat.getColor(
            requireContext(),
            R.color.gamora_accent
        )

    private val borderColor: Int
        get() = ContextCompat.getColor(
            requireContext(),
            R.color.gamora_border
        )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnCreateGoal)
            .setOnClickListener {
                showCreateGoalDialog()
            }

        observar(GamoraGoalStore.goalsFlow, ::renderGoals)
    }

    private fun showCreateGoalDialog() {
        val context = requireContext()

        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }

        val nameInput = TextInputEditText(context).apply {
            hint = texto(R.string.goals_nombre_de_la_meta)
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(100))
        }

        val targetInput = TextInputEditText(context).apply {
            hint = texto(R.string.goals_cuanto_quieres_ahorrar_mxn)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
        }

        val deadlineInput = TextInputEditText(context).apply {
            hint = texto(R.string.goals_fecha_limite_opcional)
            isFocusable = false
            isClickable = true
        }

        var selectedDeadline: Long? = null

        deadlineInput.setOnClickListener {
            val calendar = Calendar.getInstance()

            selectedDeadline?.let {
                calendar.timeInMillis = it
            }

            DatePickerDialog(
                context,
                { _, year, month, day ->
                    calendar.set(year, month, day, 12, 0, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    selectedDeadline = calendar.timeInMillis
                    deadlineInput.setText(
                        formatDate(calendar.timeInMillis)
                    )
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        form.addView(nameInput.enCampo())
        form.addView(targetInput.enCampo())
        form.addView(deadlineInput.enCampo())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(texto(R.string.goals_crear_meta_de_ahorro))
            .setView(form)
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.goals_crear), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val name = nameInput.text.toString().trim()
                    val target = parseMoney(
                        targetInput.text.toString()
                    )

                    when {
                        name.isBlank() -> {
                            nameInput.error = texto(R.string.goals_escribe_un_nombre)
                        }

                        // Mismo límite que las reglas de Firestore.
                        target == null || target <= 0L || target > 100_000_000_000L -> {
                            targetInput.error =
                                texto(R.string.goals_introduce_una_cantidad_valida)
                        }

                        else -> {
                            GamoraGoalStore.crear(
                                nombre = name,
                                objetivoCentavos = target,
                                fechaLimite = selectedDeadline,
                                onError = mostrarError
                            )

                            dialog.dismiss()

                            Toast.makeText(
                                context,
                                texto(R.string.goals_meta_creada_correctamente),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
        }

        dialog.show()
    }

    private fun showContributionDialog(goal: SavingsGoal) {
        val input = TextInputEditText(requireContext()).apply {
            hint = texto(R.string.goals_cantidad_a_aportar_mxn)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val descontar = MaterialCheckBox(requireContext()).apply {
            text = texto(R.string.goals_descontar_de_mi_saldo)
            isChecked = true
        }

        val contenido = input.enDialogo().apply {
            orientation = LinearLayout.VERTICAL
            addView(descontar)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(texto(R.string.goals_aportar_a, goal.name))
            .setMessage(
                texto(R.string.goals_objetivo_nahorrado, dinero(goal.targetCents), dinero(goal.savedCents))
            )
            .setView(contenido)
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.goals_registrar), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val amount = parseMoney(
                        input.text.toString()
                    )

                    if (amount == null || amount <= 0L) {
                        input.error = texto(R.string.goals_introduce_una_cantidad_valida)
                        return@setOnClickListener
                    }

                    // Mismo límite que las reglas de Firestore para movimientos.
                    if (amount > 100_000_000_000L || Long.MAX_VALUE - goal.savedCents < amount) {
                        input.error = texto(R.string.goals_la_cantidad_es_demasiado)
                        return@setOnClickListener
                    }

                    GamoraGoalStore.aportar(goal, amount, descontar.isChecked, mostrarError)
                    dialog.dismiss()

                    Toast.makeText(
                        requireContext(),
                        texto(R.string.goals_aportacion_registrada),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        dialog.show()
    }

    private fun confirmDeleteGoal(goal: SavingsGoal) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(texto(R.string.goals_eliminar_meta))
            .setMessage(
                texto(R.string.goals_quieres_eliminar_se_eliminara, goal.name)
            )
            .setNegativeButton(texto(R.string.import_review_cancelar), null)
            .setPositiveButton(texto(R.string.goals_eliminar)) { _, _ ->
                GamoraGoalStore.eliminar(goal.id, mostrarError)
            }
            .show()
    }

    private fun renderGoals(view: View) {
        val totalSaved = goals.sumOf { it.savedCents }
        val activeGoals = goals.count { it.activa }

        view.findViewById<TextView>(R.id.txtTotalSaved).text =
            dinero(totalSaved)

        view.findViewById<TextView>(R.id.txtGoalsCount).text =
            textoPlural(R.plurals.goals_metas_activas, activeGoals, activeGoals)

        val container =
            view.findViewById<LinearLayout>(R.id.layoutGoals)

        val emptyMessage =
            view.findViewById<TextView>(R.id.txtEmptyGoals)

        // Conservamos el mensaje vacío que ya existe en el XML.
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)

            if (child.id != R.id.txtEmptyGoals) {
                container.removeViewAt(i)
            }
        }

        emptyMessage.visibility =
            if (goals.isEmpty()) View.VISIBLE else View.GONE

        goals.forEach { goal ->
            container.addView(createGoalCard(goal))
        }
    }

    private fun createGoalCard(goal: SavingsGoal): View {
        val context = requireContext()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = ContextCompat.getDrawable(
                context,
                R.drawable.bg_dashboard_card
            )
        }

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }

        val title = TextView(context).apply {
            text = texto(R.string.comun_icono_y_texto, if (goal.activa) "🎯" else "🏆", goal.name)
            setTextAppearance(R.style.TextAppearance_Gamora_Title)
        }

        card.addView(title)

        val target = TextView(context).apply {
            text = texto(R.string.goals_objetivo, dinero(goal.targetCents))
            textSize = 13f
            setTextColor(secondaryColor)
            setPadding(0, dp(6), 0, dp(12))
        }

        card.addView(target)

        val progressPercent = (
                goal.savedCents.toDouble() /
                        goal.targetCents.toDouble() * 100
                ).toInt().coerceIn(0, 100)

        val progress = LinearProgressIndicator(context).apply {
            max = 100
            this.progress = progressPercent
            trackThickness = dp(10)
            trackCornerRadius = dp(5)
            trackStopIndicatorSize = 0
            indicatorTrackGapSize = 0
            setIndicatorColor(accentColor)
            trackColor = ContextCompat.getColor(context, R.color.gamora_surface_variant)
        }

        card.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val savedText = TextView(context).apply {
            text = texto(R.string.goals_ahorrados, dinero(goal.savedCents), progressPercent)
            textSize = 14f
            setTextColor(primaryColor)
            setPadding(0, dp(10), 0, dp(4))
        }

        card.addView(savedText)

        val remaining = (goal.targetCents - goal.savedCents)
            .coerceAtLeast(0L)

        val remainingText = TextView(context).apply {
            text = if (remaining == 0L) {
                texto(R.string.goals_meta_completada)
            } else {
                texto(R.string.goals_te_faltan, dinero(remaining))
            }

            textSize = 13f
            setTextColor(
                if (remaining == 0L) accentColor else secondaryColor
            )
        }

        card.addView(remainingText)

        goal.deadlineMillis?.let { deadline ->
            val dateText = TextView(context).apply {
                text = texto(R.string.goals_fecha_limite, formatDate(deadline))
                textSize = 13f
                setTextColor(secondaryColor)
                setPadding(0, dp(6), 0, 0)
            }

            card.addView(dateText)
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // A la izquierda: a la derecha queda el botón flotante de Gamora AI.
            gravity = Gravity.START
            setPadding(0, dp(12), 0, 0)
        }

        fun boton(texto: String, fondo: Int, colorTexto: Int) = MaterialButton(context).apply {
            text = texto
            isAllCaps = false
            minHeight = dp(44)
            minimumHeight = dp(44)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(14)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, fondo))
            setTextColor(ContextCompat.getColor(context, colorTexto))
            elevation = 0f
            stateListAnimator = null
        }

        val deleteButton = boton(texto(R.string.goals_eliminar), android.R.color.transparent, R.color.gamora_expense).apply {
            setOnClickListener {
                confirmDeleteGoal(goal)
            }
        }

        if (goal.activa) {
            val contributeButton = boton(texto(R.string.goals_aportar), R.color.gamora_accent_light, R.color.gamora_accent).apply {
                setOnClickListener {
                    showContributionDialog(goal)
                }
            }

            actions.addView(
                contributeButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            )
        }

        actions.addView(deleteButton)
        card.addView(actions)

        card.layoutParams = cardParams
        return card
    }

    private fun parseMoney(value: String): Long? = centavosDe(value)?.takeIf { it > 0L }

    private fun formatDate(timestamp: Long): String {
        return formatoFecha("ddMMyyyy").format(Date(timestamp))
    }

}

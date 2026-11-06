
package com.gamora.feature.goals

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gamora.R
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class SavingsGoal(
    val id: String,
    val name: String,
    val targetCents: Long,
    var savedCents: Long = 0L,
    val deadlineMillis: Long? = null
)

class GoalsFragment : Fragment(R.layout.fragment_goals) {

    private val currencyFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    private val goals = mutableListOf<SavingsGoal>()

    private val preferencesName = "gamora_savings_goals"
    private val preferencesKey = "goals_json"

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

        loadGoals()

        view.findViewById<Button>(R.id.btnCreateGoal)
            .setOnClickListener {
                showCreateGoalDialog()
            }

        renderGoals(view)
    }

    private fun showCreateGoalDialog() {
        val context = requireContext()

        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }

        val nameInput = EditText(context).apply {
            hint = "Ej. Comprar una computadora"
            isSingleLine = true
        }

        val targetInput = EditText(context).apply {
            hint = "Cantidad objetivo en MXN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
        }

        val deadlineInput = EditText(context).apply {
            hint = "Fecha límite (opcional)"
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

        form.addView(label("Nombre de la meta"))
        form.addView(nameInput, fieldParams())

        form.addView(label("¿Cuánto quieres ahorrar?"))
        form.addView(targetInput, fieldParams())

        form.addView(label("Fecha límite"))
        form.addView(deadlineInput, fieldParams())

        val dialog = AlertDialog.Builder(context)
            .setTitle("Crear meta de ahorro")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Crear", null)
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
                            nameInput.error = "Escribe un nombre"
                        }

                        target == null || target <= 0L -> {
                            targetInput.error =
                                "Introduce una cantidad válida"
                        }

                        else -> {
                            goals.add(
                                SavingsGoal(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = name,
                                    targetCents = target,
                                    deadlineMillis = selectedDeadline
                                )
                            )

                            saveGoals()
                            viewOrRender()
                            dialog.dismiss()

                            Toast.makeText(
                                context,
                                "Meta creada correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
        }

        dialog.show()
    }

    private fun showContributionDialog(goal: SavingsGoal) {
        val input = EditText(requireContext()).apply {
            hint = "Cantidad que vas a aportar en MXN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Aportar a ${goal.name}")
            .setMessage(
                "Objetivo: ${formatMoney(goal.targetCents)}\n" +
                        "Ahorrado: ${formatMoney(goal.savedCents)}"
            )
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registrar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val amount = parseMoney(
                        input.text.toString()
                    )

                    if (amount == null || amount <= 0L) {
                        input.error = "Introduce una cantidad válida"
                        return@setOnClickListener
                    }

                    if (Long.MAX_VALUE - goal.savedCents < amount) {
                        input.error = "La cantidad es demasiado grande"
                        return@setOnClickListener
                    }

                    goal.savedCents += amount

                    saveGoals()
                    viewOrRender()
                    dialog.dismiss()

                    Toast.makeText(
                        requireContext(),
                        "Aportación registrada",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        dialog.show()
    }

    private fun confirmDeleteGoal(goal: SavingsGoal) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar meta")
            .setMessage(
                "¿Quieres eliminar \"${goal.name}\"? " +
                        "Se eliminará también su registro de aportaciones."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                goals.removeAll { it.id == goal.id }
                saveGoals()
                viewOrRender()
            }
            .show()
    }

    private fun renderGoals(view: View) {
        val totalSaved = goals.sumOf { it.savedCents }
        val activeGoals = goals.count {
            it.savedCents < it.targetCents
        }

        view.findViewById<TextView>(R.id.txtTotalSaved).text =
            formatMoney(totalSaved)

        view.findViewById<TextView>(R.id.txtGoalsCount).text =
            "$activeGoals metas activas"

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
            text = goal.name
            textSize = 18f
            setTextColor(primaryColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        card.addView(title)

        val target = TextView(context).apply {
            text = "Objetivo: ${formatMoney(goal.targetCents)}"
            textSize = 13f
            setTextColor(secondaryColor)
            setPadding(0, dp(6), 0, dp(12))
        }

        card.addView(target)

        val progressPercent = (
                goal.savedCents.toDouble() /
                        goal.targetCents.toDouble() * 100
                ).toInt().coerceIn(0, 100)

        val progress = ProgressBar(
            context,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            this.progress = progressPercent
            progressTintList =
                android.content.res.ColorStateList.valueOf(accentColor)
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(borderColor)
        }

        card.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        val savedText = TextView(context).apply {
            text = "${formatMoney(goal.savedCents)} ahorrados · " +
                    "$progressPercent%"
            textSize = 14f
            setTextColor(primaryColor)
            setPadding(0, dp(10), 0, dp(4))
        }

        card.addView(savedText)

        val remaining = (goal.targetCents - goal.savedCents)
            .coerceAtLeast(0L)

        val remainingText = TextView(context).apply {
            text = if (remaining == 0L) {
                "¡Meta completada!"
            } else {
                "Te faltan ${formatMoney(remaining)}"
            }

            textSize = 13f
            setTextColor(
                if (remaining == 0L) accentColor else secondaryColor
            )
        }

        card.addView(remainingText)

        goal.deadlineMillis?.let { deadline ->
            val dateText = TextView(context).apply {
                text = "Fecha límite: ${formatDate(deadline)}"
                textSize = 13f
                setTextColor(secondaryColor)
                setPadding(0, dp(6), 0, 0)
            }

            card.addView(dateText)
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }

        val contributeButton = Button(context).apply {
            text = "Aportar"
            isAllCaps = false
            setOnClickListener {
                showContributionDialog(goal)
            }
        }

        actions.addView(contributeButton)

        val deleteButton = Button(context).apply {
            text = "Eliminar"
            isAllCaps = false
            setOnClickListener {
                confirmDeleteGoal(goal)
            }
        }

        actions.addView(deleteButton)
        card.addView(actions)

        card.layoutParams = cardParams
        return card
    }

    private fun viewOrRender() {
        view?.let { renderGoals(it) }
    }

    private fun saveGoals() {
        val array = JSONArray()

        goals.forEach { goal ->
            array.put(
                JSONObject().apply {
                    put("id", goal.id)
                    put("name", goal.name)
                    put("targetCents", goal.targetCents)
                    put("savedCents", goal.savedCents)

                    if (goal.deadlineMillis == null) {
                        put("deadlineMillis", JSONObject.NULL)
                    } else {
                        put("deadlineMillis", goal.deadlineMillis)
                    }
                }
            )
        }

        requireContext()
            .getSharedPreferences(
                preferencesName,
                android.content.Context.MODE_PRIVATE
            )
            .edit()
            .putString(preferencesKey, array.toString())
            .apply()
    }

    private fun loadGoals() {
        goals.clear()

        val json = requireContext()
            .getSharedPreferences(
                preferencesName,
                android.content.Context.MODE_PRIVATE
            )
            .getString(preferencesKey, null)
            ?: return

        try {
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                val target = item.getLong("targetCents")
                val saved = item.optLong("savedCents", 0L)

                if (target <= 0L || saved < 0L) continue

                val deadline = if (
                    item.isNull("deadlineMillis")
                ) {
                    null
                } else {
                    item.optLong("deadlineMillis")
                }

                goals.add(
                    SavingsGoal(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        targetCents = target,
                        savedCents = saved,
                        deadlineMillis = deadline
                    )
                )
            }
        } catch (_: Exception) {
            Toast.makeText(
                requireContext(),
                "No se pudieron leer las metas guardadas.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun parseMoney(value: String): Long? {
        return try {
            val normalized = value.trim().replace(",", ".")

            if (normalized.isBlank()) return null

            BigDecimal(normalized)
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
                .takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatMoney(cents: Long): String {
        return currencyFormat.format(cents / 100.0)
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat(
            "dd/MM/yyyy",
            Locale("es", "MX")
        ).format(Date(timestamp))
    }

    private fun label(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(primaryColor)
            setPadding(0, dp(12), 0, dp(4))
        }
    }

    private fun fieldParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

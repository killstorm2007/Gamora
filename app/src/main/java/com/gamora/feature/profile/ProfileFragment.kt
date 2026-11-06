
package com.gamora.feature.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gamora.R
import com.gamora.core.preferences.AppearancePreferences
import com.gamora.core.preferences.GamoraTheme
import com.gamora.feature.transactions.GamoraTransactionStore
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val currencyFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    private var actualizandoSelector = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        actualizarResumen(view)
        configurarSelectorTema(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { actualizarResumen(it) }
    }

    private fun actualizarResumen(view: View) {
        val movimientos = GamoraTransactionStore.movements

        val ingresos = movimientos
            .filter { it.isIncome }
            .sumOf { it.amountCents }

        val gastos = movimientos
            .filter { !it.isIncome }
            .sumOf { it.amountCents }

        val balance = ingresos - gastos

        view.findViewById<TextView>(R.id.tvPatrimonio).text =
            formatearDinero(balance)

        view.findViewById<TextView>(
            R.id.tvAhorroAcumulado
        ).text = "Por configurar"

        view.findViewById<TextView>(
            R.id.tvMetasActivas
        ).text = "0"
    }

    private fun configurarSelectorTema(view: View) {

        val grupoTema =
            view.findViewById<MaterialButtonToggleGroup>(
                R.id.groupTema
            )

        grupoTema.addOnButtonCheckedListener {
                _, checkedId, isChecked ->

            if (!isChecked || actualizandoSelector) {
                return@addOnButtonCheckedListener
            }

            val tema = when (checkedId) {
                R.id.btnTemaClaro -> GamoraTheme.LIGHT
                R.id.btnTemaOscuro -> GamoraTheme.DARK
                R.id.btnTemaSistema -> GamoraTheme.SYSTEM
                else -> return@addOnButtonCheckedListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                AppearancePreferences.setTheme(
                    requireContext(),
                    tema
                )

                val modo = when (tema) {
                    GamoraTheme.LIGHT ->
                        AppCompatDelegate.MODE_NIGHT_NO

                    GamoraTheme.DARK ->
                        AppCompatDelegate.MODE_NIGHT_YES

                    GamoraTheme.SYSTEM ->
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                AppCompatDelegate.setDefaultNightMode(modo)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                AppearancePreferences
                    .themeFlow(requireContext())
                    .collect { tema ->

                        actualizandoSelector = true

                        when (tema) {
                            GamoraTheme.LIGHT ->
                                grupoTema.check(R.id.btnTemaClaro)

                            GamoraTheme.DARK ->
                                grupoTema.check(R.id.btnTemaOscuro)

                            GamoraTheme.SYSTEM ->
                                grupoTema.clearChecked()
                        }

                        actualizandoSelector = false
                    }
            }
        }
    }

    private fun formatearDinero(centavos: Long): String {
        return currencyFormat.format(centavos / 100.0)
    }
}

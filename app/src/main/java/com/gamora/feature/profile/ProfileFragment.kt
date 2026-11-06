package com.gamora.feature.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gamora.MainActivity
import com.gamora.R
import com.gamora.core.preferences.AppearancePreferences
import com.gamora.core.preferences.GamoraTheme
import com.gamora.data.repository.AuthRepository
import com.gamora.data.repository.GoalsRepository
import com.gamora.data.repository.MovementRepository
import com.gamora.data.repository.ProfileRepository
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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

        observarDatos(view)
        configurarSelectorTema(view)
        configurarCierreDeSesion(view)
    }

    /**
     * Observa movimientos y metas, y recarga todo al volver a la pantalla.
     */
    private fun observarDatos(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MovementRepository.movements.collect {
                        actualizarResumen(view)
                    }
                }

                launch {
                    GoalsRepository.goals.collect {
                        actualizarResumen(view)
                    }
                }

                cargarPerfil(view)

                MovementRepository.refresh()
                GoalsRepository.refresh()

                actualizarResumen(view)
            }
        }
    }

    private suspend fun cargarPerfil(view: View) {
        val correo = AuthRepository.currentEmail()
        val nombreUsuario = AuthRepository.currentUser()
            ?.userMetadata
            ?.get("full_name")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }

        view.findViewById<TextView>(R.id.tvCorreoPerfil).text =
            correo ?: "Sesión sin correo"

        ProfileRepository.load().onSuccess { profile ->
            val nombre = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: nombreUsuario
                ?: "Mi cuenta"

            view.findViewById<TextView>(R.id.tvNombrePerfil).text = nombre
        }.onFailure {
            view.findViewById<TextView>(R.id.tvNombrePerfil).text =
                nombreUsuario ?: "Mi cuenta"
        }
    }

    private fun actualizarResumen(view: View) {
        val movimientos = MovementRepository.movements.value

        val ingresos = movimientos
            .filter { it.isIncome }
            .sumOf { it.amountCents }

        val gastos = movimientos
            .filter { !it.isIncome }
            .sumOf { it.amountCents }

        val balance = ingresos - gastos

        view.findViewById<TextView>(R.id.tvPatrimonio).text =
            formatearDinero(balance)

        val metas = GoalsRepository.goals.value
        val ahorroAcumulado = metas.sumOf { it.savedCents }
        val metasActivas = metas.count { it.savedCents < it.targetCents }

        view.findViewById<TextView>(R.id.tvAhorroAcumulado).text =
            formatearDinero(ahorroAcumulado)

        view.findViewById<TextView>(R.id.tvMetasActivas).text =
            metasActivas.toString()
    }

    private fun configurarCierreDeSesion(view: View) {
        view.findViewById<View>(R.id.btnCerrarSesion)
            .setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    AuthRepository.signOut().onFailure { error ->
                        Toast.makeText(
                            requireContext(),
                            error.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    // Evita que el siguiente usuario vea datos del anterior.
                    MovementRepository.clear()
                    GoalsRepository.clear()

                    startActivity(
                        Intent(requireContext(), MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )

                    activity?.finish()
                }
            }
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

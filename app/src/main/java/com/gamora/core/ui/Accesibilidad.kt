package com.gamora.core.ui

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

/*
 * Ajustes para lectores de pantalla (TalkBack) que no cambian nada visual:
 * - Los emojis y símbolos decorativos al inicio de un texto ("👤     Información personal",
 *   "+  Ingreso", "📊\n\nTodavía no hay…") no se leen en voz alta.
 * - Los títulos grandes se marcan como encabezados para poder saltar entre secciones.
 */

// Uno o más caracteres que no son letras ni números, seguidos de espacio o salto de línea.
private val DECORACION_INICIAL = Regex("""^[^\p{L}\p{N}]+[\s]+""")

/** El texto como lo debe leer TalkBack: sin la decoración inicial. Null si no hay nada que quitar. */
fun textoParaLector(texto: CharSequence?): String? {
    val original = texto?.toString() ?: return null
    val limpio = original.replaceFirst(DECORACION_INICIAL, "").trim()
    return limpio.takeIf { it.isNotEmpty() && it != original.trim() }
}

private object LectorSinDecoracion : AccessibilityDelegateCompat() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        // Se calcula con el texto de este momento: si el texto cambia, lo leído también.
        if (host.contentDescription == null) textoParaLector((host as? TextView)?.text)?.let { info.contentDescription = it }
    }
}

private const val TAMANO_TITULO_SP = 24f

/** Aplica los ajustes a [vista] y a todo lo que contiene. */
fun ajustarParaLectores(vista: View) {
    if (vista is TextView) {
        if (ViewCompat.getAccessibilityDelegate(vista) == null) ViewCompat.setAccessibilityDelegate(vista, LectorSinDecoracion)
        val sp = vista.textSize / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, vista.resources.displayMetrics)
        val negrita = vista.typeface?.isBold == true || vista.typeface == Typeface.DEFAULT_BOLD
        if (sp >= TAMANO_TITULO_SP && negrita) ViewCompat.setAccessibilityHeading(vista, true)
    }
    if (vista is ViewGroup) for (i in 0 until vista.childCount) ajustarParaLectores(vista.getChildAt(i))
}

/** Aplica los ajustes a cada pantalla (Activity y Fragment) de la app; se registra en Application. */
fun Application.ajustarPantallasParaLectores() {
    val alCrearVista = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) =
            ajustarParaLectores(v)
    }
    registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            (activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(alCrearVista, true)
        }

        override fun onActivityStarted(activity: Activity) =
            ajustarParaLectores(activity.window.decorView)

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    })
}

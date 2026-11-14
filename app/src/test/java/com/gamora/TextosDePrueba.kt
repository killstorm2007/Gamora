package com.gamora

import com.gamora.core.Textos
import java.io.File
import java.util.Locale

/** Instala en [Textos] un lector de app/src/main/res/values/strings.xml (las pruebas no tienen Context). */
object TextosDePrueba {

    private val archivo = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
        .map(::File).first { it.exists() }.readText()

    private fun limpiar(valor: String): String {
        val bs = '\\'
        val sinComillas = valor.trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
        return sinComillas
            .replace("$bs'", "'").replace("$bs\"", "\"").replace("$bs@", "@").replace("$bs?", "?")
            .replace("${bs}n", "\n")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }

    private val textos: Map<String, String> = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(archivo).associate { it.groupValues[1] to limpiar(it.groupValues[2]) }

    private val plurales: Map<String, Map<String, String>> =
        Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(archivo).associate { p ->
            p.groupValues[1] to Regex("""<item quantity="(\w+)">(.*?)</item>""").findAll(p.groupValues[2])
                .associate { it.groupValues[1] to limpiar(it.groupValues[2]) }
        }

    private fun nombre(clase: String, id: Int): String =
        Class.forName("com.gamora.R$$clase").fields.first { it.getInt(null) == id }.name

    private val locale = Locale.forLanguageTag("es-MX")

    fun instalar() {
        Textos.texto = { id, args ->
            val valor = textos.getValue(nombre("string", id))
            if (args.isEmpty()) valor else String.format(locale, valor, *args)
        }
        Textos.plural = { id, cantidad, args ->
            val formas = plurales.getValue(nombre("plurals", id))
            val valor = (if (cantidad == 1) formas["one"] else formas["other"]) ?: formas.getValue("other")
            String.format(locale, valor, *args)
        }
    }
}

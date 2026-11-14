package com.gamora.feature.importar

import com.gamora.R
import com.gamora.core.texto
import com.gamora.core.Categoria
import com.gamora.core.ui.centavosDe
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class NoEsNominaException(mensaje: String) : Exception(mensaje)

private val NO_ES_NOMINA get() = texto(R.string.nomina_cfdi_este_xml_no_es)

/**
 * Lee un CFDI de nómina (XML del SAT) en el teléfono, sin IA.
 * Registra un solo ingreso por el neto pagado (Total del comprobante) en la fecha de pago.
 */
fun leerNominaCfdi(xml: ByteArray): ResultadoLectura {
    val documento = try {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Un CFDI nunca trae DOCTYPE; bloquearlo evita entidades externas.
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
            }
        }.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    } catch (_: Exception) {
        throw NoEsNominaException(NO_ES_NOMINA)
    }

    fun elemento(nombre: String): Element? =
        documento.getElementsByTagNameNS("*", nombre).item(0) as? Element

    fun Element.atributo(nombre: String): String = getAttribute(nombre).trim()

    val comprobante = documento.documentElement
        ?.takeIf { it.localName == "Comprobante" }
        ?: throw NoEsNominaException(NO_ES_NOMINA)
    val nomina = elemento("Nomina") ?: throw NoEsNominaException(NO_ES_NOMINA)

    val monto = centavosDe(comprobante.atributo("Total"))?.takeIf { it > 0 }
        ?: throw NoEsNominaException(texto(R.string.nomina_cfdi_el_recibo_no_indica))

    val fecha = fechaDe(nomina.atributo("FechaPago"))
        ?: fechaDe(comprobante.atributo("Fecha").take(10))
        ?: throw NoEsNominaException(texto(R.string.nomina_cfdi_el_recibo_no_indica_2))

    val empresa = elemento("Emisor")?.atributo("Nombre").orEmpty()

    val inicio = nomina.atributo("FechaInicialPago")
    val fin = nomina.atributo("FechaFinalPago")

    return ResultadoLectura(
        banco = empresa,
        periodo = if (inicio.isNotEmpty() && fin.isNotEmpty()) texto(R.string.nomina_periodo, inicio, fin) else "",
        movimientos = listOf(
            MovimientoImportado(
                fecha = fecha,
                descripcion = if (empresa.isEmpty()) texto(R.string.nomina_cfdi_nomina_2) else texto(R.string.nomina_cfdi_nomina_3, empresa).take(200),
                montoCentavos = monto,
                esIngreso = true,
                categoria = Categoria.SALARIO
            )
        ),
        descartados = 0
    )
}

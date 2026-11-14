package com.gamora.feature.importar.sinia

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.gamora.core.esperar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/*
 * Saca las palabras de un documento con su posición, todo en el teléfono y en memoria:
 * del texto del PDF cuando lo trae, o con reconocimiento de texto (ML Kit, sin internet)
 * cuando es una imagen escaneada o una foto.
 */

private const val PUNTOS_ANCHO_CARTA = 612f   // ancho de una hoja carta en puntos de PDF
private const val ESCALA_OCR = 2.5f           // ~180 ppp: suficiente para leer montos pequeños
private const val MAXIMO_PAGINAS_OCR = 15

/** Texto del PDF con su número de páginas (para saber si es un escaneo). */
class TextoDePdf(val palabras: List<Palabra>, val paginas: Int) {
    /** Pocas palabras por página: el PDF es una imagen escaneada (p. ej. Santander). */
    val esEscaneo: Boolean get() = palabras.size < 40 * paginas.coerceAtLeast(1)
}

/** Palabras del texto del PDF (ya sin contraseña). Vacío si el PDF es solo imágenes. */
fun textoDePdf(pdf: ByteArray): TextoDePdf =
    PDDocument.load(pdf).use { documento ->
        val palabras = mutableListOf<Palabra>()

        object : PDFTextStripper() {
            init {
                sortByPosition = true
            }

            override fun writeString(texto: String, posiciones: MutableList<TextPosition>) {
                var inicio: TextPosition? = null
                var fin: TextPosition? = null
                val actual = StringBuilder()

                fun emitir() {
                    val a = inicio ?: return
                    val b = fin ?: return
                    if (actual.isEmpty()) return
                    palabras += Palabra(
                        texto = actual.toString(),
                        x0 = a.xDirAdj,
                        x1 = b.xDirAdj + b.widthDirAdj,
                        y = a.yDirAdj,
                        pagina = currentPageNo,
                        alto = a.heightDir.coerceAtLeast(1f)
                    )
                }

                // Una palabra por cada tramo sin espacios: así cada monto conserva su columna.
                for (posicion in posiciones) {
                    val caracter = posicion.unicode
                    if (caracter.isBlank()) {
                        emitir()
                        actual.clear(); inicio = null; fin = null
                        continue
                    }
                    if (inicio == null) inicio = posicion
                    fin = posicion
                    actual.append(caracter)
                }
                emitir()
            }
        }.getText(documento)

        TextoDePdf(palabras, documento.numberOfPages)
    }


/** Reconocimiento de texto de cada página del PDF (las primeras [MAXIMO_PAGINAS_OCR]). */
suspend fun reconocerPdf(pdf: ByteArray, alAvanzar: (pagina: Int, total: Int) -> Unit = { _, _ -> }): List<Palabra> {
    // El dibujo de Android (Android 11+) entiende las páginas escaneadas como máscaras en blanco y
    // negro (p. ej. Santander), que PDFBox deja en blanco; y es más rápido.
    abrirEnAndroid(pdf)?.use { renderizador ->
        return leerPaginas(minOf(renderizador.pageCount, MAXIMO_PAGINAS_OCR), alAvanzar) { indice ->
            renderizador.openPage(indice).use { pagina ->
                Bitmap.createBitmap(
                    (pagina.width * ESCALA_OCR).toInt(),
                    (pagina.height * ESCALA_OCR).toInt(),
                    Bitmap.Config.ARGB_8888
                ).apply {
                    eraseColor(Color.WHITE)   // la página se dibuja sobre transparente
                    pagina.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    return PDDocument.load(pdf).use { documento ->
        val renderizador = PDFRenderer(documento)
        leerPaginas(minOf(documento.numberOfPages, MAXIMO_PAGINAS_OCR), alAvanzar) { indice ->
            renderizador.renderImage(indice, ESCALA_OCR)
        }
    }
}

/**
 * Abre el PDF con el lector de Android sin escribirlo en el almacenamiento: los bytes van a un
 * archivo en memoria (memfd) que desaparece al cerrarse. Null si no se puede (Android 10 o menos).
 */
private fun abrirEnAndroid(pdf: ByteArray): PdfRenderer? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return try {
        val fd = Os.memfd_create("documento", 0)
        try {
            var escritos = 0
            while (escritos < pdf.size) escritos += Os.write(fd, pdf, escritos, pdf.size - escritos)
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            PdfRenderer(ParcelFileDescriptor.dup(fd))
        } finally {
            Os.close(fd)
        }
    } catch (error: Exception) {
        null
    }
}

/** Dibuja y reconoce página por página con un solo lector: el modelo se carga una vez. */
private suspend fun leerPaginas(
    total: Int,
    alAvanzar: (pagina: Int, total: Int) -> Unit,
    dibujar: (indice: Int) -> Bitmap
): List<Palabra> = nuevoLector().use { lector ->
    val palabras = mutableListOf<Palabra>()
    for (indice in 0 until total) {
        coroutineContext.ensureActive()
        alAvanzar(indice + 1, total)
        val imagen = dibujar(indice)
        try {
            palabras += lector.reconocer(imagen, indice + 1, ESCALA_OCR)
        } finally {
            imagen.recycle()
        }
    }
    palabras
}

/** Reconocimiento de texto de una foto; se escala como si fuera una hoja carta. */
suspend fun reconocerImagen(bytes: ByteArray): List<Palabra> {
    // Fotos enormes se reducen al leerlas: el texto sigue siendo legible y se ahorra memoria.
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    var muestreo = 1
    while (limites.outWidth / muestreo > 3000 || limites.outHeight / muestreo > 4000) muestreo *= 2

    val imagen = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestreo })
        ?: return emptyList()
    return try {
        nuevoLector().use { it.reconocer(imagen, 1, imagen.width / PUNTOS_ANCHO_CARTA) }
    } finally {
        imagen.recycle()
    }
}

private fun nuevoLector(): TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

private suspend fun TextRecognizer.reconocer(imagen: Bitmap, pagina: Int, escala: Float): List<Palabra> {
    val texto = process(InputImage.fromBitmap(imagen, 0)).esperar()
    return corregirOcr(texto.textBlocks.flatMap { bloque ->
        bloque.lines.flatMap { linea ->
            linea.elements.mapNotNull { elemento ->
                val caja = elemento.boundingBox ?: return@mapNotNull null
                Palabra(
                    texto = elemento.text,
                    x0 = caja.left / escala,
                    x1 = caja.right / escala,
                    // Base de la palabra, como en el texto del PDF.
                    y = caja.bottom / escala,
                    pagina = pagina,
                    alto = (caja.height() / escala).coerceAtLeast(1f)
                )
            }
        }
    })
}

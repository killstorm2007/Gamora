package com.gamora.feature.importar

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.ByteArrayOutputStream

class ContrasenaRequeridaException : Exception()
class ContrasenaIncorrectaException : Exception()

/**
 * Deja el PDF listo para Gemini, todo en memoria: sin cifrado lo devuelve igual; con contraseña
 * lo abre y devuelve una copia sin seguridad. Nada se escribe en disco y [contrasena] se borra al terminar.
 */
fun prepararPdf(bytes: ByteArray, contrasena: CharArray?): ByteArray {
    try {
        // PdfBox solo acepta String; vive lo que dura esta llamada.
        val documento = try {
            if (contrasena == null) PDDocument.load(bytes) else PDDocument.load(bytes, String(contrasena))
        } catch (_: InvalidPasswordException) {
            throw if (contrasena == null) ContrasenaRequeridaException() else ContrasenaIncorrectaException()
        }

        return documento.use { pdf ->
            if (!pdf.isEncrypted) return bytes

            pdf.isAllSecurityToBeRemoved = true
            ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
        }
    } finally {
        contrasena?.fill('\u0000')
    }
}

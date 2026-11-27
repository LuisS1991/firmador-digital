package com.lnantes.uifirmaelectronica.backend

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptPdfGeneratorTest {

    @Test
    fun `el pdf se genera con los datos de la api y no con la plantilla de ejemplo`() {
        val json = """
            {
              "data": {
                "policia": {
                  "nombreCompleto": "POLICIA PRUEBA",
                  "cedula": "11111111",
                  "grado": 2,
                  "pertenece": { "nombre": "SECCION PRUEBA" }
                },
                "entrega": {
                  "fecha": "2026-11-18",
                  "memo": "99999",
                  "motivo": "devolucion de prueba",
                  "operador": { "nombre": "OPERADOR PRUEBA", "cedula": "22222222" },
                  "recibo": { "codigo": "AAAA-BBBB-CCCC" },
                  "ropa_asignada": [
                    { "cantidad": "2", "ver_prendas": { "nombre": "CAMERA PRUEBA", "color": "ROJO", "talle": "M" } }
                  ]
                }
              }
            }
        """.trimIndent()

        val salida = File.createTempFile("recibo-api-", ".pdf").apply { deleteOnExit() }
        val recibo = ReciboRemoto(
            codigo = "AAAA-BBBB-CCCC",
            data = backendJson.parseToJsonElement(json).objeto()!!["data"]!!.objeto()!!,
            ruta = "",
            crudo = json,
        )

        ReceiptPdfGenerator().generar(recibo, salida)

        val texto = Loader.loadPDF(salida).use { documento -> PDFTextStripper().getText(documento) }
        assertTrue(texto.contains("POLICIA PRUEBA"))
        assertTrue(texto.contains("OPERADOR PRUEBA"))
        assertTrue(texto.contains("CAMERA PRUEBA"))
        assertTrue(texto.contains("AAAA-BBBB-CCCC"))
        assertTrue(texto.contains("18 de Noviembre del año 2026"))
        assertFalse(texto.contains("Adrian Almada"))
        assertFalse(texto.contains("LUIS DANIEL NANTES SILVA"))
    }
}

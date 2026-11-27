package com.lnantes.uifirmaelectronica.backend

import kotlinx.serialization.json.JsonObject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import java.time.LocalDate

/**
 * Genera el PDF del recibo directamente desde el JSON de Laravel.
 *
 * Antes se cargaba un recibo de ejemplo y se tapaban sus datos con rectángulos
 * blancos: el texto original se traslucía y parecía «un PDF pegado sobre otro».
 * Aquí se dibuja una página nueva, así que todo lo que aparece proviene de la
 * API. Las firmas se estampan después con `GeneradorReciboPdf`.
 */
class ReceiptPdfGenerator {
    private val fuente = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val negrita = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    fun generar(recibo: ReciboRemoto, salida: File): File {
        val datos = recibo.data
        val policia = datos.subobjeto("policia")
        val entrega = datos.subobjeto("entrega")
        val operador = entrega.subobjeto("operador")

        salida.parentFile?.mkdirs()
        if (salida.isFile) salida.delete()

        PDDocument().use { documento ->
            val pagina = PDPage(PDRectangle.A4)
            documento.addPage(pagina)

            PDPageContentStream(documento, pagina).use { flujo ->
                flujo.setNonStrokingColor(0f, 0f, 0f)
                flujo.setStrokingColor(0f, 0f, 0f)

                cabecera(flujo, pagina)
                texto(flujo, pagina, 298f, 118f, "RECIBO ENTREGA", 15f, centrado = true, bold = true)

                val fecha = entrega.texto("fecha", "fecha_emision")
                if (fecha.isNotBlank()) {
                    texto(
                        flujo, pagina, 545f, 152f, "Montevideo ${fechaCastellano(fecha)}",
                        9f, derecha = true,
                    )
                }

                campo(flujo, pagina, 180f, "Nombre Completo: ", policia.texto("nombreCompleto", "nombre"))
                campo(flujo, pagina, 192f, "Cédula Identidad: ", policia.texto("cedula", "ci", "documento"))
                campo(flujo, pagina, 204f, "Grado: ", grado(policia))
                campo(
                    flujo, pagina, 216f, "Dependencia: ",
                    policia.subobjeto("pertenece").texto("nombre")
                        .ifBlank { policia.texto("unidad", "dependencia") },
                )
                campo(flujo, pagina, 228f, "Memo nro: ", entrega.texto("memo"))
                campo(flujo, pagina, 240f, "Motivo: ", entreComillas(entrega.texto("motivo")))

                tablaPrendas(flujo, pagina, entrega["ropa_asignada"]?.arreglo().orEmpty())

                columnas(flujo, pagina, operador, policia)

                val codigo = entrega.subobjeto("recibo").texto("codigo").ifBlank { recibo.codigo }
                verificacion(flujo, pagina, codigo)
                texto(
                    flujo, pagina, 298f, 800f,
                    "Documento emitido electrónicamente. Conserve este comprobante para futuras consultas.",
                    8f, centrado = true,
                )
            }
            documento.save(salida)
        }
        return salida
    }

    private fun cabecera(flujo: PDPageContentStream, pagina: PDPage) {
        val lineas = listOf(
            "Jefatura de Policía de Montevideo",
            "Dirección logística y Apoyo",
            "Cuarto 3137 bis",
            "Tel: 2030 2955",
            "logisticayapoyo.montevideo@minterior.gub.uy",
        )
        lineas.forEachIndexed { indice, linea ->
            texto(flujo, pagina, 552f, 40f + indice * 12f, linea, 8f, derecha = true)
        }
    }

    private fun tablaPrendas(
        flujo: PDPageContentStream,
        pagina: PDPage,
        prendas: List<kotlinx.serialization.json.JsonElement>,
    ) {
        val columnas = listOf(90f, 280f, 370f, 455f, 540f)
        val altura = pagina.cropBox.height
        val cabecera = listOf("Nombre", "Color", "Talle", "Cantidad")

        texto(flujo, pagina, columnas[0] + 4f, 288f, cabecera[0], 9f, bold = true)
        texto(flujo, pagina, columnas[1] + 4f, 288f, cabecera[1], 9f, bold = true)
        texto(flujo, pagina, columnas[2] + 4f, 288f, cabecera[2], 9f, bold = true)
        texto(flujo, pagina, columnas[3] + 4f, 288f, cabecera[3], 9f, bold = true)

        flujo.setStrokingColor(0.75f, 0.75f, 0.75f)
        flujo.setLineWidth(0.6f)
        val filas = (0..3).map { 296f + it * 20f }
        filas.forEach { yTop ->
            flujo.moveTo(columnas.first(), altura - yTop)
            flujo.lineTo(columnas.last(), altura - yTop)
            flujo.stroke()
        }
        columnas.forEach { x ->
            flujo.moveTo(x, altura - 296f)
            flujo.lineTo(x, altura - 356f)
            flujo.stroke()
        }
        flujo.setStrokingColor(0f, 0f, 0f)

        prendas.take(3).forEachIndexed { indice, elemento ->
            val prenda = elemento.objeto() ?: return@forEachIndexed
            val detalle = prenda.subobjeto("ver_prendas")
            val y = 310f + indice * 20f
            texto(flujo, pagina, columnas[0] + 4f, y, detalle.texto("nombre", "prenda"), 9f)
            texto(flujo, pagina, columnas[1] + 4f, y, detalle.texto("color"), 9f)
            texto(flujo, pagina, columnas[2] + 4f, y, detalle.texto("talle"), 9f)
            texto(flujo, pagina, columnas[3] + 4f, y, prenda.texto("cantidad"), 9f)
        }
    }

    private fun columnas(
        flujo: PDPageContentStream,
        pagina: PDPage,
        operador: JsonObject,
        policia: JsonObject,
    ) {
        texto(flujo, pagina, 90f, 386f, "Entrega:", 10f, bold = true)
        texto(flujo, pagina, 377f, 386f, "Recibe:", 10f, bold = true)
        texto(flujo, pagina, 90f, 404f, "Firma:", 10f)
        texto(flujo, pagina, 377f, 404f, "Firma:", 10f)

        // Líneas donde se estampan las firmas (coinciden con ZonasRecibo).
        flujo.setStrokingColor(0.6f, 0.6f, 0.6f)
        flujo.setLineWidth(0.5f)
        val alto = pagina.cropBox.height
        listOf(150f to 350f, 435f to 580f).forEach { (desde, hasta) ->
            flujo.moveTo(desde, alto - 418f)
            flujo.lineTo(hasta, alto - 418f)
        }
        flujo.setStrokingColor(0f, 0f, 0f)

        campo(flujo, pagina, 422f, "Contra Firma: ", operador.texto("nombre"), x = 90f, tamano = 9f)
        campo(flujo, pagina, 440f, "Cédula: ", operador.texto("cedula", "ci"), x = 90f, tamano = 9f)
        campo(
            flujo, pagina, 422f, "Contra Firma: ",
            policia.texto("nombreCompleto", "nombre"), x = 377f, tamano = 9f,
        )
        campo(
            flujo, pagina, 440f, "Cédula: ",
            policia.texto("cedula", "ci"), x = 377f, tamano = 9f,
        )
    }

    private fun verificacion(flujo: PDPageContentStream, pagina: PDPage, codigo: String) {
        texto(flujo, pagina, 45f, 505f, "DOCUMENTO VERIFICABLE", 10f, bold = true)
        texto(
            flujo, pagina, 45f, 520f,
            "Este recibo cuenta con un código único de validación.", 9f,
        )
        campo(flujo, pagina, 535f, "Código: ", codigo, x = 45f, tamano = 10f)
        texto(
            flujo, pagina, 45f, 550f,
            "Presente este código en la Jefatura para verificar el documento.", 9f,
        )
    }

    private fun campo(
        flujo: PDPageContentStream,
        pagina: PDPage,
        yTop: Float,
        etiqueta: String,
        valor: String,
        x: Float = 90f,
        tamano: Float = 10f,
    ) = texto(flujo, pagina, x, yTop, etiqueta + seguro(valor), tamano)

    private fun texto(
        flujo: PDPageContentStream,
        pagina: PDPage,
        x: Float,
        yTop: Float,
        valor: String,
        tamano: Float,
        centrado: Boolean = false,
        derecha: Boolean = false,
        bold: Boolean = false,
    ) {
        val contenido = seguro(valor)
        if (contenido.isBlank()) return
        val tipo = if (bold) negrita else fuente
        val ancho = tipo.getStringWidth(contenido) * tamano / 1000f
        val origen = when {
            centrado -> x - ancho / 2f
            derecha -> x - ancho
            else -> x
        }
        flujo.beginText()
        flujo.setFont(tipo, tamano)
        flujo.newLineAtOffset(origen, pagina.cropBox.height - yTop - tamano)
        flujo.showText(cortar(contenido, 465f, tamano, tipo))
        flujo.endText()
    }

    /** Helvetica no soporta caracteres fuera de WinAnsi; los reemplaza. */
    private fun seguro(texto: String): String = texto
        .replace('\n', ' ')
        .replace('\r', ' ')
        .map { if (it.code in 32..255) it else '?' }
        .joinToString("")
        .trim()

    private fun cortar(
        texto: String,
        ancho: Float,
        tamano: Float,
        tipo: PDType1Font = fuente,
    ): String {
        val maximo = ((ancho - 4f) / (tamano * 0.52f)).toInt().coerceAtLeast(4)
        return if (texto.length <= maximo) texto else texto.take(maximo - 1) + "."
    }

    private fun grado(policia: JsonObject): String {
        val codigo = policia.numero("grado").takeIf { it > 0 }
            ?: policia.texto("grado").toLongOrNull()
            ?: return ""
        return GRADOS[codigo].orEmpty()
    }

    private fun entreComillas(valor: String): String =
        if (valor.isBlank()) "" else "\"$valor\""

    private fun fechaCastellano(valor: String): String = runCatching {
        val fecha = LocalDate.parse(valor.take(10))
        "${fecha.dayOfMonth} de ${MESES[fecha.monthValue - 1]} del año ${fecha.year}"
    }.getOrDefault(valor)

    private companion object {
        val MESES = listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        val GRADOS = mapOf(
            1L to "Agente",
            2L to "Cabo",
            3L to "Sargento",
            4L to "Sub Oficial Mayor",
            5L to "Oficial Ayte",
            6L to "Oficial Ppl",
            7L to "Sub Crio",
            8L to "Comisario",
            9L to "Comisario Mayor"
        )
    }
}

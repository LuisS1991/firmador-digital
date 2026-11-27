package org.lnantes.firmadigitalservidor.pdfs

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.lnantes.firmadigitalservidor.avanzada.FirmadorAvanzadoPkcs11
import org.lnantes.firmadigitalservidor.avanzada.ValidadorCertificado
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Helper de firma de documentos PDF.
 *
 * Toda la parte criptográfica (token/cédula, clave privada y certificados)
 * delegada en [FirmadorAvanzadoPkcs11]; aquí solo se gestiona el documento:
 * se estampa la firma (PAdES con sello opcionalmente visible) y se muestra el
 * resultado en el visor del sistema para imprimir, enviar por correo, etc.
 */
class FirmadorPDF(
    private val firmador: SignatureInterface,
    private val nombreFirmante: () -> String? = { null }
) {

    /** Constructor habitual: usa el token/cédula de la carpeta «avanzada». */
    constructor(firmador: FirmadorAvanzadoPkcs11) : this(firmador, { firmador.obtenerNombreComun() })

    /** Resultado de la última validación del certificado, para trazabilidad en la UI. */
    var ultimaValidacion: ValidadorCertificado.Resultado? = null
        private set

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Flujo completo encapsulado: abre el dispositivo PKCS#11, estampa la firma
     * en el PDF y muestra el documento resultante en el visor del sistema.
     */
    fun abrirEstamparYMostrar(
        pdfEntrada: File,
        pdfSalida: File = rutaSalidaPorDefecto(pdfEntrada),
        pinUsuario: CharArray? = null,
        motivo: String = "Recepcion de indumentaria",
        ubicacion: String = "Montevideo, Uruguay",
        conSelloVisible: Boolean = true,
        sello: File? = null,
        zonaSello: PDRectangle? = null,
        mostrarAlTerminar: Boolean = true,
        verificarRevocacion: Boolean = true
    ): File {
        val dispositivo = firmador as? FirmadorAvanzadoPkcs11
            ?: error("Este método requiere un FirmadorAvanzadoPkcs11; use estamparFirma con el dispositivo ya inicializado")
        dispositivo.inicializarDispositivo(pinUsuario)
        return try {
            val firmado = estamparFirma(
                pdfEntrada = pdfEntrada,
                pdfSalida = pdfSalida,
                motivo = motivo,
                ubicacion = ubicacion,
                conSelloVisible = conSelloVisible,
                sello = sello,
                zonaSello = zonaSello,
                verificarRevocacion = verificarRevocacion
            )
            if (mostrarAlTerminar) {
                mostrarPdf(firmado)
            }
            firmado
        } finally {
            dispositivo.liberarProveedor()
        }
    }

    /**
     * Estampa la firma en el PDF de entrada y guarda el resultado de forma incremental.
     * Requiere que el dispositivo ya esté inicializado (ver [FirmadorAvanzadoPkcs11.inicializarDispositivo]).
     */
    fun estamparFirma(
        pdfEntrada: File,
        pdfSalida: File = rutaSalidaPorDefecto(pdfEntrada),
        motivo: String = "Recepcion de indumentaria",
        ubicacion: String = "Montevideo, Uruguay",
        conSelloVisible: Boolean = true,
        sello: File? = null,
        zonaSello: PDRectangle? = null,
        verificarRevocacion: Boolean = true
    ): File {
        if (firmador is FirmadorAvanzadoPkcs11) {
            check(firmador.estaInicializado()) {
                "Debe inicializar el dispositivo PKCS#11 antes de estampar la firma"
            }
            // Requisitos 5 y 6 de la guía UCE: nunca firmar con certificado
            // vencido o revocado (AGESIC rechazaría esa firma).
            val validacion = firmador.validarCertificado(verificarRevocacion)
            ultimaValidacion = validacion
            if (!validacion.vigente) {
                throw IllegalStateException("No se firmó: ${validacion.motivo}. ${validacion.resumen}")
            }
            if (validacion.revocacion == ValidadorCertificado.EstadoRevocacion.REVOCADA) {
                throw IllegalStateException(
                    "No se firmó: el certificado está revocado (${validacion.detalleRevocacion}). ${validacion.resumen}"
                )
            }
        }
        require(pdfEntrada.isFile) { "El PDF de entrada no existe: ${pdfEntrada.absolutePath}" }
        require(pdfSalida.absolutePath != pdfEntrada.absolutePath) {
            "El PDF de salida debe ser distinto del original"
        }

        pdfSalida.parentFile?.mkdirs()

        // Se carga la entrada y se guarda incrementalmente en otra ruta.
        // PDFBox escribe la base completa más el incremento, por lo que los
        // trazos de la firma común y el resto del contenido se conservan.
        // No se debe cargar `pdfSalida` y escribirla a la vez: el handle de
        // lectura queda abierto y el resultado se corrompe.
        Loader.loadPDF(pdfEntrada).use { documento: PDDocument ->
            require(documento.numberOfPages > 0) { "El PDF no contiene páginas" }

            val firmante = nombreFirmante()?.takeIf { it.isNotBlank() } ?: "Firmante"
            val fechaFirma = Calendar.getInstance()

            val signature = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                name = firmante
                location = ubicacion
                reason = motivo
                signDate = fechaFirma
            }

            val options = SignatureOptions().apply {
                preferredSignatureSize = 24576
            }

            var visualStream: ByteArrayInputStream? = null

            try {
                if (conSelloVisible) {
                    val ultimaPaginaIndex = documento.numberOfPages - 1
                    val pagina: PDPage = documento.getPage(ultimaPaginaIndex)
                    val rect = zonaSello ?: PDRectangle(340f, 40f, 220f, 65f)

                    val bytesVisual = generarPdfSelloVisible(
                        mediaBox = pagina.mediaBox,
                        rect = rect,
                        firmante = firmante,
                        fecha = fechaFirma,
                        motivo = motivo,
                        sello = sello
                    )
                    visualStream = ByteArrayInputStream(bytesVisual)
                    options.setVisualSignature(visualStream)
                }

                // El firmador de «avanzada» resuelve el bloque CMS con el token.
                documento.addSignature(signature, firmador, options)

                FileOutputStream(pdfSalida).use { fos ->
                    documento.saveIncremental(fos)
                }
            } finally {
                visualStream?.close()
                options.close()
            }
        }

        return pdfSalida
    }

    /** Nombre sugerido para el PDF firmado: `<original>_firmado.pdf`. */
    fun rutaSalidaPorDefecto(pdfEntrada: File): File =
        File(pdfEntrada.parentFile, "${pdfEntrada.nameWithoutExtension}_firmado.pdf")

    /**
     * Abre el PDF indicado en el visor predeterminado para imprimir o enviar por correo.
     * Sirve tanto para el original (previsualización) como para el resultado firmado.
     */
    fun mostrarPdf(archivoPdf: File) = abrirEnVisor(archivoPdf)

    companion object {
        /**
         * Variante estática para previsualizar sin necesidad de abrir el dispositivo.
         *
         * IMPORTANTE: se ejecuta en un hilo aparte. `Desktop.open()` bloquea al
         * hilo llamante hasta que cierra el visor, lo que congelaba la UI JavaFX
         * al elegir un PDF. Se prefiere `xdg-open`/`start`/`open` con `start()`,
         * que devuelve el control de inmediato.
         */
        @JvmStatic
        fun abrirEnVisor(archivoPdf: File) {
            require(archivoPdf.isFile) { "No existe el PDF a mostrar: ${archivoPdf.absolutePath}" }

            Thread {
                val os = System.getProperty("os.name").lowercase(Locale.ROOT)
                val comando = when {
                    os.contains("win") -> listOf("cmd", "/c", "start", "", archivoPdf.absolutePath)
                    os.contains("mac") -> listOf("open", archivoPdf.absolutePath)
                    else -> listOf("xdg-open", archivoPdf.absolutePath)
                }

                val lanzado = runCatching {
                    ProcessBuilder(comando)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                }.isSuccess

                // Último recurso en sistemas donde no hay xdg-open/open/start.
                if (!lanzado && Desktop.isDesktopSupported() &&
                    Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
                ) {
                    runCatching { Desktop.getDesktop().open(archivoPdf) }
                }
            }.apply {
                isDaemon = true
                name = "VisorPDF-${archivoPdf.name}"
            }.start()
        }
    }

    private fun generarPdfSelloVisible(
        mediaBox: PDRectangle,
        rect: PDRectangle,
        firmante: String,
        fecha: Calendar,
        motivo: String,
        sello: File? = null
    ): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage(mediaBox)
            doc.addPage(page)

            val acroForm = PDAcroForm(doc).apply {
                doc.documentCatalog.acroForm = this
            }

            val field = PDSignatureField(acroForm)
            val widget: PDAnnotationWidget = field.widgets.first().apply {
                rectangle = rect
                this.page = page
                isPrinted = true
            }

            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val fechaStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-UY"))
                .format(fecha.time)

            val appStream = PDAppearanceStream(doc).apply {
                bBox = PDRectangle(0f, 0f, rect.width, rect.height)
                resources = PDResources()
            }

            PDPageContentStream(doc, appStream).use { cs: PDPageContentStream ->
                cs.setStrokingColor(0.2f, 0.2f, 0.2f)
                cs.setLineWidth(1f)
                cs.addRect(1f, 1f, rect.width - 2f, rect.height - 2f)
                cs.stroke()

                // SELLO.png se dibuja dentro del propio appearance de la firma,
                // por lo que queda cubierto por la firma PAdES (no es un sello suelto).
                val imagenSello = sello?.takeIf { it.isFile }?.let {
                    PDImageXObject.createFromFile(it.absolutePath, doc)
                }
                val textoX: Float
                if (imagenSello != null) {
                    val espacioImagen = rect.width * 0.38f
                    val escala = minOf(
                        (espacioImagen - 6f) / imagenSello.width,
                        (rect.height - 8f) / imagenSello.height
                    )
                    val anchoImagen = imagenSello.width * escala
                    val altoImagen = imagenSello.height * escala
                    cs.drawImage(
                        imagenSello,
                        4f,
                        (rect.height - altoImagen) / 2f,
                        anchoImagen,
                        altoImagen
                    )
                    textoX = espacioImagen + 2f
                } else {
                    textoX = 8f
                }

                val tamanoTexto = if (imagenSello != null) 7f else 8f
                val salto = if (imagenSello != null) 10f else 11f
                cs.beginText()
                cs.setFont(font, tamanoTexto)
                cs.newLineAtOffset(textoX, rect.height - 14f)
                cs.showText("Firmado digitalmente por:")
                cs.newLineAtOffset(0f, -salto)
                cs.showText(firmante.take(38))
                cs.newLineAtOffset(0f, -salto)
                cs.showText("Fecha: $fechaStr")
                cs.newLineAtOffset(0f, -salto)
                cs.showText("Motivo: ${motivo.take(36)}")
                cs.endText()
            }

            widget.appearance = PDAppearanceDictionary().apply {
                setNormalAppearance(appStream)
            }

            page.annotations.add(widget)
            acroForm.fields.add(field)

            ByteArrayOutputStream().use { out ->
                doc.save(out)
                return out.toByteArray()
            }
        }
    }
}

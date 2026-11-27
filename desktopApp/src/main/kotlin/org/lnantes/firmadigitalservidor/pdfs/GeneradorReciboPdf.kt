package org.lnantes.firmadigitalservidor.pdfs

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.lnantes.firmadigitalservidor.ContenidoTextoFirma
import org.lnantes.firmadigitalservidor.PuntoVector
import org.lnantes.firmadigitalservidor.avanzada.FirmadorAvanzadoPkcs11
import java.io.File

/**
 * Zona del recibo donde se estampa una firma. Coordenadas en puntos PDF
 * (origen abajo-izquierda), medidas sobre el recibo A4 de ejemplo
 * (`docs/455527453.pdf`, 596 x 842 pt).
 */
data class ZonaFirma(
    val etiqueta: String,
    val x: Float,
    val y: Float,
    val ancho: Float,
    val alto: Float
) {
    fun aRectangulo(): PDRectangle = PDRectangle(x, y, ancho, alto)

    /** Reescala la zona si el PDF no es A4 (por ejemplo, carta o una página rotada). */
    fun enPagina(pagina: PDPage): ZonaFirma {
        val escalaX = pagina.cropBox.width / ANCHO_REFERENCIA
        val escalaY = pagina.cropBox.height / ALTO_REFERENCIA
        return copy(
            x = x * escalaX,
            y = y * escalaY,
            ancho = ancho * escalaX,
            alto = alto * escalaY
        )
    }

    companion object {
        const val ANCHO_REFERENCIA = 596f
        const val ALTO_REFERENCIA = 842f
    }
}

/**
 * Posiciones de ambas firmas en el recibo de ejemplo, en coordenadas PDF
 * estándar (origen abajo-izquierda). Cubren la línea «Firma:» de cada columna.
 */
object ZonasRecibo {
    /** Columna izquierda: «Entrega: Firma:» → firma avanzada con token. */
    val ENTREGA = ZonaFirma("Entrega", 140f, 426f, 210f, 18f)

    /** Columna derecha: «Recibe: Firma:» → firma común por trazos del móvil. */
    val RECIBE = ZonaFirma("Recibe", 410f, 426f, 165f, 18f)
}

/**
 * Helper 100 % local para generar el recibo con la firma común, la avanzada
 * o ambas. No hace POST, no consulta SQLite, no abre ventanas y no envía nada
 * por TCP: el acto de firmar ocurre siempre en esta máquina.
 *
 * La lógica de comunicación/AOA sigue viviendo en `serveraos`; este helper
 * solo recibe los trazos ya capturados (`ContenidoTextoFirma`) y el firmador
 * PKCS#11 ya disponible.
 */
class GeneradorReciboPdf(
    private val pdfRecibo: File = recurso(Recurso.DOCUMENTO),
    private val sello: File = recurso(Recurso.SELLO),
    private val onLog: (String) -> Unit = {}
) {

    /**
     * Estampa los trazos del móvil en la zona «Recibe». Los dibuja como
     * vectores (no como imagen) para que no se pixeleen al imprimir.
     */
    fun estamparFirmaComun(
        pdfSalida: File,
        contenido: ContenidoTextoFirma,
        pdfEntrada: File = pdfRecibo,
        zona: ZonaFirma = ZonasRecibo.RECIBE,
        adaptarZona: Boolean = true
    ): File {
        validarEntrada(pdfEntrada, pdfSalida)
        prepararSalida(pdfEntrada, pdfSalida)
        val trazos = contenido.strokes.filter { it.isNotEmpty() }
        // El móvil dibuja sobre un canvas en px. Si no informa su tamaño se usa
        // la referencia del equipo de campo (720 x 1280), para no deformar los
        // trazos metiéndolos en una caja mucho más chica que el dibujo original.
        val anchoArea = contenido.anchoArea?.toFloat()?.takeIf { it > 0f } ?: 720f
        val altoArea = contenido.altoArea?.toFloat()?.takeIf { it > 0f } ?: 1280f

        Loader.loadPDF(pdfEntrada).use { documento ->
            require(documento.numberOfPages > 0) { "El PDF no contiene páginas" }
            val pagina = documento.getPage(documento.numberOfPages - 1)
            val destino = if (adaptarZona) zona.enPagina(pagina) else zona
            // El recibo de ejemplo fue generado por cairo y deja un
            // `1 0 0 -1 0 842 cm` activo al final del content stream: allí las
            // coordenadas van invertidas. En un PDF normal se dibuja directo.
            val zonaDibujo = if (contentInvertido(pagina)) {
                destino.copy(y = pagina.cropBox.height - destino.y - destino.alto)
            } else {
                destino
            }

            if (trazos.isNotEmpty()) {
                PDPageContentStream(
                    documento,
                    pagina,
                    PDPageContentStream.AppendMode.APPEND,
                    true
                ).use { cs ->
                    cs.setStrokingColor(0.08f, 0.16f, 0.36f)
                    cs.setLineWidth(1.4f)
                    // Mismos extremos/uniones redondos que el canvas de Compose:
                    // así el trazo se ve igual en el móvil y en el PDF.
                    cs.setLineCapStyle(1)
                    cs.setLineJoinStyle(1)
                    normalizar(trazos, zonaDibujo, anchoArea, altoArea).forEach { trazo ->
                        trazo.forEachIndexed { indice, punto ->
                            if (indice == 0) cs.moveTo(punto.first, punto.second)
                            else cs.lineTo(punto.first, punto.second)
                        }
                        cs.stroke()
                    }
                }
            }

            pdfSalida.parentFile?.mkdirs()
            documento.save(pdfSalida)
        }

        onLog("🖊️ Firma común estampada en '${zona.etiqueta}' → ${pdfSalida.name} (${trazos.size} trazo(s)).")
        return pdfSalida
    }

    /**
     * Firma el PDF con el token/cédula (PAdES) y dibuja SELLO.png dentro del
     * appearance visible de la firma, en la zona «Entrega».
     *
     * Si [inicializar] es true abre y cierra la sesión PKCS#11 con [pin].
     * Si el dispositivo ya estaba inicializado, pase false; en ese caso el
     * llamador es responsable de liberarlo.
     */
    fun estamparFirmaAvanzada(
        pdfSalida: File,
        firmador: FirmadorAvanzadoPkcs11,
        pin: CharArray? = null,
        pdfEntrada: File = pdfRecibo,
        zona: ZonaFirma = ZonasRecibo.ENTREGA,
        adaptarZona: Boolean = true,
        motivo: String = "Recepcion de indumentaria",
        ubicacion: String = "Montevideo, Uruguay",
        nombreFirmante: String? = null,
        verificarRevocacion: Boolean = true,
        inicializar: Boolean = true
    ): File {
        validarEntrada(pdfEntrada, pdfSalida)
        prepararSalida(pdfEntrada, pdfSalida)
        val rect = if (adaptarZona) {
            Loader.loadPDF(pdfEntrada).use { documento ->
                zona.enPagina(documento.getPage(documento.numberOfPages - 1)).aRectangulo()
            }
        } else {
            zona.aRectangulo()
        }

        fun estampar() {
            val helper = FirmadorPDF(firmador) { nombreFirmante ?: firmador.obtenerNombreComun() }
            helper.estamparFirma(
                pdfEntrada = pdfEntrada,
                pdfSalida = pdfSalida,
                motivo = motivo,
                ubicacion = ubicacion,
                conSelloVisible = true,
                sello = sello,
                zonaSello = rect,
                verificarRevocacion = verificarRevocacion
            )
        }

        if (inicializar) {
            firmador.inicializarDispositivo(pin)
            try {
                estampar()
            } finally {
                firmador.liberarProveedor()
            }
        } else {
            check(firmador.estaInicializado()) {
                "El firmador PKCS#11 debe estar inicializado cuando inicializar=false"
            }
            estampar()
        }

        onLog("🔐 Firma avanzada estampada en '${zona.etiqueta}' (SELLO.png) → ${pdfSalida.name}.")
        return pdfSalida
    }

    /** Recibo completo: primero la firma común y después la PAdES sobre ese resultado. */
    fun generarReciboFirmado(
        pdfSalida: File,
        contenido: ContenidoTextoFirma,
        firmador: FirmadorAvanzadoPkcs11,
        pin: CharArray? = null,
        motivo: String = "Recepcion de indumentaria",
        ubicacion: String = "Montevideo, Uruguay",
        verificarRevocacion: Boolean = true,
        inicializar: Boolean = true
    ): File {
        validarEntrada(pdfRecibo, pdfSalida)
        val temporal = File(
            pdfSalida.parentFile,
            ".${pdfSalida.nameWithoutExtension}_con_firma_comun.pdf"
        )
        try {
            estamparFirmaComun(temporal, contenido, pdfEntrada = pdfRecibo)
            estamparFirmaAvanzada(
                pdfSalida = pdfSalida,
                firmador = firmador,
                pin = pin,
                pdfEntrada = temporal,
                motivo = motivo,
                ubicacion = ubicacion,
                verificarRevocacion = verificarRevocacion,
                inicializar = inicializar
            )
        } finally {
            temporal.delete()
        }
        onLog("📄 Recibo con ambas firmas generado: ${pdfSalida.absolutePath}")
        return pdfSalida
    }

    /** Detecta el `cm` con escala Y negativa que usan los PDF generados por cairo. */
    private fun contentInvertido(pagina: PDPage): Boolean {
        val streams = pagina.contentStreams ?: return false
        if (!streams.hasNext()) return false
        val inicio = streams.next().createInputStream().use { entrada ->
            String(entrada.readNBytes(4096), Charsets.ISO_8859_1)
        }
        return Regex("""^\s*1\s+0\s+0\s+-1\s+0\s+[\d.]+\s+cm""").containsMatchIn(inicio)
    }

    private fun validarEntrada(pdfEntrada: File, pdfSalida: File) {
        require(pdfEntrada.isFile) { "El PDF de entrada no existe: ${pdfEntrada.absolutePath}" }
        require(pdfEntrada.absolutePath != pdfSalida.absolutePath) {
            "El PDF de salida debe ser distinto del original"
        }
    }

    /** Evita el aviso de PDFBox al sobrescribir una salida de una ejecución anterior. */
    private fun prepararSalida(pdfEntrada: File, pdfSalida: File) {
        pdfSalida.parentFile?.mkdirs()
        if (pdfSalida.isFile && pdfSalida.absolutePath != pdfEntrada.absolutePath) {
            pdfSalida.delete()
        }
    }

    /**
     * Lleva los puntos del canvas del móvil al rectángulo «Firma:» del recibo.
     *
     * Si las coordenadas caben en el canvas declarado (o en la referencia
     * 720 x 1280) se respeta la posición relativa exacta de cada punto: el
     * trazo ocupa en el PDF la misma franja que ocupaba en pantalla. Antes se
     * ajustaba sólo la caja envolvente y con trazos de pantalla completa el
     * resultado quedaba reducido a unos pocos puntos en el centro de la línea.
     *
     * Para payloads antiguos sin canvas conocido se usa la caja envolvente,
     * estirándola en X e Y para que la firma ocupe toda la línea disponible.
     */
    private fun normalizar(
        trazos: List<List<PuntoVector>>,
        zona: ZonaFirma,
        anchoArea: Float,
        altoArea: Float
    ): List<List<Pair<Float, Float>>> {
        val puntos = trazos.flatten()
        val margen = 2f
        val anchoUtil = (zona.ancho - 2 * margen).coerceAtLeast(1f)
        val altoUtil = (zona.alto - 2 * margen).coerceAtLeast(1f)
        // Algunos móviles envían coordenadas ya normalizadas (0..1).
        val anchoEfectivo = if (puntos.maxOf { it.x } <= 1.0) 1f else anchoArea
        val altoEfectivo = if (puntos.maxOf { it.y } <= 1.0) 1f else altoArea
        val cabeEnCanvas = puntos.all { punto ->
            punto.x >= 0.0 && punto.x <= anchoEfectivo && punto.y >= 0.0 && punto.y <= altoEfectivo
        }

        if (cabeEnCanvas && anchoEfectivo > 0f && altoEfectivo > 0f) {
            // Mapeo lineal directo. Y se invierte: el móvil mide desde arriba,
            // el contenido PDF (o su CTM invertida) desde abajo.
            return trazos.map { trazo ->
                trazo.map { punto ->
                    (zona.x + margen + (punto.x / anchoEfectivo).toFloat() * anchoUtil) to
                        (zona.y + margen + (1.0 - punto.y / altoEfectivo).toFloat() * altoUtil)
                }
            }
        }

        val minX = puntos.minOf { it.x }
        val maxX = puntos.maxOf { it.x }
        val minY = puntos.minOf { it.y }
        val maxY = puntos.maxOf { it.y }
        val rangoX = ((maxX - minX).takeIf { it > 0.0001 } ?: 1.0).toFloat()
        val rangoY = ((maxY - minY).takeIf { it > 0.0001 } ?: 1.0).toFloat()
        val escalaX = anchoUtil / rangoX
        val escalaY = altoUtil / rangoY
        return trazos.map { trazo ->
            trazo.map { punto ->
                (zona.x + margen + (punto.x - minX).toFloat() * escalaX) to
                    (zona.y + margen + (maxY - punto.y).toFloat() * escalaY)
            }
        }
    }

    private object Recurso {
        const val DOCUMENTO = "org/lnantes/firmadigitalservidor/docs/455527453.pdf"
        const val SELLO = "org/lnantes/firmadigitalservidor/icons/SELLO.png"
    }
}

/** Busca el recurso en el directorio de desarrollo o lo extrae del JAR. */
private fun recurso(ruta: String): File {
    val enDirectorio = File("src/main/resources", ruta)
    if (enDirectorio.isFile) return enDirectorio

    val extension = ruta.substringAfterLast('.', "tmp")
    val stream = GeneradorReciboPdf::class.java.classLoader.getResourceAsStream(ruta)
        ?: error("No se encontró el recurso '$ruta' en el classpath")
    return File.createTempFile("recibo-firma-", ".$extension").apply {
        deleteOnExit()
        outputStream().use { stream.copyTo(it) }
    }
}

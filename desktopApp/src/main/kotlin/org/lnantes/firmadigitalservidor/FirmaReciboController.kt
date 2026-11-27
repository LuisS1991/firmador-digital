package org.lnantes.firmadigitalservidor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.lnantes.firmadigitalservidor.compresion.PreparadorPostGzip
import org.lnantes.firmadigitalservidor.servicios.ServicioApiFirma
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CompletableFuture

/**
 * Estado del recibo/firma y fachada para la capa REST.
 *
 * Toda la comunicación HTTP la hace [ServicioApiFirma]; este controller solo
 * mantiene el código, el payload local y expone el mismo contrato de siempre a
 * la UI y a `serveraos`.
 */
class FirmaReciboController(
    private val servicioApi: ServicioApiFirma
) {
    /** Constructor de compatibilidad: recibe la URL completa del endpoint. */
    constructor(
        endpointUrl: String,
        bearerToken: String? = null,
        httpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build(),
        preparadorPost: PreparadorPostGzip = PreparadorPostGzip()
    ) : this(
        servicioApi = ServicioApiFirma.desdeEndpoint(
            endpointUrl = endpointUrl,
            bearerToken = bearerToken,
            httpClient = httpClient,
            preparadorPost = preparadorPost
        )
    )

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    private var codigoActual: String? = null
    private var payloadActual: PayloadFirma? = null
    private var firmaGuardada: Int = 1
    /** Token del deep link; se envía como parámetro `token` en el POST. */
    private var tokenDeepLink: String? = null
    /** Datos del firmante de trazos, para que Laravel sepa quién firmó. */
    private var firmante: String? = null
    private var lado: String? = null

    fun setCodigo(rawCodigo: String) {
        // Extrae el código XXXX-XXXX-XXXX si viene como deep link (ej. app://firmar?codigo=D896-C2E5-27D7 o argumento directo)
        val match = Regex("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")
            .find(rawCodigo.trim().uppercase())

        codigoActual = match?.value ?: rawCodigo.trim().uppercase()
    }

    fun getCodigo(): String? = codigoActual

    /** Carga el token recibido en `firmaapp://firmar?id=...&token=...`. */
    fun setToken(valor: String?) {
        tokenDeepLink = valor?.trim()?.takeIf { it.isNotBlank() }
    }

    fun getToken(): String? = tokenDeepLink

    fun setFirmante(valor: String?) {
        firmante = valor?.trim()?.takeIf { it.isNotBlank() }
    }

    fun setLado(valor: String?) {
        lado = valor?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    }

    /**
     * 1 = la firma quedó guardada en SQLite; 0 = no se pudo guardar.
     */
    fun setFirmaGuardada(valor: Int) {
        firmaGuardada = if (valor == 1) 1 else 0
    }

    fun getFirmaGuardada(): Int = firmaGuardada

    /**
     * Body LOCAL (incluye `firma_base64`) usado para SQLite y para la vista
     * previa. El POST a producción se prepara aparte con [prepararEnvio].
     */
    fun getBodyJson(): String? {
        val codigo = codigoActual ?: return null
        val payload = payloadActual ?: return null
        return json.encodeToString(
            RequestFirmaRecibo(
                codigo = codigo,
                payload = payload,
                firmaGuardada = firmaGuardada,
                token = tokenDeepLink,
                firmante = firmante,
                lado = lado
            )
        )
    }

    /**
     * Prepara el body del POST: quita la imagen base64 y comprime con GZIP.
     * Devuelve null si todavía no hay código o payload.
     */
    fun prepararEnvio(): PreparadorPostGzip.Resultado? {
        val codigo = codigoActual ?: return null
        val payload = payloadActual ?: return null
        return servicioApi.prepararEnvio(codigo, payload, firmaGuardada, tokenDeepLink, firmante, lado)
    }

    /**
     * Genera un payload de prueba con trazos vectoriales y su firma_base64 correspondiente.
     */
    fun generarFirmaTest() {
        val epochMillis = Instant.now().toEpochMilli()
        val strokesTest = listOf(
            listOf(
                PuntoVector(340.45, 416.31),
                PuntoVector(414.80, 465.51),
                PuntoVector(499.39, 518.87),
                PuntoVector(589.08, 576.34),
                PuntoVector(675.71, 632.81)
            ),
            listOf(
                PuntoVector(404.86, 603.54),
                PuntoVector(520.10, 662.07),
                PuntoVector(614.44, 746.52),
                PuntoVector(590.00, 837.07),
                PuntoVector(432.00, 1063.00)
            )
        )

        val contenido = ContenidoTextoFirma(
            tipo = "TRAZO_FIRMA_VECTORIAL",
            timestamp = epochMillis,
            strokes = strokesTest
        )

        val contenidoJsonStr = json.encodeToString(contenido)
        val base64Str = Base64.getEncoder()
            .encodeToString(contenidoJsonStr.toByteArray(StandardCharsets.UTF_8))

        payloadActual = PayloadFirma(
            timestamp = Instant.now().toString(),
            tamanoBytes = base64Str.toByteArray(StandardCharsets.UTF_8).size,
            contenidoTexto = contenido,
            firmaBase64 = base64Str
        )
    }

    /**
     * Permite asignar un payload ya construido desde tu capturador real de trazos.
     */
    fun setPayload(payload: PayloadFirma) {
        this.payloadActual = payload
    }

    /** Acceso al servicio REST para quien quiera consumir la API directamente. */
    fun getServicioApi(): ServicioApiFirma = servicioApi

    /**
     * Envía la petición POST de forma asíncrona a través de [ServicioApiFirma].
     * Prepara el body con GZIP antes de enviarlo (sin `firma_base64`).
     */
    fun enviarFirmaPost(): CompletableFuture<ResultadoEnvioFirma> {
        val codigo = codigoActual
        if (codigo.isNullOrBlank()) {
            return CompletableFuture.completedFuture(
                ResultadoEnvioFirma(ok = false, statusCode = null, mensaje = "No hay código de recibo definido.")
            )
        }
        val payload = payloadActual ?: return CompletableFuture.completedFuture(
            ResultadoEnvioFirma(ok = false, statusCode = null, mensaje = "No hay payload de firma generado.")
        )

        return servicioApi.enviarFirma(codigo, payload, firmaGuardada, tokenDeepLink, firmante, lado)
            .thenApply { respuesta ->
                ResultadoEnvioFirma(
                    ok = respuesta.ok,
                    statusCode = respuesta.statusCode,
                    mensaje = respuesta.crudo.takeIf { it.isNotBlank() } ?: respuesta.mensaje
                )
            }
    }

    /** Envía bytes ya comprimidos con GZIP. Expuesto para el servicio de firma común. */
    fun enviarFirmaPostComprimido(cuerpoComprimido: ByteArray): CompletableFuture<ResultadoEnvioFirma> =
        servicioApi.enviarFirmaComprimida(cuerpoComprimido).thenApply { respuesta ->
            ResultadoEnvioFirma(
                ok = respuesta.ok,
                statusCode = respuesta.statusCode,
                mensaje = respuesta.crudo.takeIf { it.isNotBlank() } ?: respuesta.mensaje
            )
        }
}

data class ResultadoEnvioFirma(
    val ok: Boolean,
    val statusCode: Int?,
    val mensaje: String
)

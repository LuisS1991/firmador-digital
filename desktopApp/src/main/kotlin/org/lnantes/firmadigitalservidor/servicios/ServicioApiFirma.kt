package org.lnantes.firmadigitalservidor.servicios

import kotlin.jvm.JvmOverloads
import kotlinx.serialization.json.Json
import org.lnantes.firmadigitalservidor.PayloadFirma
import org.lnantes.firmadigitalservidor.compresion.PreparadorPostGzip
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Servicio de consumo de la API REST de firmas.
 *
 * Es la única puerta de entrada HTTP de la aplicación: el controller de firma,
 * el flujo AOA o un futuro REST/CLI pueden usarlo sin conocer `HttpClient`.
 * No depende de JavaFX y no firma nada: solo prepara/serializa y envía.
 */
class ServicioApiFirma @JvmOverloads constructor(
    private val configuracion: ConfiguracionApiFirma = ConfiguracionApiFirma(),
    private val cliente: ClienteRest = ClienteRest(
        ConfiguracionRest(
            baseUrl = configuracion.baseUrl,
            connectTimeout = configuracion.connectTimeout,
            requestTimeout = configuracion.requestTimeout,
            bearerToken = configuracion.bearerToken,
            cabecerasPorDefecto = configuracion.cabecerasPorDefecto
        )
    ),
    private val preparadorPost: PreparadorPostGzip = PreparadorPostGzip(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onLog: (String) -> Unit = {}
) {

    /** Body gzip listo para enviar, sin `firma_base64`. Útil para vista previa/SQLite. */
    fun prepararEnvio(
        codigo: String,
        payload: PayloadFirma,
        firmaGuardada: Int = 1,
        token: String? = null,
        firmante: String? = null,
        lado: String? = null,
    ): PreparadorPostGzip.Resultado =
        preparadorPost.preparar(codigo, payload, firmaGuardada, token, firmante, lado)

    /** Prepara y envía la firma. Devuelve la respuesta ya decodificada. */
    fun enviarFirma(
        codigo: String,
        payload: PayloadFirma,
        firmaGuardada: Int = 1,
        token: String? = null,
        firmante: String? = null,
        lado: String? = null,
    ): CompletableFuture<RespuestaApi<RespuestaFirmaApi>> {
        val preparado = prepararEnvio(codigo, payload, firmaGuardada, token, firmante, lado)
        registrarEnvio(preparado, firmaGuardada, token)
        return enviarFirmaComprimida(preparado.cuerpoComprimido).thenApply(::decodificar)
    }

    /** Envía bytes ya comprimidos; mantiene el contrato anterior del controller. */
    fun enviarFirmaComprimida(cuerpoComprimido: ByteArray): CompletableFuture<RespuestaApi<String>> =
        cliente.enviarCrudo(
            url = configuracion.urlFirma,
            metodo = "POST",
            cuerpo = cuerpoComprimido,
            cabeceras = cabecerasFirma()
        )

    /** Variante tipada para consumidores nuevos. */
    fun enviarFirmaComprimidaTipada(
        cuerpoComprimido: ByteArray
    ): CompletableFuture<RespuestaApi<RespuestaFirmaApi>> =
        enviarFirmaComprimida(cuerpoComprimido).thenApply(::decodificar)

    /**
     * Consulta opcional a la API. Si no se configuró [ConfiguracionApiFirma.rutaConsultaFirma],
     * devuelve un fallo local en vez de inventar un endpoint.
     */
    fun consultarFirma(codigo: String): CompletableFuture<RespuestaApi<RespuestaFirmaApi>> {
        val ruta = configuracion.rutaConsultaFirma ?: return CompletableFuture.completedFuture(
            RespuestaApi(
                ok = false,
                statusCode = null,
                cuerpo = null,
                crudo = "",
                mensaje = "No se configuró rutaConsultaFirma en ConfiguracionApiFirma."
            )
        )
        val url = cliente.url(ruta.replace("{codigo}", codigo))
        return cliente.enviarCrudo(url, "GET", null, emptyMap()).thenApply(::decodificar)
    }

    private fun cabecerasFirma(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Content-Encoding" to "gzip",
        "Accept" to "application/json"
    )

    private fun registrarEnvio(
        preparado: PreparadorPostGzip.Resultado,
        firmaGuardada: Int,
        token: String?
    ) {
        val conToken = token?.isNotBlank() == true
        onLog(
            "📤 API POST ${configuracion.urlFirma} · " +
                "firma_guardada=$firmaGuardada · token=${if (conToken) "sí" else "no"} · " +
                "${preparado.tamanoJson} bytes → gzip ${preparado.tamanoComprimido} bytes " +
                "(${preparado.porcentajeReduccion}% reducido)"
        )
    }

    private fun decodificar(respuesta: RespuestaApi<String>): RespuestaApi<RespuestaFirmaApi> {
        if (!respuesta.ok || respuesta.crudo.isBlank()) {
            return RespuestaApi(
                ok = respuesta.ok,
                statusCode = respuesta.statusCode,
                cuerpo = null,
                crudo = respuesta.crudo,
                mensaje = respuesta.mensaje
            )
        }
        return try {
            RespuestaApi(
                ok = true,
                statusCode = respuesta.statusCode,
                cuerpo = json.decodeFromString<RespuestaFirmaApi>(respuesta.crudo),
                crudo = respuesta.crudo,
                mensaje = respuesta.mensaje
            )
        } catch (error: Exception) {
            RespuestaApi(
                ok = false,
                statusCode = respuesta.statusCode,
                cuerpo = null,
                crudo = respuesta.crudo,
                mensaje = "No se pudo interpretar la respuesta: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    companion object {
        /** Compatibilidad con el constructor anterior basado en URL completa. */
        fun desdeEndpoint(
            endpointUrl: String,
            bearerToken: String? = null,
            httpClient: HttpClient? = null,
            preparadorPost: PreparadorPostGzip = PreparadorPostGzip(),
            onLog: (String) -> Unit = {}
        ): ServicioApiFirma {
            val config = ConfiguracionApiFirma.desdeEndpoint(endpointUrl, bearerToken)
            val cliente = ClienteRest(
                configuracion = ConfiguracionRest(
                    baseUrl = config.baseUrl,
                    connectTimeout = config.connectTimeout,
                    requestTimeout = config.requestTimeout,
                    bearerToken = config.bearerToken,
                    cabecerasPorDefecto = config.cabecerasPorDefecto
                ),
                httpClient = httpClient ?: HttpClient.newBuilder()
                    .connectTimeout(config.connectTimeout)
                    .build(),
                onLog = onLog
            )
            return ServicioApiFirma(
                configuracion = config,
                cliente = cliente,
                preparadorPost = preparadorPost,
                onLog = onLog
            )
        }
    }
}

package org.lnantes.firmadigitalservidor.servicios

import kotlin.jvm.JvmOverloads
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** Configuración común de la capa REST (timeouts, token y cabeceras fijas). */
data class ConfiguracionRest(
    val baseUrl: String = "http://localhost:8000",
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val requestTimeout: Duration = Duration.ofSeconds(15),
    val bearerToken: String? = null,
    val cabecerasPorDefecto: Map<String, String> = emptyMap()
)

/** Resultado uniforme de cualquier llamada REST, con o sin deserialización. */
data class RespuestaApi<out T>(
    val ok: Boolean,
    val statusCode: Int?,
    val cuerpo: T?,
    val crudo: String,
    val mensaje: String
)

/**
 * Cliente HTTP genérico para consumir APIs REST.
 *
 * Es asíncrono (`CompletableFuture`), centraliza cabeceras/token/timeouts y
 * convierte errores de red o de JSON en un [RespuestaApi] en vez de excepciones.
 * No depende de JavaFX ni de la lógica de firma.
 */
class ClienteRest @JvmOverloads constructor(
    private val configuracion: ConfiguracionRest = ConfiguracionRest(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(configuracion.connectTimeout)
        .build(),
    private val onLog: (String) -> Unit = {}
) {

    /** Resuelve una ruta relativa contra [ConfiguracionRest.baseUrl]. */
    fun url(ruta: String): String {
        val destino = ruta.ifBlank { "/" }
        if (destino.startsWith("http://") || destino.startsWith("https://")) return destino
        return configuracion.baseUrl.trimEnd('/') +
            if (destino.startsWith("/")) destino else "/$destino"
    }

    inline fun <reified T> get(
        ruta: String,
        cabeceras: Map<String, String> = emptyMap()
    ): CompletableFuture<RespuestaApi<T>> = get(url(ruta), serializer<T>(), cabeceras)

    fun <T> get(
        url: String,
        serializer: KSerializer<T>,
        cabeceras: Map<String, String> = emptyMap()
    ): CompletableFuture<RespuestaApi<T>> = enviar(url, "GET", null, cabeceras, serializer)

    inline fun <reified T> postJson(
        ruta: String,
        cuerpo: String,
        cabeceras: Map<String, String> = emptyMap()
    ): CompletableFuture<RespuestaApi<T>> =
        enviar(url(ruta), "POST", cuerpo.toByteArray(Charsets.UTF_8), cabeceras, serializer<T>())

    /** POST de bytes ya serializados (por ejemplo, JSON comprimido con GZIP). */
    fun postBytes(
        ruta: String,
        cuerpo: ByteArray,
        cabeceras: Map<String, String> = emptyMap()
    ): CompletableFuture<RespuestaApi<String>> =
        postBytesUrl(url(ruta), cuerpo, cabeceras)

    /** Variante con URL absoluta: útil cuando el endpoint completo ya viene configurado. */
    fun postBytesUrl(
        url: String,
        cuerpo: ByteArray,
        cabeceras: Map<String, String> = emptyMap()
    ): CompletableFuture<RespuestaApi<String>> =
        enviarCrudo(url, "POST", cuerpo, cabeceras)

    inline fun <reified T> enviarUrl(
        url: String,
        metodo: String,
        cuerpo: ByteArray? = null,
        cabeceras: Map<String, String> = emptyMap()
    ): CompletableFuture<RespuestaApi<T>> =
        enviar(url, metodo, cuerpo, cabeceras, serializer<T>())

    fun <T> enviar(
        url: String,
        metodo: String,
        cuerpo: ByteArray?,
        cabeceras: Map<String, String>,
        serializer: KSerializer<T>
    ): CompletableFuture<RespuestaApi<T>> =
        enviarCrudo(url, metodo, cuerpo, cabeceras).thenApply { respuesta ->
            decodificar(respuesta, serializer)
        }

    /** Llama al endpoint y devuelve el body tal cual, sin intentar parsearlo. */
    fun enviarCrudo(
        url: String,
        metodo: String,
        cuerpo: ByteArray?,
        cabeceras: Map<String, String>
    ): CompletableFuture<RespuestaApi<String>> {
        val cabecerasFinales = linkedMapOf("Accept" to "application/json").apply {
            putAll(configuracion.cabecerasPorDefecto)
            configuracion.bearerToken?.takeIf { it.isNotBlank() }?.let {
                put("Authorization", "Bearer $it")
            }
            // Las cabeceras explícitas de la llamada ganan a las de configuración.
            putAll(cabeceras)
        }

        val solicitud = HttpRequest.newBuilder(URI.create(url))
            .timeout(configuracion.requestTimeout)
            .apply { cabecerasFinales.forEach { (nombre, valor) -> setHeader(nombre, valor) } }
            .method(
                metodo,
                cuerpo?.let { HttpRequest.BodyPublishers.ofByteArray(it) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            .build()

        onLog("➡️ REST $metodo $url")
        return httpClient.sendAsync(solicitud, HttpResponse.BodyHandlers.ofString())
            .thenApply { respuesta ->
                val texto = respuesta.body()
                val correcto = respuesta.statusCode() in 200..299
                onLog("⬅️ REST ${respuesta.statusCode()} $url (${texto.length} bytes)")
                RespuestaApi(
                    ok = correcto,
                    statusCode = respuesta.statusCode(),
                    cuerpo = texto,
                    crudo = texto,
                    mensaje = if (correcto) "OK" else "HTTP ${respuesta.statusCode()}"
                )
            }
            .exceptionally { error ->
                val detalle = "${error.javaClass.simpleName}: ${error.message ?: "sin mensaje"}"
                onLog("❌ REST $metodo $url → $detalle")
                RespuestaApi(
                    ok = false,
                    statusCode = null,
                    cuerpo = null,
                    crudo = "",
                    mensaje = "Error de red: $detalle"
                )
            }
    }

    private fun <T> decodificar(
        respuesta: RespuestaApi<String>,
        serializer: KSerializer<T>
    ): RespuestaApi<T> {
        if (!respuesta.ok || respuesta.crudo.isBlank()) {
            return RespuestaApi(
                respuesta.ok,
                respuesta.statusCode,
                null,
                respuesta.crudo,
                respuesta.mensaje
            )
        }
        return try {
            RespuestaApi(
                ok = true,
                statusCode = respuesta.statusCode,
                cuerpo = json.decodeFromString(serializer, respuesta.crudo),
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
}

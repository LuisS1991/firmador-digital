package org.lnantes.firmadigitalservidor.servicios

import kotlin.jvm.JvmOverloads
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Duration

/** Rutas conocidas de la API Laravel. */
object RutasApi {
    const val BASE_LOCAL = "http://localhost:8000"
    const val FIRMAR = "/devoluciones/recibos/firmar"
}

/**
 * Respuesta tolerante del endpoint de firma.
 *
 * Se aceptan `ok`/`success` y `message`/`mensaje` porque Laravel y un cliente
 * PHP pueden devolver cualquiera de las dos convenciones. Los campos que no
 * aparezcan en el JSON se ignoran (`ignoreUnknownKeys` en el cliente).
 */
@Serializable
data class RespuestaFirmaApi(
    val ok: Boolean? = null,
    val success: Boolean? = null,
    val message: String? = null,
    val mensaje: String? = null,
    val codigo: String? = null,
    @SerialName("id_solicitud") val idSolicitud: String? = null
) {
    val correcto: Boolean
        get() = ok ?: success ?: false

    val texto: String
        get() = message ?: mensaje.orEmpty()
}

/**
 * Configuración de la capa REST de firma.
 *
 * [rutaConsultaFirma] es opcional: cuando la API agregue un GET de consulta se
 * indica acá (acepta el marcador `{codigo}`) y no hace falta tocar el servicio.
 */
data class ConfiguracionApiFirma @JvmOverloads constructor(
    val baseUrl: String = RutasApi.BASE_LOCAL,
    val rutaFirma: String = RutasApi.FIRMAR,
    val rutaConsultaFirma: String? = null,
    val bearerToken: String? = null,
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val requestTimeout: Duration = Duration.ofSeconds(15),
    val cabecerasPorDefecto: Map<String, String> = emptyMap()
) {
    /** URL absoluta del endpoint de envío. */
    val urlFirma: String
        get() = unir(baseUrl, rutaFirma)

    companion object {
        /** Construye la configuración a partir de una URL completa (estilo anterior). */
        fun desdeEndpoint(
            endpointUrl: String,
            bearerToken: String? = null,
            connectTimeout: Duration = Duration.ofSeconds(10),
            requestTimeout: Duration = Duration.ofSeconds(15),
            cabecerasPorDefecto: Map<String, String> = emptyMap()
        ): ConfiguracionApiFirma = runCatching {
            val uri = URI.create(endpointUrl)
            val base = "${uri.scheme}://${uri.authority}"
            val ruta = uri.rawPath.ifBlank { "/" } +
                (uri.rawQuery?.let { "?$it" } ?: "")
            ConfiguracionApiFirma(
                baseUrl = base,
                rutaFirma = ruta,
                bearerToken = bearerToken,
                connectTimeout = connectTimeout,
                requestTimeout = requestTimeout,
                cabecerasPorDefecto = cabecerasPorDefecto
            )
        }.getOrElse {
            ConfiguracionApiFirma(
                baseUrl = endpointUrl.trimEnd('/'),
                rutaFirma = "",
                bearerToken = bearerToken,
                connectTimeout = connectTimeout,
                requestTimeout = requestTimeout,
                cabecerasPorDefecto = cabecerasPorDefecto
            )
        }

        private fun unir(base: String, ruta: String): String {
            val destino = ruta.ifBlank { "/" }
            if (destino.startsWith("http://") || destino.startsWith("https://")) return destino
            return base.trimEnd('/') + if (destino.startsWith("/")) destino else "/$destino"
        }
    }
}

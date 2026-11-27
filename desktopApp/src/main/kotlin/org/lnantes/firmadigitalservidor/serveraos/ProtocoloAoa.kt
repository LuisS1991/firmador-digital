package org.lnantes.firmadigitalservidor.serveraos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.lnantes.firmadigitalservidor.ConsentimientoFirma

/**
 * Protocolo de mensajes sobre el canal AOA (bulk IN/OUT).
 *
 * Todos los mensajes viajan con el mismo framing de 4 bytes de longitud que ya
 * usaba la captura de trazos, así que el móvil y el servidor pueden compartir
 * un único lector de tramas para cualquier evento.
 *
 * Eventos:
 *  - `modo_firma`: PC → móvil. Ordena «comun» (por defecto) o «avanzada».
 *  - `solicitar_pin`: PC → móvil. Pide al usuario el PIN del token/cédula.
 *  - `pin_respuesta`: móvil → PC. Devuelve el PIN (o la cancelación) y el
 *    consentimiento otorgado en el celular.
 *  - trama con `strokes`: móvil → PC. Firmas vectoriales (flujo actual) con el
 *    consentimiento adjunto.
 */
object ProtocoloAoa {
    const val EVENTO_MODO_FIRMA = "modo_firma"
    const val EVENTO_SOLICITAR_PIN = "solicitar_pin"
    const val EVENTO_PIN_RESPUESTA = "pin_respuesta"
    const val EVENTO_TRAZOS = "trazos_recibidos"
    const val EVENTO_TEXTO = "texto_recibido"

    const val MODO_COMUN = "comun"
    const val MODO_AVANZADA = "avanzada"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/** Envelope genérico; `body` queda como JsonObject para no acoplar eventos nuevos. */
@Serializable
data class MensajeAoa(
    val evento: String = "",
    val body: JsonObject? = null
)

@Serializable
data class BodySolicitudPin(
    val motivo: String,
    val id: String
)

@Serializable
data class BodyModoFirma(
    val modo: String
)

@Serializable
data class BodyRespuestaPin(
    val id: String? = null,
    val pin: String? = null,
    val cancelado: Boolean = false,
    val consentimiento: ConsentimientoFirma? = null
)

/** Trama ya interpretada, lista para ser despachada por [ControladorAoa]. */
sealed interface TramaAoa {
    /** Payload original de trazos (se conserva tal cual para el JSON/SQLite histórico). */
    data class Trazos(val crudo: ByteArray) : TramaAoa
    data class Pin(val respuesta: BodyRespuestaPin) : TramaAoa
    data class Texto(val contenido: String) : TramaAoa
    data class Comando(val mensaje: MensajeAoa) : TramaAoa
}

fun crearSolicitudPin(motivo: String, id: String): MensajeAoa = MensajeAoa(
    evento = ProtocoloAoa.EVENTO_SOLICITAR_PIN,
    body = ProtocoloAoa.json.encodeToJsonElement(
        BodySolicitudPin.serializer(),
        BodySolicitudPin(motivo, id)
    ) as JsonObject
)

/** Orden enviada al móvil al sincronizar o al elegir una firma avanzada. */
fun crearModoFirma(modo: String): MensajeAoa = MensajeAoa(
    evento = ProtocoloAoa.EVENTO_MODO_FIRMA,
    body = ProtocoloAoa.json.encodeToJsonElement(
        BodyModoFirma.serializer(),
        BodyModoFirma(modo)
    ) as JsonObject
)

fun crearRespuestaPin(id: String, pin: String? = null, cancelado: Boolean = false): MensajeAoa =
    MensajeAoa(
        evento = ProtocoloAoa.EVENTO_PIN_RESPUESTA,
        body = ProtocoloAoa.json.encodeToJsonElement(
            BodyRespuestaPin.serializer(),
            BodyRespuestaPin(id = id, pin = pin, cancelado = cancelado)
        ) as JsonObject
    )

/** Interpreta una trama. Los trazos del móvil siguen siendo JSON "crudo" sin envelope. */
fun interpretarTrama(crudo: ByteArray): TramaAoa {
    val texto = String(crudo, Charsets.UTF_8).trimEnd('\u0000')
    val elemento = runCatching { Json.parseToJsonElement(texto) }.getOrNull()

    if (elemento is JsonObject) {
        val evento = runCatching { elemento["evento"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        when (evento) {
            ProtocoloAoa.EVENTO_PIN_RESPUESTA -> {
                val body = elemento["body"] as? JsonObject
                val respuesta = body?.let {
                    runCatching {
                        ProtocoloAoa.json.decodeFromJsonElement(BodyRespuestaPin.serializer(), it)
                    }.getOrNull()
                }
                if (respuesta != null) return TramaAoa.Pin(respuesta)
            }
            ProtocoloAoa.EVENTO_SOLICITAR_PIN,
            ProtocoloAoa.EVENTO_MODO_FIRMA -> {
                val mensaje = runCatching {
                    ProtocoloAoa.json.decodeFromJsonElement(MensajeAoa.serializer(), elemento)
                }.getOrNull()
                if (mensaje != null) return TramaAoa.Comando(mensaje)
            }
        }

        // Formato histórico del móvil: {"tipo":"TRAZO_FIRMA_VECTORIAL",...,"strokes":[...]}
        if (elemento.containsKey("strokes")) {
            return TramaAoa.Trazos(crudo)
        }
    }

    return TramaAoa.Texto(texto)
}

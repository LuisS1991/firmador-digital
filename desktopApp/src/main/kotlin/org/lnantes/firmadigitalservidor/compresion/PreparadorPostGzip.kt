package org.lnantes.firmadigitalservidor.compresion

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.lnantes.firmadigitalservidor.PayloadFirma
import org.lnantes.firmadigitalservidor.RequestFirmaRecibo
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

/**
 * Prepara el cuerpo del POST a producción usando GZIP nativo del JDK.
 *
 * Antes de comprimir quita `payload.firma_base64`: la imagen ya queda en el
 * SQLite local y no necesita viajar al servidor. El body resultante sigue siendo
 * JSON; solo se envía comprimido con `Content-Encoding: gzip`. Si se recibe el
 * token del deep link se agrega como parámetro de primer nivel `token`, que es
 * obligatorio para Laravel.
 *
 * Esta clase no hace HTTP ni toca la UI: solo transforma el body.
 */
class PreparadorPostGzip(
    private val json: Json = Json {
        prettyPrint = false
        encodeDefaults = true
    }
) {
    data class Resultado(
        /** JSON legible que efectivamente se comprimió (sin `firma_base64`). */
        val jsonSinBase64: String,
        /** Bytes listos para `HttpRequest.BodyPublishers.ofByteArray`. */
        val cuerpoComprimido: ByteArray,
        val tamanoJson: Int,
        val tamanoComprimido: Int
    ) {
        val porcentajeReduccion: Int
            get() = if (tamanoJson == 0) {
                0
            } else {
                ((1.0 - tamanoComprimido.toDouble() / tamanoJson.toDouble()) * 100).toInt()
            }
    }

    /** Construye el body sin la imagen y lo comprime con GZIP. */
    fun preparar(
        codigo: String,
        payload: PayloadFirma,
        firmaGuardada: Int,
        token: String? = null,
        firmante: String? = null,
        lado: String? = null,
    ): Resultado {
        val tokenLimpio = token?.trim()?.takeIf { it.isNotBlank() }
        val request = RequestFirmaRecibo(
            codigo = codigo,
            payload = payload,
            firmaGuardada = firmaGuardada,
            token = tokenLimpio,
            firmante = firmante?.trim()?.takeIf { it.isNotBlank() },
            lado = lado?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        )

        // Serializamos el DTO completo y luego quitamos solo la clave de la
        // imagen. Así conservamos timestamp, tamaño, trazos y consentimiento.
        val elemento = json.encodeToJsonElement(
            RequestFirmaRecibo.serializer(),
            request
        ) as JsonObject
        val payloadOriginal = elemento["payload"] as? JsonObject ?: JsonObject(emptyMap())
        val payloadLimpio = JsonObject(payloadOriginal.toMap().filterKeys { it != CLAVE_BASE64 })

        val elementoLimpio = buildJsonObject {
            elemento.forEach { (clave, valor) ->
                // No mandar `token: null`: Laravel lo exige como string.
                if (valor is JsonNull) return@forEach
                put(clave, if (clave == "payload") payloadLimpio else valor)
            }
        }

        val jsonLimpio = json.encodeToString(JsonObject.serializer(), elementoLimpio)
        val bytesJson = jsonLimpio.toByteArray(StandardCharsets.UTF_8)
        val comprimido = comprimir(bytesJson)

        return Resultado(
            jsonSinBase64 = jsonLimpio,
            cuerpoComprimido = comprimido,
            tamanoJson = bytesJson.size,
            tamanoComprimido = comprimido.size
        )
    }

    /** Compresión GZIP pura, reutilizable si después se quiere comprimir otro body. */
    fun comprimir(datos: ByteArray): ByteArray = ByteArrayOutputStream().use { salida ->
        GZIPOutputStream(salida).use { gzip ->
            gzip.write(datos)
        }
        salida.toByteArray()
    }

    private companion object {
        const val CLAVE_BASE64 = "firma_base64"
    }
}

package org.lnantes.firmadigitalservidor.serveraos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.lnantes.firmadigitalservidor.ConsentimientoFirma
import org.lnantes.firmadigitalservidor.FirmaSQLiteController
import org.lnantes.firmadigitalservidor.PayloadFirma
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/**
 * Captura de firmas vectoriales (trazos). Contiene exactamente el comportamiento
 * que antes tenía `AoaServerApp.guardarFirmaEnJson()`:
 *
 *  1. escribe `firma_recogida.json` con timestamp, tamaño, contenido y base64;
 *  2. si hay código de recibo, guarda/actualiza el registro en SQLite;
 *  3. expone el último payload para el flujo de envío HTTP.
 *
 * No conoce USB ni JavaFX: recibe la trama ya reensamblada.
 */
class CapturadorTrazos(
    private val proveedorCodigo: () -> String?,
    private val baseDatos: FirmaSQLiteController,
    private val onLog: (String) -> Unit = {},
    val archivoFirma: File = File(System.getProperty("user.dir"), "firma_recogida.json")
) {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile
    var ultimoPayload: PayloadFirma? = null
        private set

    /**
     * Descarta el payload ya estampado para que la siguiente captura ESPERE una
     * firma nueva del móvil (necesario cuando firman dos personas seguidas).
     * El archivo/SQLite se conservan como respaldo.
     */
    fun descartarUltimo() {
        ultimoPayload = null
    }

    /** Procesa una trama de trazos cruda (bytes exactos recibidos del móvil). */
    fun procesar(crudo: ByteArray) {
        try {
            val textoOriginal = String(crudo, StandardCharsets.UTF_8).trimEnd('\u0000')
            val base64 = Base64.getEncoder().encodeToString(crudo)
            val contenido = if (pareceJson(textoOriginal)) {
                textoOriginal
            } else {
                "\"${escaparJson(textoOriginal)}\""
            }
            val jsonContent = """
                {
                    "timestamp": "${Instant.now()}",
                    "tamano_bytes": ${crudo.size},
                    "contenido_texto": $contenido,
                    "firma_base64": "$base64"
                }
            """.trimIndent()

            archivoFirma.writeText(jsonContent, StandardCharsets.UTF_8)
            onLog("💾 Guardado '${archivoFirma.absolutePath}' (${crudo.size} bytes).")
            guardarEnBaseDatos(jsonContent)
            guardarConsentimiento(textoOriginal)
        } catch (e: Exception) {
            onLog("❌ Error al guardar el archivo JSON: ${e.localizedMessage}")
        }
    }

    /** Lee el último JSON capturado; devuelve null si no existe o no es válido. */
    fun leerPayload(): PayloadFirma? {
        if (!archivoFirma.isFile) return null
        return try {
            val payload = json.decodeFromString<PayloadFirma>(
                archivoFirma.readText(StandardCharsets.UTF_8)
            )
            ultimoPayload = payload
            payload
        } catch (e: Exception) {
            onLog("⚠️ No se pudo interpretar '${archivoFirma.name}': ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    /** Resumen legible para el log. */
    fun resumen(payload: PayloadFirma): String {
        val trazos = payload.contenidoTexto.strokes.size
        val puntos = payload.contenidoTexto.strokes.sumOf { it.size }
        return "$trazos trazo(s), $puntos punto(s)"
    }

    private fun guardarEnBaseDatos(jsonContent: String) {
        val codigo = proveedorCodigo()
        if (codigo.isNullOrBlank()) {
            onLog("⚠️ No se guardó en SQLite: no hay código de recibo del enlace profundo.")
            return
        }
        try {
            val payload = json.decodeFromString<PayloadFirma>(jsonContent)
            ultimoPayload = payload
            baseDatos.guardar(codigo, payload)
            onLog("🗄️ Guardado en SQLite '${baseDatos.nombreArchivo}' con código primario '$codigo'.")
        } catch (e: Exception) {
            onLog("⚠️ No se pudo guardar en SQLite: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Extrae el consentimiento adjunto por el móvil al payload de trazos. */
    private fun guardarConsentimiento(textoOriginal: String) {
        val consentimiento = extraerConsentimiento(textoOriginal)
        if (consentimiento == null) {
            onLog("⚠️ La trama de trazos no trae consentimiento adjunto.")
            return
        }
        val codigo = proveedorCodigo()
        try {
            baseDatos.guardarConsentimiento(codigo, consentimiento, "comun")
            onLog(
                "🗄️ Consentimiento guardado en SQLite: tipo=${consentimiento.tipo}, " +
                    "otorgado=${consentimiento.otorgado}, normativa=${consentimiento.normativaId}."
            )
        } catch (e: Exception) {
            onLog("⚠️ No se pudo guardar el consentimiento: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun extraerConsentimiento(texto: String): ConsentimientoFirma? = try {
        val elemento = json.parseToJsonElement(texto)
        val objeto = elemento as? JsonObject
        val nodo = objeto?.get("consentimiento")
        if (nodo != null) json.decodeFromJsonElement(ConsentimientoFirma.serializer(), nodo) else null
    } catch (e: Exception) {
        onLog("⚠️ Consentimiento adjunto inválido: ${e.message ?: e.javaClass.simpleName}")
        null
    }

    private fun pareceJson(texto: String): Boolean {
        val limpio = texto.trim()
        return limpio.startsWith("{") && limpio.endsWith("}")
    }

    private fun escaparJson(texto: String): String = texto
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

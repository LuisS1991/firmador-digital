package com.lnantes.uifirmaelectronica.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/** Datos del recibo devueltos por Laravel, sin acoplar la UI al JSON. */
data class ReciboRemoto(
    val codigo: String,
    val data: JsonObject,
    val ruta: String,
    val crudo: String
)

private class ReciboHttpException(
    val estado: Int,
    mensaje: String
) : IllegalStateException(mensaje)

/**
 * Puerta de entrada HTTP para obtener la información del recibo antes de
 * generar/firmar el PDF. No envía firmas: eso lo hace la capa REST existente.
 *
 * La ruta principal es la nueva de Laravel:
 * `/devolucion/generar/desktop/recibo/{codigo}`. Se conserva como alternativa la
 * ruta anterior (`/devolucion/generar/recibo/{codigo}`) solo para entornos
 * todavía no actualizados; si la principal responde 404 se avisa por log.
 */
class ApiGateway(
    private val config: BackendConfig,
    private val onLog: (String) -> Unit = {}
) {
    suspend fun obtenerRecibo(codigo: String): ReciboRemoto = withContext(Dispatchers.IO) {
        val rutas = listOf(config.rutaRecibo, config.rutaReciboAlternativa)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        require(rutas.isNotEmpty()) { "No hay rutas configuradas para obtener el recibo" }

        var ultimoError: ReciboHttpException? = null
        rutas.forEachIndexed { indice, ruta ->
            try {
                return@withContext obtenerDe(ruta, codigo)
            } catch (e: ReciboHttpException) {
                val esUltima = indice == rutas.lastIndex
                if (e.estado != 404 || esUltima) throw e
                ultimoError = e
                onLog("⚠️ Ruta ${ruta.replace("{codigo}", codigo)} no existe (404); probando alternativa…")
            }
        }

        throw ultimoError ?: error("No se pudo obtener el recibo $codigo")
    }

    private fun obtenerDe(ruta: String, codigo: String): ReciboRemoto {
        val url = config.apiBaseUrl + ruta.replace("{codigo}", codigo)
        onLog("➡️ GET $url")

        val conexion = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }

        val (estado, texto) = try {
            val estadoHttp = conexion.responseCode
            val fuente = if (estadoHttp in 200..299) conexion.inputStream else conexion.errorStream
            val cuerpo = fuente?.use { it.readBytes() }?.toString(StandardCharsets.UTF_8).orEmpty()
            estadoHttp to cuerpo
        } catch (e: Exception) {
            throw ReciboHttpException(
                estado = 0,
                mensaje = "No se pudo contactar ${config.apiBaseUrl}: ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            conexion.disconnect()
        }

        if (estado !in 200..299) {
            throw ReciboHttpException(
                estado = estado,
                mensaje = "No se pudo obtener el recibo (HTTP $estado): " +
                    texto.take(200).ifBlank { "sin cuerpo de error" }
            )
        }

        val raiz = backendJson.parseToJsonElement(texto).objeto()
            ?: error("La respuesta del recibo no es un objeto JSON")
        val estadoApi = raiz.texto("status", "estado")
        if (estadoApi.isNotBlank() && estadoApi.lowercase() !in setOf("ok", "success", "true")) {
            error("La API devolvió status=$estadoApi")
        }
        val data = raiz["data"]?.objeto()
            ?: raiz["recibo"]?.objeto()
            ?: raiz
        onLog("⬅️ Recibo obtenido para '$codigo' desde $url (${texto.toByteArray(StandardCharsets.UTF_8).size} bytes)")
        return ReciboRemoto(codigo = codigo, data = data, ruta = url, crudo = texto)
    }
}

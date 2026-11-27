package org.lnantes.firmadigitalservidor.serveraos

import kotlinx.serialization.json.Json
import org.lnantes.firmadigitalservidor.FirmaReciboController
import org.lnantes.firmadigitalservidor.FirmaSQLiteController
import org.lnantes.firmadigitalservidor.RequestFirmaRecibo
import org.lnantes.firmadigitalservidor.ResultadoEnvioFirma
import org.lnantes.firmadigitalservidor.servicios.RespuestaFirmaApi

/**
 * Flujo de firma común (trazos del móvil → SQLite → POST). Es la lógica que
 * antes estaba dentro del botón «Firmar y enviar» de la UI; ahora es reutilizable
 * y no depende de JavaFX. Las etapas y el resultado se informan por callbacks.
 */
class ServicioFirmaComun(
    private val controladorFirma: FirmaReciboController,
    private val capturador: CapturadorTrazos,
    private val baseDatos: FirmaSQLiteController,
    private val endpointDescripcion: String = "http://localhost:8000/devoluciones/recibos/firmar",
    private val onLog: (String) -> Unit = {}
) {
    enum class Etapa { PREPARANDO, GUARDANDO, ENVIANDO }

    data class Resultado(
        val ok: Boolean,
        val estadoTexto: String,
        val mensaje: String,
        val detalle: String,
        val codigo: String?,
        val body: String?
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Prepara el body y lanza el POST asíncrono. Los callbacks se invocan en el hilo llamante. */
    fun ejecutar(
        codigoUi: String?,
        onEtapa: (Etapa) -> Unit,
        onTerminado: (Resultado) -> Unit
    ) {
        onEtapa(Etapa.PREPARANDO)
        onLog("🖱️ Ejecutando firma común (trazos).")

        if (!codigoUi.isNullOrBlank()) {
            controladorFirma.setCodigo(codigoUi)
            onLog("⌨️ Código actualizado: ${controladorFirma.getCodigo()}")
        }

        val payload = capturador.leerPayload()
        if (payload != null) {
            controladorFirma.setPayload(payload)
            onLog(
                "📝 Firma recibida cargada desde '${capturador.archivoFirma.name}' " +
                    "(${capturador.resumen(payload)})."
            )
        } else {
            controladorFirma.generarFirmaTest()
            onLog("⚠️ Aún no hay firma recibida válida. Se usa un payload de prueba.")
        }

        val bodyInicial = controladorFirma.getBodyJson()
        if (bodyInicial == null) {
            onTerminado(
                Resultado(
                    ok = false,
                    estadoTexto = "Falta código o payload",
                    mensaje = "No se pudo construir el body: faltan código o payload.",
                    detalle = "",
                    codigo = controladorFirma.getCodigo(),
                    body = ""
                )
            )
            return
        }

        onEtapa(Etapa.GUARDANDO)
        val codigo = controladorFirma.getCodigo()
        var guardadoOk = false
        try {
            if (!codigo.isNullOrBlank()) {
                val solicitud = json.decodeFromString<RequestFirmaRecibo>(bodyInicial)
                baseDatos.guardar(solicitud.codigo, solicitud.payload)
                guardadoOk = true
                onLog("🗄️ Registro SQLite actualizado para '$codigo' antes del envío.")
            }
        } catch (e: Exception) {
            onLog("⚠️ No se pudo actualizar SQLite antes del envío: ${e.message ?: e.javaClass.simpleName}")
        }

        controladorFirma.setFirmaGuardada(if (guardadoOk) 1 else 0)

        // A producción NO va la imagen base64: el SQLite local ya la conserva.
        // El body se comprime con GZIP nativo antes del POST.
        val preparado = controladorFirma.prepararEnvio()
        if (preparado == null) {
            onTerminado(
                Resultado(
                    ok = false,
                    estadoTexto = "No se pudo preparar el envío",
                    mensaje = "No se pudo construir el body gzip para el POST.",
                    detalle = "",
                    codigo = codigo,
                    body = ""
                )
            )
            return
        }

        onLog("📤 POST $endpointDescripcion · firma_guardada=${controladorFirma.getFirmaGuardada()} · Content-Encoding: gzip")
        onLog(
            "📦 Body sin firma_base64: ${preparado.tamanoJson} bytes → " +
                "gzip ${preparado.tamanoComprimido} bytes (${preparado.porcentajeReduccion}% reducido)."
        )

        onEtapa(Etapa.ENVIANDO)
        controladorFirma.enviarFirmaPostComprimido(preparado.cuerpoComprimido).whenComplete { resultado, error ->
            // La UI muestra el JSON limpio; por wire viajaron los bytes gzip.
            onTerminado(
                resolverResultado(
                    codigo = codigo,
                    body = preparado.jsonSinBase64,
                    resultado = resultado,
                    error = error,
                    token = controladorFirma.getToken()
                )
            )
        }
    }

    private fun resolverResultado(
        codigo: String?,
        body: String,
        resultado: ResultadoEnvioFirma?,
        error: Throwable?,
        token: String? = null
    ): Resultado {
        val mensajeBase = error?.message?.let { "Error de red al enviar firma: $it" }
            ?: resultado?.mensaje
            ?: "No se obtuvo respuesta del servidor."
        val respuesta = try {
            json.decodeFromString<RespuestaFirmaApi>(mensajeBase)
        } catch (_: Exception) {
            null
        }
        val mensajeServidor = respuesta?.texto.takeIf { !it.isNullOrBlank() } ?: mensajeBase
        val codigoHttp = resultado?.statusCode
        val envioOk = error == null && (resultado?.ok ?: false)
        val detalle = "HTTP $codigoHttp · ok=${respuesta?.correcto ?: resultado?.ok ?: false}" +
            (respuesta?.codigo?.let { " · código=$it" } ?: "")

        if (!codigo.isNullOrBlank()) {
            try {
                baseDatos.marcarEnvio(codigo, envioOk, mensajeServidor)
                onLog("🗄️ Estado de envío guardado en SQLite para '$codigo' (ok=$envioOk).")
            } catch (e: Exception) {
                onLog("⚠️ No se pudo actualizar el estado en SQLite: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        return Resultado(
            ok = envioOk,
            estadoTexto = if (envioOk) {
                "Firma registrada (HTTP $codigoHttp)"
            } else {
                "No se pudo enviar (HTTP ${codigoHttp ?: "—"})"
            },
            mensaje = mensajeServidor,
            detalle = detalle,
            codigo = codigo,
            body = token?.takeIf { it.isNotBlank() }
                ?.let { body.replace("\"token\":\"$it\"", "\"token\":\"***\"") }
                ?: body
        )
    }
}

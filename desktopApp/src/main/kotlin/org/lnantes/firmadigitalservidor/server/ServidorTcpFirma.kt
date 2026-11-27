package org.lnantes.firmadigitalservidor.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class SolicitudFirmaTcp(
    val evento: String = "",
    val body: BodySolicitudFirma? = null
)

@Serializable
data class BodySolicitudFirma(
    val codigo: String? = null
)

@Serializable
data class RespuestaFirmaTcp(
    val evento: String,
    val body: BodyRespuestaFirma
)

@Serializable
data class BodyRespuestaFirma(
    val ok: Boolean,
    val codigo: String? = null,
    @SerialName("firma_base64") val firmaBase64: String? = null,
    val mensaje: String? = null
)

class ServidorTcpFirma(
    private val puerto: Int = PUERTO,
    private val buscarFirmaBase64: (String) -> String?,
    private val onLog: (String) -> Unit = {}
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val activo = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val clientes: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "tcp-firma-cliente").apply { isDaemon = true }
    }

    val estaActivo: Boolean get() = activo.get()

    @Synchronized
    fun iniciar() {
        if (activo.get()) return

        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("0.0.0.0", puerto))
        serverSocket = socket
        activo.set(true)

        acceptThread = Thread(::aceptarConexiones, "tcp-firma-accept").apply {
            isDaemon = true
            start()
        }
        onLog("🌐 Servidor TCP escuchando en 0.0.0.0:$puerto")
    }

    @Synchronized
    fun detener() {
        if (!activo.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        acceptThread?.interrupt()
        clientes.shutdownNow()
        onLog("🌐 Servidor TCP detenido.")
    }

    private fun aceptarConexiones() {
        while (activo.get()) {
            try {
                val cliente = serverSocket?.accept() ?: break
                clientes.execute { atenderCliente(cliente) }
            } catch (e: SocketException) {
                if (activo.get()) onLog("⚠️ Error al aceptar conexión TCP: ${e.message}")
            } catch (e: Exception) {
                if (activo.get()) onLog("⚠️ Error TCP: ${e.message}")
            }
        }
    }

    private fun atenderCliente(cliente: Socket) {
        try {
            cliente.use { socket ->
                socket.soTimeout = 30_000
                socket.tcpNoDelay = true

                val entrada = BufferedReader(
                    InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                )
                val salida = PrintWriter(
                    OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                )

                while (activo.get()) {
                    val linea = entrada.readLine() ?: break
                    onLog("🔌 TCP recibido desde ${socket.remoteSocketAddress}: $linea")
                    val respuesta = procesar(linea)
                    salida.print(respuesta)
                    salida.print("\r\n")
                    salida.flush()
                    onLog("📤 TCP enviado (${respuesta.length} bytes): $respuesta")
                    break
                }
            }
        } catch (e: SocketTimeoutException) {
            onLog("⏱️ Cliente TCP sin actividad: ${cliente.remoteSocketAddress}")
        } catch (e: Exception) {
            onLog("⚠️ Error atendiendo cliente TCP: ${e.message}")
        }
    }

    private fun procesar(linea: String): String {
        val texto = linea.trim()
        if (texto.isEmpty()) {
            return responderError(null, "Petición vacía")
        }

        return if (texto.startsWith("{")) {
            procesarJson(texto)
        } else {
            val codigo = extraerCodigo(texto)
            val firma = codigo?.let(::buscarSeguro)
            firma?.takeIf { it.isNotBlank() }
                ?: "ERROR: No se encontró firma para el código ${codigo ?: texto}"
        }
    }

    private fun procesarJson(texto: String): String {
        val solicitud = try {
            json.decodeFromString<SolicitudFirmaTcp>(texto)
        } catch (e: Exception) {
            return responderError(null, "JSON inválido: ${e.message}")
        }

        if (solicitud.evento != "obtener_firma") {
            return responderError(null, "Evento desconocido: ${solicitud.evento}")
        }

        val codigoCrudo = solicitud.body?.codigo
        if (codigoCrudo.isNullOrBlank()) {
            return responderError(null, "Falta el código")
        }

        val codigo = extraerCodigo(codigoCrudo)
            ?: return responderError(null, "Formato de código inválido: $codigoCrudo")

        val firma = buscarSeguro(codigo)
        if (firma.isNullOrBlank()) {
            return responderError(codigo, "No se encontró firma para el código $codigo")
        }

        onLog("📤 TCP → firma_base64 para '$codigo' (${firma.length} caracteres).")
        return json.encodeToString(
            RespuestaFirmaTcp(
                evento = "firma_respuesta",
                body = BodyRespuestaFirma(ok = true, codigo = codigo, firmaBase64 = firma)
            )
        )
    }

    private fun responderError(codigo: String?, mensaje: String): String {
        onLog("⚠️ TCP error para '$codigo': $mensaje")
        return json.encodeToString(
            RespuestaFirmaTcp(
                evento = "error",
                body = BodyRespuestaFirma(ok = false, codigo = codigo, mensaje = mensaje)
            )
        )
    }

    private fun buscarSeguro(codigo: String): String? = try {
        buscarFirmaBase64(codigo)
    } catch (e: Exception) {
        onLog("⚠️ Error buscando firma en SQLite: ${e.message}")
        null
    }

    private fun extraerCodigo(raw: String): String? {
        val texto = raw.trim().uppercase()
        val coincidencia = Regex("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}").find(texto)
        return coincidencia?.value ?: texto.takeIf { it.matches(Regex("[A-Z0-9-]{1,100}")) }
    }

    companion object {
        const val PUERTO = 5500
    }
}

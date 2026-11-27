package org.lnantes.firmadigitalservidor.serveraos

import org.lnantes.firmadigitalservidor.ConsentimientoFirma
import org.usb4java.DeviceDescriptor
import org.usb4java.DeviceHandle
import org.usb4java.DeviceList
import org.usb4java.LibUsb
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.experimental.or

/**
 * Toda la comunicación USB/AOA (sincronización, envío de comandos y recepción
 * de tramas). No conoce JavaFX ni ningún control de la UI.
 *
 * Antes este código vivía dentro de `AoaServerApp` y solo leía del móvil.
 * Ahora additionally escribe por el endpoint OUT para poder pedir el PIN.
 */
class ConexionAoa(
    private val onLog: (String) -> Unit = {},
    private val onEstado: (EstadoConexion) -> Unit = {}
) {
    enum class EstadoConexion { CONECTANDO, CONECTADO, DESCONECTADO }

    data class ResultadoPin(
        val ok: Boolean,
        val pin: CharArray?,
        val mensaje: String,
        val consentimiento: ConsentimientoFirma? = null
    )

    private data class PinPendiente(
        val id: String,
        val future: CompletableFuture<BodyRespuestaPin>
    )

    private val activo = AtomicBoolean(false)
    private val parar = AtomicBoolean(false)
    private val handleRef = AtomicReference<DeviceHandle?>(null)
    private val pinPendiente = AtomicReference<PinPendiente?>(null)
    private val escrituraLock = Any()
    private var hilo: Thread? = null
    private var receptor: (TramaAoa) -> Unit = {}

    @Volatile
    var conectada: Boolean = false
        private set

    /** Arranca el hilo de conexión; devuelve el control de inmediato. */
    @Synchronized
    fun iniciar() {
        if (activo.get() && !parar.get()) {
            onLog("⚠️ El servidor USB ya está en ejecución.")
            return
        }
        // Tras una desconexión, el hilo anterior todavía debe liberar libusb. Esperamos a
        // que termine antes de volver a inicializarlo para no solapar init/exit.
        hilo?.takeIf { it.isAlive }?.let { anterior ->
            runCatching { anterior.join(5_000) }
            if (anterior.isAlive) {
                onLog("⚠️ El enlace AOA anterior todavía se está cerrando; reintente en unos segundos.")
                return
            }
        }
        parar.set(false)
        activo.set(true)
        hilo = Thread(::cicloConexion, "aoa-sync").apply {
            isDaemon = true
            start()
        }
    }

    /** Corta el enlace actual y abre uno nuevo (botón «Reintentar» de la UI). */
    fun reiniciar() {
        detener()
        iniciar()
    }

    /** Pide la desconexión; la limpieza de libusb se hace en el hilo de lectura. */
    fun detener() {
        // `activo` se baja recién en el finally del hilo para no solapar
        // una nueva conexión con la limpieza de libusb. `conectada` sí cae ya:
        // así ningún solicitante escribe sobre un handle en cierre.
        parar.set(true)
        conectada = false
        hilo?.interrupt()
        pinPendiente.getAndSet(null)?.future?.cancel(true)
    }

    fun establecerReceptor(receptor: (TramaAoa) -> Unit) {
        this.receptor = receptor
    }

    /** Envía un mensaje al móvil con el framing de 4 bytes. */
    fun enviar(mensaje: MensajeAoa): Boolean {
        val handle = handleRef.get()
        if (!conectada || handle == null) {
            onLog("⚠️ No se puede enviar '${mensaje.evento}': el móvil no está conectado.")
            return false
        }

        val payload = ProtocoloAoa.json
            .encodeToString(MensajeAoa.serializer(), mensaje)
            .toByteArray(StandardCharsets.UTF_8)
        val trama = TramasAoa.construir(payload)

        return synchronized(escrituraLock) {
            var offset = 0
            var enviado = true
            while (offset < trama.size && enviado) {
                val porcion = minOf(CHUNK_OUT_BYTES, trama.size - offset)
                val buffer = ByteBuffer.allocateDirect(porcion)
                buffer.put(trama, offset, porcion)
                val transferidos = IntBuffer.allocate(1)
                val resultado = LibUsb.bulkTransfer(
                    handle, ENDPOINT_OUT, buffer, transferidos, WRITE_TIMEOUT_MS
                )
                if (resultado != LibUsb.SUCCESS || transferidos.get() != porcion) {
                    onLog("⚠️ Error en bulk OUT ($resultado, ${transferidos.get()}/$porcion bytes).")
                    enviado = false
                }
                offset += porcion
            }
            if (enviado) {
                onLog("📤 Comando AOA '${mensaje.evento}' enviado (${payload.size} bytes).")
            }
            enviado
        }
    }

    /**
     * Pide el PIN al móvil y espera la respuesta. Bloquea: llamar desde hilo de
     * fondo. Si todavía no hay conexión, espera hasta [esperaConexionMs] para
     * dar tiempo a que el teléfono termine el handshake AOAP.
     */
    fun solicitarPin(
        motivo: String,
        timeoutMs: Long = 120_000,
        esperaConexionMs: Long = 25_000
    ): ResultadoPin {
        if (!esperarConexion(esperaConexionMs)) {
            return ResultadoPin(
                false,
                null,
                "El móvil no se conectó en ${esperaConexionMs / 1000} s. " +
                    "Conectá el cable, abrí la app y presioná «Sincronizar»."
            )
        }

        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<BodyRespuestaPin>()
        val pendiente = PinPendiente(id, future)
        if (!pinPendiente.compareAndSet(null, pendiente)) {
            return ResultadoPin(false, null, "Ya hay una solicitud de PIN en curso.")
        }

        if (!enviar(crearSolicitudPin(motivo, id))) {
            pinPendiente.compareAndSet(pendiente, null)
            return ResultadoPin(false, null, "No se pudo enviar la solicitud de PIN al móvil.")
        }
        onLog("📲 Esperando PIN del móvil (id=${id.take(8)})...")

        return try {
            val respuesta = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            when {
                respuesta.cancelado -> ResultadoPin(
                    ok = false,
                    pin = null,
                    mensaje = "El usuario canceló la solicitud de PIN en el móvil.",
                    consentimiento = respuesta.consentimiento
                )
                respuesta.pin.isNullOrBlank() -> ResultadoPin(
                    ok = false,
                    pin = null,
                    mensaje = "El móvil devolvió un PIN vacío.",
                    consentimiento = respuesta.consentimiento
                )
                else -> ResultadoPin(
                    ok = true,
                    pin = respuesta.pin.toCharArray(),
                    mensaje = "PIN recibido desde el móvil.",
                    consentimiento = respuesta.consentimiento
                )
            }
        } catch (e: TimeoutException) {
            pinPendiente.compareAndSet(pendiente, null)
            ResultadoPin(false, null, "El móvil no respondió el PIN en ${timeoutMs / 1000} s.")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            pinPendiente.compareAndSet(pendiente, null)
            ResultadoPin(false, null, "Solicitud de PIN interrumpida.")
        } catch (e: ExecutionException) {
            pinPendiente.compareAndSet(pendiente, null)
            ResultadoPin(false, null, "Error al esperar el PIN: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Llamado por el controlador cuando llega un evento `pin_respuesta`. */
    fun resolverPin(respuesta: BodyRespuestaPin) {
        val pendiente = pinPendiente.get() ?: return
        if (!respuesta.id.isNullOrBlank() && respuesta.id != pendiente.id) {
            onLog("⚠️ Respuesta de PIN con id desconocido; se ignora.")
            return
        }
        if (pinPendiente.compareAndSet(pendiente, null)) {
            pendiente.future.complete(respuesta)
        }
    }

    /** Espera a que el hilo `aoa-sync` termine el handshake. Devuelve false si se detuvo. */
    private fun esperarConexion(esperaMs: Long): Boolean {
        if (conectada) return true
        onLog("⏳ Esperando al móvil para pedir el PIN (hasta ${esperaMs / 1000} s)...")
        val fin = System.currentTimeMillis() + esperaMs
        while (System.currentTimeMillis() < fin) {
            if (conectada) return true
            if (!activo.get() || parar.get()) return false
            try {
                Thread.sleep(250)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return conectada
    }

    private fun cicloConexion() {
        var libusbInicializado = false
        try {
            val resultadoInit = LibUsb.init(null)
            if (resultadoInit != LibUsb.SUCCESS) {
                onLog("❌ No se pudo inicializar libusb (código: $resultadoInit).")
                return
            }
            libusbInicializado = true
            onEstado(EstadoConexion.CONECTANDO)

            val handle = conectarOIniciarAoa()
            if (handle == null) {
                onLog("⚠️ No se pudo establecer conexión AOAP. Verifique el cable o reinicie el servicio.")
                return
            }

            handleRef.set(handle)
            conectada = true
            onEstado(EstadoConexion.CONECTADO)
            onLog("✅ Conexión establecida con éxito y dispositivo reclamado.")
            escuchar(handle)
        } catch (e: Exception) {
            onLog("❌ Error crítico en el servidor USB: ${e.localizedMessage}")
        } finally {
            conectada = false
            handleRef.getAndSet(null)?.let { handle ->
                try {
                    LibUsb.releaseInterface(handle, 0)
                } catch (_: Exception) {
                }
                try {
                    LibUsb.close(handle)
                } catch (_: Exception) {
                }
            }
            if (libusbInicializado) {
                try {
                    LibUsb.exit(null)
                } catch (_: Exception) {
                }
            }
            onEstado(EstadoConexion.DESCONECTADO)
            activo.set(false)
            parar.set(false)
            hilo = null
            onLog("🔌 Servidor USB detenido.")
        }
    }

    /** Reutiliza un accesorio ya activo o ejecuta el handshake AOAP. */
    private fun conectarOIniciarAoa(): DeviceHandle? {
        buscarHandleAoa()?.let {
            onLog("🔌 Dispositivo ya conectado en modo AOA. Interfaz reclamada.")
            return it
        }
        if (!enviarHandshakeAoa()) return null

        onLog("⏳ Esperando a que el dispositivo se reconecte en modo accesorio...")
        repeat(20) { intento ->
            if (!activo.get() || parar.get()) return null
            Thread.sleep(1000)
            buscarHandleAoa()?.let {
                onLog("✅ Dispositivo AOA detectado tras ${intento + 1} segundo(s).")
                return it
            }
        }
        return null
    }

    private fun buscarHandleAoa(): DeviceHandle? {
        val lista = DeviceList()
        LibUsb.getDeviceList(null, lista)
        try {
            for (device in lista) {
                val descriptor = DeviceDescriptor()
                if (LibUsb.getDeviceDescriptor(device, descriptor) != LibUsb.SUCCESS) continue

                val vid = descriptor.idVendor().toInt() and 0xffff
                val pid = descriptor.idProduct().toInt() and 0xffff
                if (vid != AOA_VENDOR_GOOGLE || pid !in AOA_PRODUCT_IDS) continue

                val handle = DeviceHandle()
                if (LibUsb.open(device, handle) != LibUsb.SUCCESS) continue
                LibUsb.setAutoDetachKernelDriver(handle, true)

                val resultado = LibUsb.claimInterface(handle, 0)
                if (resultado == LibUsb.SUCCESS) {
                    onLog(
                        "🔌 Interfaz USB reclamada (VID: 0x${vid.toString(16)}, " +
                            "PID: 0x${pid.toString(16)})."
                    )
                    return handle
                }
                onLog("⚠️ Error al reclamar la interfaz USB: $resultado")
                LibUsb.close(handle)
            }
        } finally {
            LibUsb.freeDeviceList(lista, true)
        }
        return null
    }

    private fun enviarHandshakeAoa(): Boolean {
        val lista = DeviceList()
        LibUsb.getDeviceList(null, lista)
        try {
            for (device in lista) {
                val descriptor = DeviceDescriptor()
                if (LibUsb.getDeviceDescriptor(device, descriptor) != LibUsb.SUCCESS) continue

                val vid = descriptor.idVendor().toInt() and 0xffff
                val pid = descriptor.idProduct().toInt() and 0xffff
                if (vid == AOA_VENDOR_GOOGLE && pid in AOA_PRODUCT_IDS) continue

                val handle = DeviceHandle()
                if (LibUsb.open(device, handle) != LibUsb.SUCCESS) continue
                try {
                    val bufferProtocolo = ByteBuffer.allocateDirect(2)
                    val resultadoProtocolo = LibUsb.controlTransfer(
                        handle,
                        (LibUsb.ENDPOINT_IN or LibUsb.REQUEST_TYPE_VENDOR or LibUsb.RECIPIENT_DEVICE).toByte(),
                        51.toByte(), // ACCESSORY_GET_PROTOCOL
                        0, 0, bufferProtocolo, 1000
                    )
                    if (resultadoProtocolo < 2) continue

                    bufferProtocolo.order(ByteOrder.LITTLE_ENDIAN)
                    val version = bufferProtocolo.getShort(0).toInt() and 0xffff
                    onLog("🔄 Dispositivo estándar detectado (VID: 0x${vid.toString(16)}, PID: 0x${pid.toString(16)}).")
                    onLog("✅ Protocolo AOA soportado. Versión detectada: $version")

                    enviarCadenaControl(handle, 0, AOA_MANUFACTURER)
                    enviarCadenaControl(handle, 1, AOA_MODEL)
                    enviarCadenaControl(handle, 2, AOA_DESCRIPTION)
                    enviarCadenaControl(handle, 3, AOA_VERSION)
                    enviarCadenaControl(handle, 4, AOA_URI)
                    enviarCadenaControl(handle, 5, AOA_SERIAL)

                    // usb4java ignora data == null; hace falta un buffer directo vacío.
                    val inicio = ByteBuffer.allocateDirect(0)
                    val resultadoInicio = LibUsb.controlTransfer(
                        handle,
                        (LibUsb.ENDPOINT_OUT or LibUsb.REQUEST_TYPE_VENDOR or LibUsb.RECIPIENT_DEVICE).toByte(),
                        53.toByte(), // ACCESSORY_START
                        0, 0, inicio, 1000
                    )
                    if (resultadoInicio == LibUsb.SUCCESS) {
                        onLog("🚀 ACCESSORY_START enviado. El móvil debe reiniciar su USB en modo accesorio.")
                        return true
                    }
                    onLog("⚠️ Error al enviar ACCESSORY_START: $resultadoInicio")
                } finally {
                    LibUsb.close(handle)
                }
            }
        } finally {
            LibUsb.freeDeviceList(lista, true)
        }
        return false
    }

    /** ACCESSORY_SEND_STRING exige cadena UTF-8 terminada en NUL. */
    private fun enviarCadenaControl(handle: DeviceHandle, index: Int, valor: String) {
        val datos = valor.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocateDirect(datos.size + 1)
        buffer.put(datos)
        buffer.put(0.toByte())
        val resultado = LibUsb.controlTransfer(
            handle,
            (LibUsb.ENDPOINT_OUT or LibUsb.REQUEST_TYPE_VENDOR or LibUsb.RECIPIENT_DEVICE).toByte(),
            52.toByte(), // ACCESSORY_SEND_STRING
            0, index.toShort(), buffer, 1000
        )
        if (resultado < 0) {
            onLog("⚠️ Error al enviar metadato [$index] ('$valor'): $resultado")
        }
    }

    private fun escuchar(handle: DeviceHandle) {
        val clear = LibUsb.clearHalt(handle, ENDPOINT_IN)
        if (clear != LibUsb.SUCCESS) {
            onLog("⚠️ No se pudo limpiar el halt del endpoint IN: $clear")
        }
        onLog("👂 Escuchando paquetes AOA...")

        val acumulador = AcumuladorTramas()
        while (activo.get() && !parar.get()) {
            // Siempre pedir un bloque grande: pedir solo 4 bytes provoca
            // ERROR_OVERFLOW cuando el móvil envía la trama completa junta.
            val buffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE)
            val transferidos = IntBuffer.allocate(1)
            val resultado = LibUsb.bulkTransfer(
                handle, ENDPOINT_IN, buffer, transferidos, READ_TIMEOUT_MS
            )
            if (resultado != LibUsb.SUCCESS && resultado != LibUsb.ERROR_TIMEOUT) {
                onLog("❌ Error en transferencia Bulk IN: $resultado")
                break
            }

            val leidos = transferidos.get()
            if (leidos <= 0) continue

            val chunk = ByteArray(leidos)
            buffer.get(chunk, 0, leidos)
            for (trama in acumulador.alimentar(chunk)) {
                val mensaje = interpretarTrama(trama)
                try {
                    receptor(mensaje)
                } catch (e: Exception) {
                    onLog("⚠️ Error al procesar trama AOA: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    private companion object {
        const val AOA_VENDOR_GOOGLE = 0x18D1
        val AOA_PRODUCT_IDS = setOf(0x2D00, 0x2D01, 0x2D02, 0x2D03, 0x2D04, 0x2D05)

        const val AOA_MANUFACTURER = "MiEmpresa"
        const val AOA_MODEL = "FirmaDigitalAccessory"
        const val AOA_DESCRIPTION = "Cliente Firma Digital"
        const val AOA_VERSION = "1.0"
        const val AOA_URI = "http://www.minuri.com"
        const val AOA_SERIAL = "123456"

        val ENDPOINT_IN: Byte = 0x81.toByte()
        val ENDPOINT_OUT: Byte = 0x01.toByte()
        const val READ_TIMEOUT_MS = 2000L
        const val WRITE_TIMEOUT_MS = 2000L
        const val READ_BUFFER_SIZE = 16 * 1024
        const val CHUNK_OUT_BYTES = 16 * 1024
    }
}

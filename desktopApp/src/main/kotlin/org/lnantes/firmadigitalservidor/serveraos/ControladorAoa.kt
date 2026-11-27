package org.lnantes.firmadigitalservidor.serveraos

import org.lnantes.firmadigitalservidor.FirmaReciboController
import org.lnantes.firmadigitalservidor.FirmaSQLiteController
import java.util.concurrent.atomic.AtomicReference

/**
 * Punto de entrada de la capa de sincronización. Crea y conecta todos los
 * servicios del paquete `serveraos` y despacha cada trama según su evento.
 *
 * La UI solo debe:
 *  - llamar a [iniciar] / [detener];
 *  - usar [servicioComun] y [servicioAvanzado] para ejecutar los casos de uso;
 *  - mostrar en pantalla los callbacks que recibe por constructor.
 */
class ControladorAoa(
    controladorFirma: FirmaReciboController,
    private val baseDatos: FirmaSQLiteController,
    private val proveedorCodigo: () -> String?,
    private val onLog: (String) -> Unit,
    private val onFirmaRecibida: () -> Unit,
    private val onEstadoConexion: (ConexionAoa.EstadoConexion) -> Unit = {}
) {
    /** Modo que se envía al móvil siempre que se establece la conexión. */
    private val modoDeseado = AtomicReference(ProtocoloAoa.MODO_COMUN)

    val capturador = CapturadorTrazos(proveedorCodigo, baseDatos, onLog)
    val conexion = ConexionAoa(onLog, { estado ->
        // Al conectar se reenvía el último modo ordenado por el escritorio.
        // Por defecto el móvil arranca en «común».
        if (estado == ConexionAoa.EstadoConexion.CONECTADO) {
            enviarModoFirma(modoDeseado.get())
        }
        onEstadoConexion(estado)
    })
    val servicioComun = ServicioFirmaComun(
        controladorFirma = controladorFirma,
        capturador = capturador,
        baseDatos = baseDatos,
        endpointDescripcion = ENDPOINT_FIRMA,
        onLog = onLog
    )
    val servicioAvanzado = ServicioFirmaAvanzada(conexion, onLog)

    init {
        conexion.establecerReceptor { trama ->
            when (trama) {
                is TramaAoa.Trazos -> {
                    onLog("📥 Trama de trazos recibida (${trama.crudo.size} bytes).")
                    capturador.procesar(trama.crudo)
                    onFirmaRecibida()
                }
                is TramaAoa.Pin -> {
                    // Nunca loguear el PIN: solo el hecho de que llegó.
                    onLog("📥 Respuesta de PIN recibida desde el móvil.")
                    guardarConsentimientoPin(trama)
                    conexion.resolverPin(trama.respuesta)
                }
                is TramaAoa.Texto -> {
                    onLog("📥 Trama de texto recibida (${trama.contenido.length} caracteres).")
                }
                is TramaAoa.Comando -> {
                    onLog("📥 Comando recibido del móvil: ${trama.mensaje.evento}.")
                }
            }
        }
    }

    /** Envía la orden de modo al móvil y la recuerda para futuras conexiones. */
    fun enviarModoFirma(modo: String) {
        val normalizado = if (modo == ProtocoloAoa.MODO_AVANZADA) {
            ProtocoloAoa.MODO_AVANZADA
        } else {
            ProtocoloAoa.MODO_COMUN
        }
        modoDeseado.set(normalizado)
        conexion.enviar(crearModoFirma(normalizado))
    }

    /** Persiste el consentimiento que el móvil adjuntó a la respuesta de PIN. */
    private fun guardarConsentimientoPin(trama: TramaAoa.Pin) {
        val consentimiento = trama.respuesta.consentimiento ?: run {
            onLog("⚠️ La respuesta de PIN no trae consentimiento adjunto.")
            return
        }
        try {
            baseDatos.guardarConsentimiento(
                proveedorCodigo(),
                consentimiento,
                "avanzada"
            )
            onLog(
                "🗄️ Consentimiento de firma avanzada guardado en SQLite: " +
                    "tipo=${consentimiento.tipo}, otorgado=${consentimiento.otorgado}, " +
                    "normativa=${consentimiento.normativaId}."
            )
        } catch (e: Exception) {
            onLog("⚠️ No se pudo guardar el consentimiento: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun iniciar() = conexion.iniciar()

    /** Reinicia el handshake AOA sin recrear el controlador ni la ventana. */
    fun reiniciar() = conexion.reiniciar()

    fun detener() = conexion.detener()

    private companion object {
        const val ENDPOINT_FIRMA = "http://localhost:8000/devoluciones/recibos/firmar"
    }
}

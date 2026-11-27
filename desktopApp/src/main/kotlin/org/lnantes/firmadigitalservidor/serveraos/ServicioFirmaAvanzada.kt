package org.lnantes.firmadigitalservidor.serveraos

import org.lnantes.firmadigitalservidor.avanzada.FirmadorAvanzadoPkcs11
import org.lnantes.firmadigitalservidor.pdfs.FirmadorPDF
import java.io.File

/**
 * Caso de uso «firma avanzada de PDF con token». Orquesta:
 *  1. pedir el PIN al móvil por AOA (o usar un PIN local);
 *  2. inicializar el dispositivo PKCS#11;
 *  3. validar certificado y estampar la firma PAdES.
 *
 * No abre ventanas ni visores: eso lo decide la UI con el resultado.
 * Bloquea mientras espera el PIN, así que debe ejecutarse en hilo de fondo.
 */
class ServicioFirmaAvanzada(
    private val conexion: ConexionAoa,
    private val onLog: (String) -> Unit = {}
) {
    data class Parametros(
        val pdfEntrada: File,
        val rutaLibreria: String,
        val pinLocal: CharArray? = null,
        val pedirPinAlMovil: Boolean = true,
        val motivo: String = "Recepcion de indumentaria",
        val ubicacion: String = "Montevideo, Uruguay",
        val conSelloVisible: Boolean = true,
        val verificarRevocacion: Boolean = true,
        val timeoutPinMs: Long = 120_000
    )

    data class Resultado(
        val ok: Boolean,
        val archivo: File?,
        val mensaje: String,
        val resumenCertificado: String? = null
    )

    fun firmar(parametros: Parametros): Resultado {
        var pin: CharArray? = null
        var firmador: FirmadorAvanzadoPkcs11? = null

        try {
            pin = if (parametros.pedirPinAlMovil) {
                onLog("📲 Solicitando PIN al dispositivo móvil para firma avanzada...")
                val respuesta = conexion.solicitarPin(parametros.motivo, parametros.timeoutPinMs)
                if (!respuesta.ok) {
                    return Resultado(false, null, respuesta.mensaje)
                }
                onLog("🔐 ${respuesta.mensaje}")
                onLog("🔑 Abriendo sesión PKCS#11 con el PIN recibido del móvil...")
                respuesta.pin
            } else {
                onLog("⌨️ Usando PIN ingresado en la ventana (firma avanzada).")
                parametros.pinLocal
            }

            firmador = FirmadorAvanzadoPkcs11(parametros.rutaLibreria)
            firmador.inicializarDispositivo(pin)
            val certificados = firmador.obtenerCadenaCertificados()
            onLog("✅ Token inicializado: ${certificados.size} certificado(s), titular=${
                firmador.obtenerNombreComun() ?: "desconocido"
            }")

            val validacion = firmador.validarCertificado(parametros.verificarRevocacion)
            onLog("🔎 Validación del certificado: ${validacion.resumen}")
            if (!validacion.vigente) {
                return Resultado(
                    false,
                    null,
                    "No se firmó: ${validacion.motivo}. ${validacion.resumen}"
                )
            }
            if (validacion.revocacion == org.lnantes.firmadigitalservidor.avanzada.ValidadorCertificado.EstadoRevocacion.REVOCADA) {
                return Resultado(
                    false,
                    null,
                    "No se firmó: el certificado está revocado (${validacion.detalleRevocacion}). ${validacion.resumen}"
                )
            }

            val helper = FirmadorPDF(firmador)
            val salida = File(
                parametros.pdfEntrada.parentFile,
                "${parametros.pdfEntrada.nameWithoutExtension}_firmado.pdf"
            )
            onLog("🖊️ Estampando firma en ${parametros.pdfEntrada.name} → ${salida.name}...")
            val archivo = helper.estamparFirma(
                pdfEntrada = parametros.pdfEntrada,
                pdfSalida = salida,
                motivo = parametros.motivo,
                ubicacion = parametros.ubicacion,
                conSelloVisible = parametros.conSelloVisible,
                verificarRevocacion = parametros.verificarRevocacion
            )
            return Resultado(
                ok = true,
                archivo = archivo,
                mensaje = "PDF firmado guardado: ${archivo.absolutePath}",
                resumenCertificado = helper.ultimaValidacion?.resumen
            )
        } catch (e: Exception) {
            val detalle = "${e.javaClass.simpleName}: ${e.message ?: "sin mensaje"}"
            onLog("❌ Fallo la firma avanzada: $detalle")
            onLog(e.stackTraceToString().lineSequence().take(12).joinToString("\n"))
            return Resultado(false, null, detalle)
        } finally {
            // El PIN nunca debe quedar en memoria ni en logs.
            pin?.fill('\u0000')
            parametros.pinLocal?.fill('\u0000')
            firmador?.liberarProveedor()
        }
    }
}

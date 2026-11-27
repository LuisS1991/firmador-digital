package org.lnantes.firmadigitalservidor.server

import org.lnantes.firmadigitalservidor.FirmaSQLiteController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch

private val formatoFecha = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun registrar(mensaje: String) {
    println("${LocalDateTime.now().format(formatoFecha)} $mensaje")
}

fun main(args: Array<String>) {
    val puerto = args.firstOrNull()?.toIntOrNull() ?: ServidorTcpFirma.PUERTO
    val baseDatos = FirmaSQLiteController()
    val detener = CountDownLatch(1)

    val servidor = ServidorTcpFirma(
        puerto = puerto,
        buscarFirmaBase64 = { codigo -> baseDatos.obtener(codigo)?.firmaBase64 },
        onLog = ::registrar
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        servidor.detener()
        detener.countDown()
    })

    try {
        servidor.iniciar()
        registrar("Servidor TCP de firmas listo en puerto $puerto (Ctrl+C para detener).")
        detener.await()
    } catch (e: Exception) {
        System.err.println("No se pudo iniciar el servidor TCP en el puerto $puerto: ${e.message}")
        servidor.detener()
    }
}

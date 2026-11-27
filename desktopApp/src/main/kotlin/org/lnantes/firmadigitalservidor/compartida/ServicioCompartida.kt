package org.lnantes.firmadigitalservidor.compartida

import kotlin.jvm.JvmOverloads
import java.io.File

/** Resultado uniforme de las operaciones sobre la compartida. */
data class ResultadoCompartida<out T>(
    val ok: Boolean,
    val datos: T?,
    val mensaje: String
)

/**
 * Servicio de alto nivel para usar la carpeta compartida TIC Desarrollo.
 *
 * No sabe nada de USB, PDF ni HTTP: expone operaciones de archivos y delega el
 * transporte en [ClienteCompartida] (SMBJ o directorio montado).
 */
class ServicioCompartida @JvmOverloads constructor(
    val configuracion: ConfiguracionCompartida = ConfiguracionCompartida.cargar(),
    private val cliente: ClienteCompartida = crearClienteCompartida(configuracion),
    private val onLog: (String) -> Unit = {}
) {

    /** Comprueba host, credenciales y share sin escribir nada. */
    fun probarConexion(): ResultadoCompartida<String> = ejecutar("Probar conexión") {
        cliente.probarConexion()
        val destino = if (configuracion.modo == ModoCompartida.SMB) {
            configuracion.urlSmb
        } else {
            configuracion.rutaLocal
        }
        onLog("✅ Compartida OK: $destino (${configuracion.modo})")
        destino
    }

    fun listar(rutaRelativa: String = ""): ResultadoCompartida<List<EntradaCompartida>> =
        ejecutar("Listar '$rutaRelativa'") { cliente.listar(rutaRelativa) }

    fun existe(rutaRelativa: String): ResultadoCompartida<Boolean> =
        ejecutar("Existe '$rutaRelativa'") { cliente.existe(rutaRelativa) }

    fun leer(rutaRelativa: String): ResultadoCompartida<ByteArray> =
        ejecutar("Leer '$rutaRelativa'") { cliente.leer(rutaRelativa) }

    fun guardar(
        archivo: File,
        rutaRelativa: String = archivo.name
    ): ResultadoCompartida<String> = ejecutar("Subir ${archivo.name}") {
        require(archivo.isFile) { "El archivo no existe: ${archivo.absolutePath}" }
        cliente.escribir(rutaRelativa, archivo.readBytes())
        onLog("⬆️ Subido ${archivo.name} (${archivo.length()} bytes) → $rutaRelativa")
        rutaRelativa
    }

    fun guardarBytes(
        rutaRelativa: String,
        datos: ByteArray
    ): ResultadoCompartida<String> = ejecutar("Subir bytes a '$rutaRelativa'") {
        cliente.escribir(rutaRelativa, datos)
        onLog("⬆️ Subidos ${datos.size} bytes → $rutaRelativa")
        rutaRelativa
    }

    /** Guarda un recibo firmado con un nombre correlacionado al código. */
    fun guardarRecibo(
        pdf: File,
        codigo: String
    ): ResultadoCompartida<String> {
        val seguro = codigo.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return guardar(pdf, "recibo_$seguro.pdf")
    }

    fun descargar(
        rutaRelativa: String,
        destino: File
    ): ResultadoCompartida<File> = ejecutar("Descargar '$rutaRelativa'") {
        val datos = cliente.leer(rutaRelativa)
        destino.parentFile?.mkdirs()
        destino.writeBytes(datos)
        onLog("⬇️ Descargado $rutaRelativa → ${destino.name} (${datos.size} bytes)")
        destino
    }

    fun eliminar(rutaRelativa: String): ResultadoCompartida<Boolean> =
        ejecutar("Eliminar '$rutaRelativa'") { cliente.eliminar(rutaRelativa) }

    private fun <T> ejecutar(operacion: String, bloque: () -> T): ResultadoCompartida<T> =
        try {
            ResultadoCompartida(ok = true, datos = bloque(), mensaje = "$operacion: OK")
        } catch (error: Exception) {
            val detalle = "${error.javaClass.simpleName}: ${error.message ?: "sin mensaje"}"
            onLog("❌ $operacion → $detalle")
            ResultadoCompartida(ok = false, datos = null, mensaje = detalle)
        }
}

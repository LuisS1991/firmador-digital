package org.lnantes.firmadigitalservidor.compartida

import java.io.File

/**
 * Implementación para cuando el share ya está montado por el sistema
 * (por ejemplo `/mnt/mounted_disk`). Útil como fallback si SMBJ tiene
 * problemas de red/firewall: la configuración sigue siendo la misma.
 */
class ClienteCompartidaLocal(
    private val configuracion: ConfiguracionCompartida
) : ClienteCompartida {

    private val raiz: File by lazy {
        val ruta = configuracion.rutaLocal.ifBlank {
            error("Falta compartida.ruta.local para el modo LOCAL")
        }
        val base = File(ruta).canonicalFile
        require(base.isDirectory) { "No existe el directorio montado: ${base.absolutePath}" }

        val subcarpeta = configuracion.rutaRemota
            .trim()
            .trim('/', '\\')
            .replace('/', File.separatorChar)
            .replace('\\', File.separatorChar)
        val destino = if (subcarpeta.isBlank()) base else File(base, subcarpeta).canonicalFile
        destino.apply {
            mkdirs()
            require(isDirectory) { "No existe la subcarpeta: ${absolutePath}" }
        }
    }

    override fun probarConexion() {
        raiz
    }

    override fun listar(rutaRelativa: String): List<EntradaCompartida> =
        resolver(rutaRelativa).listFiles()?.map { archivo ->
            EntradaCompartida(
                nombre = archivo.name,
                esDirectorio = archivo.isDirectory,
                tamano = if (archivo.isFile) archivo.length() else 0L
            )
        }?.sortedBy { it.nombre } ?: emptyList()

    override fun leer(rutaRelativa: String): ByteArray = resolver(rutaRelativa).readBytes()

    override fun escribir(rutaRelativa: String, datos: ByteArray) {
        val destino = resolver(rutaRelativa)
        destino.parentFile?.mkdirs()
        destino.writeBytes(datos)
    }

    override fun existe(rutaRelativa: String): Boolean = resolver(rutaRelativa).exists()

    override fun crearDirectorio(rutaRelativa: String) {
        resolver(rutaRelativa).mkdirs()
    }

    override fun eliminar(rutaRelativa: String): Boolean {
        val destino = resolver(rutaRelativa)
        if (!destino.exists()) return false
        return destino.deleteRecursively()
    }

    private fun resolver(rutaRelativa: String): File {
        val relativa = rutaRelativaSegura(rutaRelativa)
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString(File.separator)
        val destino = if (relativa.isBlank()) raiz else File(raiz, relativa).canonicalFile
        require(destino.toPath().startsWith(raiz.toPath())) {
            "La ruta sale de la carpeta compartida: '$rutaRelativa'"
        }
        return destino
    }
}

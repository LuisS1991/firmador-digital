package org.lnantes.firmadigitalservidor.compartida

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * Cliente SMBJ para `\\10.100.52.4\tic-desarollo`.
 *
 * Abre y cierra la sesión en cada operación: es más lento que mantener una
 * conexión viva, pero evita sesiones expiradas y hace el helper seguro para
 * hilos. Las credenciales se toman de [ConfiguracionCompartida] (archivo,
 * entorno o comando de Seahorse) y nunca se loguean.
 */
class ClienteCompartidaSmb(
    private val configuracion: ConfiguracionCompartida
) : ClienteCompartida {

    private val smbConfig: SmbConfig = SmbConfig.builder()
        .withTimeout(configuracion.timeoutMs, TimeUnit.MILLISECONDS)
        .withSoTimeout(configuracion.timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    override fun probarConexion() = conShare { share ->
        share.list("")
        Unit
    }

    override fun listar(rutaRelativa: String): List<EntradaCompartida> = conShare { share ->
        val ruta = rutaSmb(configuracion, rutaRelativa)
        share.list(ruta)
            .filter { it.fileName !in setOf(".", "..") }
            .map { entrada ->
                EntradaCompartida(
                    nombre = entrada.fileName,
                    esDirectorio = entrada.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L,
                    tamano = entrada.endOfFile
                )
            }
    }

    override fun leer(rutaRelativa: String): ByteArray = conShare { share ->
        val ruta = rutaSmb(configuracion, rutaRelativa)
        val archivo = share.openFile(
            ruta,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        archivo.use { abierto ->
            abierto.inputStream.use { entrada -> entrada.readBytes() }
        }
    }

    override fun escribir(rutaRelativa: String, datos: ByteArray) = conShare { share ->
        val ruta = rutaSmb(configuracion, rutaRelativa)
        crearPadres(share, ruta)
        val archivo = share.openFile(
            ruta,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        archivo.use { abierto ->
            abierto.outputStream.use { salida -> salida.write(datos) }
            abierto.flush()
        }
        Unit
    }

    override fun existe(rutaRelativa: String): Boolean = conShare { share ->
        val ruta = rutaSmb(configuracion, rutaRelativa)
        share.fileExists(ruta) || share.folderExists(ruta)
    }

    override fun crearDirectorio(rutaRelativa: String) = conShare { share ->
        crearPadres(share, rutaSmb(configuracion, rutaRelativa))
        Unit
    }

    override fun eliminar(rutaRelativa: String): Boolean = conShare { share ->
        val ruta = rutaSmb(configuracion, rutaRelativa)
        when {
            share.fileExists(ruta) -> {
                share.rm(ruta)
                true
            }
            share.folderExists(ruta) -> {
                share.rmdir(ruta, true)
                true
            }
            else -> false
        }
    }

    private fun crearPadres(share: DiskShare, ruta: String) {
        val partes = ruta.split('\\').filter { it.isNotBlank() }
        if (partes.size <= 1) return
        var acumulado = ""
        partes.dropLast(1).forEach { parte ->
            acumulado = if (acumulado.isBlank()) parte else "$acumulado\\$parte"
            if (!share.folderExists(acumulado)) {
                runCatching { share.mkdir(acumulado) }
            }
        }
    }

    /** Abre cliente → conexión → sesión → share y garantiza el cierre en orden inverso. */
    private fun <T> conShare(block: (DiskShare) -> T): T {
        var cliente: SMBClient? = null
        var conexion: Connection? = null
        var sesion: Session? = null
        var share: DiskShare? = null
        return try {
            cliente = SMBClient(smbConfig)
            conexion = cliente.connect(configuracion.host, configuracion.puerto)
            val autenticacion = if (configuracion.usuario.isBlank()) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(
                    configuracion.usuario,
                    configuracion.password.toCharArray(),
                    configuracion.dominio
                )
            }
            sesion = conexion.authenticate(autenticacion)
            share = sesion.connectShare(configuracion.share) as? DiskShare
                ?: error("El recurso '${configuracion.share}' no es un DiskShare")
            block(share)
        } finally {
            runCatching { share?.close() }
            runCatching { sesion?.close() }
            runCatching { conexion?.close() }
            runCatching { cliente?.close() }
        }
    }
}

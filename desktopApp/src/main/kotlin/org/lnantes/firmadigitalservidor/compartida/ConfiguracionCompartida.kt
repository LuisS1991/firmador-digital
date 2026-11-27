package org.lnantes.firmadigitalservidor.compartida

import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

enum class ModoCompartida { SMB, LOCAL }

/**
 * Configuración de la carpeta compartida. La password nunca se muestra en
 * [toString] para que aparecer en logs no filtre credenciales.
 */
data class ConfiguracionCompartida @JvmOverloads constructor(
    val modo: ModoCompartida = ModoCompartida.SMB,
    val host: String = "10.100.52.4",
    val puerto: Int = 445,
    val share: String = "tic-desarollo",
    val dominio: String = "",
    val usuario: String = "",
    val password: String = "",
    val passwordComando: String = "",
    val rutaRemota: String = "",
    val rutaLocal: String = "",
    val timeoutMs: Long = 15_000L
) {
    /** URL UNC útil para logs o para montar manualmente. */
    val urlSmb: String
        get() = "\\\\$host\\$share" +
            (rutaRemota.trim().trim('/', '\\').takeIf { it.isNotBlank() }?.let { "\\$it" } ?: "")

    override fun toString(): String =
        "ConfiguracionCompartida(modo=$modo, host='$host', puerto=$puerto, " +
            "share='$share', dominio='$dominio', usuario='$usuario', " +
            "password=***, passwordComando='$passwordComando', " +
            "rutaRemota='$rutaRemota', rutaLocal='$rutaLocal', timeoutMs=$timeoutMs)"

    companion object {
        private const val PREFIJO = "compartida."

        /** Carga la configuración con prioridad: sistema > entorno > archivo > defaults. */
        @JvmStatic
        @JvmOverloads
        fun cargar(rutaArchivo: String? = null): ConfiguracionCompartida {
            val propiedades = Properties()

            // De menor a mayor prioridad. El resource embebido nunca tiene la password.
            cargarRecurso("org/lnantes/firmadigitalservidor/config/compartida.properties", propiedades)
            cargarArchivo(File("compartida.properties"), propiedades)
            cargarArchivo(File("config/compartida.properties"), propiedades)

            val explicita = rutaArchivo
                ?: System.getProperty("${PREFIJO}config")
                ?: System.getenv("COMPARTIDA_CONFIG")
            explicita?.takeIf { it.isNotBlank() }?.let { cargarArchivo(File(it), propiedades) }

            fun valor(clave: String, variableEntorno: String, defecto: String = ""): String =
                System.getProperty("$PREFIJO$clave")
                    ?.takeIf { it.isNotBlank() }
                    ?: System.getenv(variableEntorno)?.takeIf { it.isNotBlank() }
                    ?: propiedades.getProperty("$PREFIJO$clave", defecto).trim()

            val passwordLiteral = valor("password", "COMPARTIDA_PASSWORD")
            val passwordComando = valor("password.comando", "COMPARTIDA_PASSWORD_COMANDO")
            val password = passwordLiteral.ifBlank { ejecutarComando(passwordComando).orEmpty() }

            return ConfiguracionCompartida(
                modo = when (valor("modo", "COMPARTIDA_MODO", "smb").lowercase()) {
                    "local", "montada", "filesystem" -> ModoCompartida.LOCAL
                    else -> ModoCompartida.SMB
                },
                host = valor("host", "COMPARTIDA_HOST", "10.100.52.4"),
                puerto = valor("puerto", "COMPARTIDA_PUERTO", "445").toIntOrNull() ?: 445,
                share = valor("share", "COMPARTIDA_SHARE", "tic-desarollo"),
                dominio = valor("dominio", "COMPARTIDA_DOMINIO"),
                usuario = valor("usuario", "COMPARTIDA_USUARIO"),
                password = password,
                passwordComando = passwordComando,
                rutaRemota = valor("ruta.remota", "COMPARTIDA_RUTA_REMOTA"),
                rutaLocal = valor("ruta.local", "COMPARTIDA_RUTA_LOCAL", "/mnt/mounted_disk"),
                timeoutMs = valor("timeout.ms", "COMPARTIDA_TIMEOUT_MS", "15000")
                    .toLongOrNull()?.takeIf { it > 0 } ?: 15_000L
            )
        }

        /**
         * Ejecuta un comando externo (por ejemplo `secret-tool lookup ...`) y
         * devuelve stdout sin el salto final. No se registra ni se imprime nada.
         */
        @JvmStatic
        fun ejecutarComando(comando: String, timeoutMs: Long = 10_000L): String? {
            if (comando.isBlank()) return null
            return try {
                val esWindows = System.getProperty("os.name").lowercase().contains("win")
                val proceso = ProcessBuilder(
                    if (esWindows) listOf("cmd", "/c", comando)
                    else listOf("/bin/sh", "-c", comando)
                ).redirectErrorStream(false).start()

                val salida = proceso.inputStream.use { it.readBytes() }
                val finalizo = proceso.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finalizo) {
                    proceso.destroyForcibly()
                    return null
                }
                if (proceso.exitValue() != 0) return null
                String(salida, Charsets.UTF_8).trimEnd('\r', '\n', ' ')
            } catch (_: Exception) {
                null
            }
        }

        private fun cargarArchivo(archivo: File, propiedades: Properties) {
            if (!archivo.isFile) return
            archivo.inputStream().use { propiedades.load(it) }
        }

        private fun cargarRecurso(ruta: String, propiedades: Properties) {
            val stream = ConfiguracionCompartida::class.java.classLoader
                .getResourceAsStream(ruta) ?: return
            stream.use { propiedades.load(it) }
        }
    }
}

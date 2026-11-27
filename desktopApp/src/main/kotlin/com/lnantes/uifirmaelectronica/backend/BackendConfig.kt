package com.lnantes.uifirmaelectronica.backend

import org.lnantes.firmadigitalservidor.compartida.ConfiguracionCompartida
import java.io.File
import java.util.Properties

/**
 * Configuración del backend JVM del escritorio.
 *
 * El archivo se lee desde `config/firmador.properties` del directorio de
 * trabajo. Cualquier clave se puede sobrescribir con una system property
 * (`-Dfirmador.api.base.url=...`) o una variable de entorno
 * (`FIRMADOR_API_BASE_URL=...`). Las credenciales de la compartida se leen
 * aparte desde `config/compartida.properties` / Seahorse.
 */
data class BackendConfig(
    val apiBaseUrl: String,
    val rutaRecibo: String,
    val rutaReciboAlternativa: String,
    val rutaFirma: String,
    val codigoPorDefecto: String,
    /** Token del deep link (`?token=...`). Viaja en el POST; nunca se loguea. */
    val tokenDeepLink: String,
    val plantillaPdf: File,
    val sello: File,
    val directorioRecibos: File,
    val rutaSqlite: String,
    val libreriaPkcs11: String,
    val pinLocal: String,
    val tcpHabilitado: Boolean,
    val tcpPuerto: Int,
    val usbHabilitado: Boolean,
    val compartida: ConfiguracionCompartida
) {
    companion object {
        private const val PREFIJO = "firmador."
        private const val RUTA_PLANTILLA = "org/lnantes/firmadigitalservidor/docs/455527453.pdf"
        private const val RUTA_SELLO = "org/lnantes/firmadigitalservidor/icons/SELLO.png"

        @JvmStatic
        fun cargar(): BackendConfig {
            val propiedades = Properties()
            cargarRecurso("org/lnantes/firmadigitalservidor/config/firmador.properties", propiedades)
            File("firmador.properties").takeIf { it.isFile }?.let { propiedades.cargar(it) }
            File("config/firmador.properties").takeIf { it.isFile }?.let { propiedades.cargar(it) }

            fun valor(clave: String, defecto: String = ""): String {
                val variable = "FIRMADOR_" + clave.replace('.', '_').replace('-', '_').uppercase()
                return System.getProperty("$PREFIJO$clave")
                    ?.takeIf { it.isNotBlank() }
                    ?: System.getenv(variable)?.takeIf { it.isNotBlank() }
                    ?: propiedades.getProperty("$PREFIJO$clave", defecto).trim()
            }

            fun bandera(clave: String, defecto: Boolean): Boolean =
                valor(clave, if (defecto) "true" else "false").lowercase() in setOf("1", "true", "si", "yes", "on")

            return BackendConfig(
                apiBaseUrl = valor("api.base.url", "http://localhost:8000").trimEnd('/'),
                rutaRecibo = valor(
                    "api.recibo.ruta",
                    "/devolucion/generar/desktop/recibo/{codigo}"
                ),
                rutaReciboAlternativa = valor(
                    "api.recibo.ruta.alternativa",
                    "/devolucion/generar/recibo/{codigo}"
                ),
                rutaFirma = valor("api.firmar.ruta", "/devoluciones/recibos/firmar"),
                codigoPorDefecto = valor("codigo", "6B1E-144A-749B").uppercase(),
                tokenDeepLink = valor("token").ifBlank { valor("deeplink.token") },
                plantillaPdf = recurso(valor("pdf.plantilla"), RUTA_PLANTILLA),
                sello = recurso(valor("pdf.sello"), RUTA_SELLO),
                directorioRecibos = File(valor("recibos.dir", "recibos_firmados")).absoluteFile.apply { mkdirs() },
                rutaSqlite = valor("sqlite", "firmas.db"),
                libreriaPkcs11 = valor("pkcs11.library"),
                pinLocal = valor("pin.local"),
                tcpHabilitado = bandera("tcp.habilitado", true),
                tcpPuerto = valor("tcp.puerto", "5500").toIntOrNull() ?: 5500,
                usbHabilitado = bandera("usb.habilitado", true),
                compartida = ConfiguracionCompartida.cargar()
            )
        }

        private fun Properties.cargar(archivo: File) {
            archivo.inputStream().use { load(it) }
        }

        private fun cargarRecurso(ruta: String, propiedades: Properties) {
            BackendConfig::class.java.classLoader.getResourceAsStream(ruta)?.use(propiedades::load)
        }

        /** Usa la ruta configurada si existe; si no, la busca en recursos. */
        private fun recurso(configurado: String, rutaClaspath: String): File {
            if (configurado.isNotBlank()) {
                val explicito = File(configurado).absoluteFile
                require(explicito.isFile) { "No existe el recurso configurado: ${explicito.absolutePath}" }
                return explicito
            }

            val candidatos = listOf(
                File("desktopApp/src/main/resources", rutaClaspath),
                File("shared/src/commonMain/resources", rutaClaspath),
                File("src/main/resources", rutaClaspath)
            )
            candidatos.firstOrNull { it.isFile }?.let { return it.absoluteFile }

            val stream = BackendConfig::class.java.classLoader.getResourceAsStream(rutaClaspath)
                ?: error("No se encontró el recurso '$rutaClaspath' en el classpath")
            val extension = rutaClaspath.substringAfterLast('.', "tmp")
            return File.createTempFile("firmador-", ".$extension").apply {
                deleteOnExit()
                outputStream().use { stream.copyTo(it) }
            }
        }
    }
}

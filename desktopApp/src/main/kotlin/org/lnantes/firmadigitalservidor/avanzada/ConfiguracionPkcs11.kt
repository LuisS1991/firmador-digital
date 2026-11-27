package org.lnantes.firmadigitalservidor.avanzada

import java.io.File

/**
 * Descubre la configuración del driver PKCS#11 que instala el middleware de la
 * cédula/token (por ejemplo `~/apiaApplet.cfg`, con `library = .../libgclib.so`).
 *
 * El usuario final no debería tener que conocer ni escribir la ruta de la
 * librería: se lee del archivo que el propio middleware deja en su HOME.
 */
object ConfiguracionPkcs11 {

    data class Configuracion(
        val rutaLibreria: String?,
        val nombreDriver: String?,
        val origen: String
    )

    /**
     * Orden de precedencia:
     *  1. `-Dpkcs11.library=...` o variable de entorno `PKCS11_LIBRARY` (override explícito)
     *  2. `~/apiaApplet.cfg` (archivo que deja el middleware del token)
     *
     * El valor `name = ...` del cfg es informativo: SunPKCS11 necesita un nombre
     * de proveedor único por sesión, así que no se reutiliza para configurarlo.
     */
    @JvmStatic
    fun descubrir(): Configuracion {
        System.getProperty("pkcs11.library")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return Configuracion(it, null, "propiedad -Dpkcs11.library") }

        System.getenv("PKCS11_LIBRARY")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return Configuracion(it, null, "variable PKCS11_LIBRARY") }

        val archivo = File(System.getProperty("user.home"), "apiaApplet.cfg")
        if (archivo.isFile) {
            val texto = runCatching { archivo.readText(Charsets.UTF_8) }
                .getOrNull()
                .orEmpty()
            val libreria = leerClave(texto, "library")
            if (!libreria.isNullOrBlank()) {
                return Configuracion(libreria, leerClave(texto, "name"), archivo.absolutePath)
            }
        }

        return Configuracion(null, null, "no detectado")
    }

    /** Atajo usado por el firmador cuando no se le indicó una ruta explícita. */
    @JvmStatic
    fun descubrirRutaLibreria(): String? = descubrir().rutaLibreria

    private fun leerClave(texto: String, clave: String): String? =
        texto.lineSequence()
            .map { it.trim() }
            .firstOrNull { linea ->
                !linea.startsWith("#") &&
                    linea.startsWith(clave, ignoreCase = true) &&
                    linea.drop(clave.length).trimStart().startsWith("=")
            }
            ?.substringAfter('=', "")
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
}

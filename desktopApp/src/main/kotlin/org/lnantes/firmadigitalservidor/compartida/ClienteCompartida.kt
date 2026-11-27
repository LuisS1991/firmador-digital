package org.lnantes.firmadigitalservidor.compartida

/** Entrada devuelta por un listado de la carpeta compartida. */
data class EntradaCompartida(
    val nombre: String,
    val esDirectorio: Boolean,
    val tamano: Long
)

/** Operaciones mínimas que necesita la aplicación sobre la compartida. */
interface ClienteCompartida {
    fun probarConexion()
    fun listar(rutaRelativa: String = ""): List<EntradaCompartida>
    fun leer(rutaRelativa: String): ByteArray
    fun escribir(rutaRelativa: String, datos: ByteArray)
    fun existe(rutaRelativa: String): Boolean
    fun crearDirectorio(rutaRelativa: String)
    fun eliminar(rutaRelativa: String): Boolean
}

/** Fábrica que elige la implementación según [ConfiguracionCompartida.modo]. */
fun crearClienteCompartida(
    configuracion: ConfiguracionCompartida = ConfiguracionCompartida.cargar()
): ClienteCompartida = when (configuracion.modo) {
    ModoCompartida.SMB -> ClienteCompartidaSmb(configuracion)
    ModoCompartida.LOCAL -> ClienteCompartidaLocal(configuracion)
}

/** Normaliza una ruta relativa y evita que salga de la carpeta base. */
internal fun rutaRelativaSegura(ruta: String): String {
    val texto = ruta.trim().replace('\\', '/')
    require(!texto.startsWith("/")) { "La ruta debe ser relativa a la compartida: '$ruta'" }
    val partes = texto.split('/').filter { it.isNotBlank() && it != "." }
    require(partes.none { it == ".." }) { "No se permiten rutas con '..': '$ruta'" }
    return partes.joinToString("/")
}

/** Une la subcarpeta configurada con la ruta relativa, usando '\\' para SMB. */
internal fun rutaSmb(configuracion: ConfiguracionCompartida, rutaRelativa: String): String {
    val base = configuracion.rutaRemota.trim().trim('/', '\\').replace('/', '\\')
    val relativa = rutaRelativaSegura(rutaRelativa).replace('/', '\\')
    return listOf(base, relativa).filter { it.isNotBlank() }.joinToString("\\")
}

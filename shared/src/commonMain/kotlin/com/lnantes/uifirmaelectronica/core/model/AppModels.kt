package com.lnantes.uifirmaelectronica.core.model

/**
 * Modos de firma ofrecidos por la aplicación.
 */
enum class SignatureMode(
    val title: String,
    val headline: String,
    val description: String,
) {
    COMMON(
        title = "Firma digital común",
        headline = "Tablet + captura rápida",
        description = "Sincroniza una tablet, captura la firma del usuario y aplica el documento. " +
            "Ideal para operaciones presenciales de alto volumen."
    ),
    ADVANCED(
        title = "Firma digital avanzada",
        headline = "Recibo PDF + token físico",
        description = "Genera un recibo en local, aplica la firma con token físico y administra " +
            "los comprobantes firmados y su intercambio con otras estaciones."
    ),
}

/**
 * Destinos de navegación de la aplicación de escritorio.
 */
enum class AppDestination(val label: String, val caption: String) {
    HOME("Inicio", "Panel de control"),
    COMMON("Firma común", "Captura desde tablet"),
    ADVANCED("Firma avanzada", "Recibo + token"),
    RECEIPTS("Recibos firmados", "Almacenados en local"),
    EXCHANGE("Intercambiar recibos", "Traspaso entre PC"),
}

/**
 * Nivel de un evento del registro de operaciones.
 */
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

/**
 * Entrada del registro visual de operaciones.
 */
data class LogEntry(
    val id: Long,
    val time: String,
    val message: String,
    val level: LogLevel,
)

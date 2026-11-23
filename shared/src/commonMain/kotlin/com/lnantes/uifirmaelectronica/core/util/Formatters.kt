package com.lnantes.uifirmaelectronica.core.util

/**
 * Formatea una marca de tiempo a HH:mm:ss de forma multiplataforma.
 */
fun formatClock(timestampMillis: Long): String {
    val totalSeconds = (timestampMillis / 1000) % 86_400
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return listOf(hours, minutes, seconds).joinToString(":") { it.toString().padStart(2, '0') }
}

/**
 * Formatea una marca de tiempo a dd/MM/yyyy HH:mm de forma multiplataforma.
 * Utiliza el algoritmo civil de conversión de días desde el epoch.
 */
fun formatDateTime(timestampMillis: Long): String {
    val days = timestampMillis / 86_400_000
    val secondsOfDay = (timestampMillis / 1000) % 86_400

    val z = days + 719_468
    val era = if (z >= 0) z / 146_097 else (z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val year = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = mp + if (mp < 10) 3 else -9
    val adjustedYear = year + if (month <= 2) 1 else 0

    val hour = secondsOfDay / 3600
    val minute = (secondsOfDay % 3600) / 60
    return "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$adjustedYear " +
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

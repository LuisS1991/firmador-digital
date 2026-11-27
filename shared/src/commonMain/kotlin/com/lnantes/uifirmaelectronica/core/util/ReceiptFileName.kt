package com.lnantes.uifirmaelectronica.core.util

/**
 * Nombres de los recibos generados en local.
 *
 * Formato: `recibo_<cedula>_<fecha>_<secuencia>[_sufijo].pdf`, por ejemplo
 * `recibo_45227453_20261115_01_firmado_avanzada.pdf`. La secuencia permite
 * generar varios recibos para la misma cédula y fecha sin sobrescribirlos.
 */
object ReceiptFileName {

    /** Prefijo común de todos los archivos de una cédula/fecha. */
    fun prefijo(cedula: String, fecha: String): String = "recibo_${cedula}_${fecha}_"

    /** Nombre base (sin sufijo de firma ni extensión). */
    fun base(cedula: String, fecha: String, secuencia: Int): String =
        prefijo(cedula, fecha) + secuencia.toString().padStart(2, '0')

    /** Nombre final con extensión; [sufijo] puede ser `firmado_comun` o `firmado_avanzada`. */
    fun nombre(baseSinExtension: String, sufijo: String? = null): String =
        baseSinExtension + (sufijo?.let { "_$it" }.orEmpty()) + ".pdf"

    /**
     * Limpia la cédula para usarla en un nombre de archivo. Si no hay datos,
     * se usa el código del recibo (o `SIN-CEDULA`).
     */
    fun cedula(valor: String?, defecto: String = "SIN-CEDULA"): String {
        val digitos = valor.orEmpty().filter { it.isDigit() }
        // Una cédula tiene 7 u 8 dígitos; con menos, el valor suele ser el
        // código del recibo (6B1E-144A-749B) y conviene conservar sus letras.
        if (digitos.length >= 7) return digitos.take(15)
        val seguro = valor.orEmpty().trim().uppercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
        return seguro.takeIf { it.length >= 3 } ?: defecto
    }

    /**
     * Convierte `2026-11-15`, `15/11/2026` o `20261115` a `20261115`.
     */
    fun fechaCompacta(valor: String?, defecto: String): String {
        val texto = valor.orEmpty().trim()
        val guiones = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(texto)
        if (guiones != null) {
            val (anio, mes, dia) = guiones.destructured
            return anio + mes.padStart(2, '0') + dia.padStart(2, '0')
        }
        val barras = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").find(texto)
        if (barras != null) {
            val (dia, mes, anio) = barras.destructured
            return anio + mes.padStart(2, '0') + dia.padStart(2, '0')
        }
        val digitos = texto.filter { it.isDigit() }
        return digitos.takeIf { it.length == 8 } ?: defecto
    }
}

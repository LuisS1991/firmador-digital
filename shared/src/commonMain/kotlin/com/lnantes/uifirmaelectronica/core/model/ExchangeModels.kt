package com.lnantes.uifirmaelectronica.core.model

/**
 * Estación remota con la que se pueden intercambiar recibos.
 */
data class RemoteStation(
    val id: String,
    val name: String,
    val ip: String,
    val online: Boolean = true,
    val lastSeen: String = "",
    val manual: Boolean = false,
) {
    val label: String get() = if (ip.isBlank()) name else "$name · $ip"
}

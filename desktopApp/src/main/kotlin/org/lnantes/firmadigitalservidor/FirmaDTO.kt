package org.lnantes.firmadigitalservidor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PuntoVector(
    val x: Double,
    val y: Double
)

/**
 * Consentimiento otorgado en el celular. NO se persiste en el móvil: viaja
 * adjunto a los trazos o a la respuesta del PIN y se guarda en SQLite aquí.
 */
@Serializable
data class ConsentimientoFirma(
    val otorgado: Boolean = false,
    val tipo: String = "comun",
    val fecha: Long = 0L,
    @SerialName("normativa_notificada") val normativaNotificada: Boolean = false,
    @SerialName("normativa_id") val normativaId: String = "",
    @SerialName("id_solicitud") val idSolicitud: String? = null
)

@Serializable
data class ContenidoTextoFirma(
    val tipo: String = "TRAZO_FIRMA_VECTORIAL",
    val timestamp: Long,
    val strokes: List<List<PuntoVector>>,
    val consentimiento: ConsentimientoFirma? = null,
    /** Tamaño del canvas del móvil en px. Opcional: los móviles antiguos no lo envían. */
    @SerialName("ancho_area") val anchoArea: Double? = null,
    @SerialName("alto_area") val altoArea: Double? = null
)

@Serializable
data class PayloadFirma(
    val timestamp: String,
    @SerialName("tamano_bytes") val tamanoBytes: Int,
    @SerialName("contenido_texto") val contenidoTexto: ContenidoTextoFirma,
    @SerialName("firma_base64") val firmaBase64: String
)

@Serializable
data class RequestFirmaRecibo(
    val codigo: String,
    val payload: PayloadFirma,
    @SerialName("firma_guardada") val firmaGuardada: Int = 1,
    /** Token recibido en `firmaapp://firmar?id=...&token=...`; lo exige Laravel. */
    val token: String? = null,
    /** Nombre del firmante que capturó los trazos (quien recibe o el operador). */
    val firmante: String? = null,
    /** Columna del recibo firmada: ENTREGA o RECIBE. */
    val lado: String? = null
)
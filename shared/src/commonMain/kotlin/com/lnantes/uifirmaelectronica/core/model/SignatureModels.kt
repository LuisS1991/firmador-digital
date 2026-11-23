package com.lnantes.uifirmaelectronica.core.model

/**
 * Tablet detectada para la captura de firma.
 */
data class TabletDevice(
    val id: String,
    val name: String,
    val model: String,
    val batteryPercent: Int,
    val connectedAt: String,
)

/**
 * Firma capturada en el dispositivo.
 */
data class CapturedSignature(
    val id: String,
    val signerName: String,
    val capturedAt: String,
    val strokeCount: Int,
)

/**
 * Documento pendiente de firma.
 */
data class DocumentInfo(
    val name: String,
    val pages: Int,
    val sizeKb: Int,
    val reference: String,
)

/**
 * Resultado de firmar un documento con firma común.
 */
data class SignedDocument(
    val document: DocumentInfo,
    val signature: CapturedSignature,
    val signedAt: String,
    val hash: String,
)

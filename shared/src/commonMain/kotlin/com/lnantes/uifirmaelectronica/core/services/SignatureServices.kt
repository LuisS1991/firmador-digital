package com.lnantes.uifirmaelectronica.core.services

import com.lnantes.uifirmaelectronica.core.model.CapturedSignature
import com.lnantes.uifirmaelectronica.core.model.DocumentInfo
import com.lnantes.uifirmaelectronica.core.model.ExchangeResult
import com.lnantes.uifirmaelectronica.core.model.LadoFirma
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.RemoteStation
import com.lnantes.uifirmaelectronica.core.model.SignedDocument
import com.lnantes.uifirmaelectronica.core.model.TabletDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * Conexión y sincronización con la tablet de captura.
 * El backend decide el protocolo (USB, red, WebSocket, etc.).
 */
interface TabletSyncService {
    suspend fun detectTablet(): Result<TabletDevice>

    /** Reintenta el enlace después de una desconexión AOA sin recrear la app. */
    suspend fun reconectar(): Result<TabletDevice> = detectTablet()

    suspend fun synchronize(
        device: TabletDevice,
        onProgress: (String) -> Unit,
    ): Result<Unit>
}

/**
 * Captura de la firma manuscrita en el dispositivo.
 */
interface SignatureCaptureService {
    suspend fun capture(onProgress: (String) -> Unit): Result<CapturedSignature>
}

/**
 * Aplicación de la firma común sobre el documento.
 * [lado] define la columna del recibo; por defecto Recibe para compatibilidad.
 */
interface DocumentSigningService {
    fun currentDocument(): DocumentInfo
    suspend fun sign(
        document: DocumentInfo,
        signature: CapturedSignature,
        lado: LadoFirma = LadoFirma.RECIBE,
        onProgress: (String) -> Unit,
    ): Result<SignedDocument>
}

/**
 * Generación y firma avanzada de recibos mediante token físico.
 * [receipts] permite a la UI reaccionar en tiempo real a cambios en local.
 */
interface ReceiptService {
    val receipts: StateFlow<List<Receipt>>
    suspend fun generateReceipt(onProgress: (String) -> Unit): Result<Receipt>

    /**
     * Genera el recibo para un código concreto (deep link o campo editable).
     * La implementación por defecto mantiene compatibilidad con los servicios
     * de demo que no conocen códigos reales.
     */
    suspend fun generateReceipt(
        code: String?,
        onProgress: (String) -> Unit,
    ): Result<Receipt> = generateReceipt(onProgress)

    /**
     * Firma con cédula/token. [lado] elige la columna y [firmante] el nombre que
     * se muestra en la firma visible (por defecto, el de esa columna).
     */
    suspend fun signWithToken(
        receipt: Receipt,
        lado: LadoFirma = LadoFirma.ENTREGA,
        firmante: String? = null,
        onProgress: (String) -> Unit,
    ): Result<Receipt>

    /**
     * Borra el PDF base y los intermedios una vez guardado el archivo final.
     * No elimina [archivoFinal].
     */
    suspend fun limpiarTemporales(
        codigo: String,
        archivoFinal: String?,
        onProgress: (String) -> Unit = {},
    ): Result<Unit> = Result.success(Unit)
}

/**
 * Exportación/importación de recibos con otras estaciones de trabajo.
 * La UI permite elegir la estación (nombre + IP) y qué recibos se transfieren.
 */
interface ReceiptExchangeService {
    /** Descubre estaciones disponibles en la red local. */
    suspend fun discoverStations(onProgress: (String) -> Unit): Result<List<RemoteStation>>

    /** Sincroniza la estación elegida antes de consultar o transferir recibos. */
    suspend fun synchronize(
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<RemoteStation>

    /** Recibos disponibles en la estación remota seleccionada. */
    suspend fun listRemoteReceipts(
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<List<Receipt>>

    suspend fun exportReceipts(
        receipts: List<Receipt>,
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<ExchangeResult>

    suspend fun importReceipts(
        receipts: List<Receipt>,
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<ExchangeResult>
}

/**
 * Contenedor único de dependencias. Sustituye la implementación de demo por
 * la real en [com.lnantes.uifirmaelectronica.App] cuando conectes tu backend.
 */
interface SignatureServices {
    val tabletSync: TabletSyncService
    val capture: SignatureCaptureService
    val signing: DocumentSigningService
    val receipts: ReceiptService
    val exchange: ReceiptExchangeService
}

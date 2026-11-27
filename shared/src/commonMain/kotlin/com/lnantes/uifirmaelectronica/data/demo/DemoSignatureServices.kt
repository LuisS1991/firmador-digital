package com.lnantes.uifirmaelectronica.data.demo

import com.lnantes.uifirmaelectronica.core.model.CapturedSignature
import com.lnantes.uifirmaelectronica.core.model.DocumentInfo
import com.lnantes.uifirmaelectronica.core.model.ExchangeOperation
import com.lnantes.uifirmaelectronica.core.model.ExchangeResult
import com.lnantes.uifirmaelectronica.core.model.LadoFirma
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.ReceiptStatus
import com.lnantes.uifirmaelectronica.core.model.RemoteStation
import com.lnantes.uifirmaelectronica.core.model.SignedDocument
import com.lnantes.uifirmaelectronica.core.model.TabletDevice
import com.lnantes.uifirmaelectronica.core.services.DocumentSigningService
import com.lnantes.uifirmaelectronica.core.services.ReceiptExchangeService
import com.lnantes.uifirmaelectronica.core.services.ReceiptService
import com.lnantes.uifirmaelectronica.core.services.SignatureCaptureService
import com.lnantes.uifirmaelectronica.core.services.SignatureServices
import com.lnantes.uifirmaelectronica.core.services.TabletSyncService
import com.lnantes.uifirmaelectronica.core.util.currentTimeMillis
import com.lnantes.uifirmaelectronica.core.util.formatDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Implementaciones simuladas: generan eventos con retardos para que cada
 * interacción tenga su feedback visual. NO contiene lógica de negocio real.
 */
class DemoSignatureServices : SignatureServices {
    private val receiptStore = DemoReceiptService()

    override val tabletSync: TabletSyncService = DemoTabletSyncService()
    override val capture: SignatureCaptureService = DemoSignatureCaptureService()
    override val signing: DocumentSigningService = DemoDocumentSigningService()
    override val receipts: ReceiptService = receiptStore
    override val exchange: ReceiptExchangeService = DemoReceiptExchangeService(receiptStore)
}

private class DemoTabletSyncService : TabletSyncService {
    override suspend fun detectTablet(): Result<TabletDevice> = runCatching {
        delay(450)
        TabletDevice(
            id = "TBL-2048-AX",
            name = "Tableta de captura · Recepción",
            model = "TabSign 10 Pro",
            batteryPercent = 78,
            connectedAt = formatDateTime(currentTime()),
        )
    }

    override suspend fun synchronize(
        device: TabletDevice,
        onProgress: (String) -> Unit,
    ): Result<Unit> = runCatching {
        onProgress("Estableciendo enlace seguro con ${device.name}…")
        delay(650)
        onProgress("Intercambiando claves de sesión…")
        delay(600)
        onProgress("Sincronización correcta · superficie de firma lista")
        delay(350)
    }
}

private class DemoSignatureCaptureService : SignatureCaptureService {
    override suspend fun capture(onProgress: (String) -> Unit): Result<CapturedSignature> = runCatching {
        onProgress("Solicitando trazo al dispositivo…")
        delay(700)
        onProgress("Firma capturada correctamente")
        delay(300)
        CapturedSignature(
            id = "SIG-${randomSuffix()}",
            signerName = "María Elena Rivas",
            capturedAt = formatDateTime(currentTime()),
            strokeCount = 7,
        )
    }
}

private class DemoDocumentSigningService : DocumentSigningService {
    private val document = DocumentInfo(
        name = "Contrato_de_prestacion_2026.pdf",
        pages = 4,
        sizeKb = 486,
        reference = "DOC-2026-1184",
    )

    override fun currentDocument(): DocumentInfo = document

    override suspend fun sign(
        document: DocumentInfo,
        signature: CapturedSignature,
        lado: LadoFirma,
        onProgress: (String) -> Unit,
    ): Result<SignedDocument> = runCatching {
        onProgress("Preparando documento y calculando huella…")
        delay(600)
        onProgress("Insertando imagen de firma en ${lado.etiqueta}…")
        delay(650)
        onProgress("Enviando datos al repositorio local…")
        delay(700)
        onProgress("Documento firmado y sellado correctamente")
        SignedDocument(
            document = document,
            signature = signature,
            signedAt = formatDateTime(currentTime()),
            hash = "SHA-256 · ${randomSuffix()}${randomSuffix()}",
        )
    }
}

private class DemoReceiptService : ReceiptService {
    private val _receipts = MutableStateFlow(
        listOf(
            Receipt(
                id = "R-1021",
                number = "REC-2026-01021",
                concept = "Servicio técnico · Visita 12",
                amount = "148,50 €",
                signer = "J. A. Morales",
                issuedAt = "20/11/2026 10:24",
                fileName = "REC-2026-01021.pdf",
                status = ReceiptStatus.SIGNED,
                signedAt = "20/11/2026 10:31",
                tokenSerial = "A3B9-77C1",
            ),
            Receipt(
                id = "R-1020",
                number = "REC-2026-01020",
                concept = "Renovación de licencia",
                amount = "89,00 €",
                signer = "L. Fernández",
                issuedAt = "19/11/2026 16:02",
                fileName = "REC-2026-01020.pdf",
                status = ReceiptStatus.SIGNED,
                signedAt = "19/11/2026 16:08",
                tokenSerial = "A3B9-77C1",
            ),
            Receipt(
                id = "R-1019",
                number = "REC-2026-01019",
                concept = "Suministro de material",
                amount = "234,80 €",
                signer = "C. Navarro",
                issuedAt = "18/11/2026 09:15",
                fileName = "REC-2026-01019.pdf",
                status = ReceiptStatus.SIGNED,
                signedAt = "18/11/2026 09:22",
                tokenSerial = "A3B9-77C1",
            ),
        )
    )
    override val receipts: StateFlow<List<Receipt>> = _receipts.asStateFlow()

    override suspend fun generateReceipt(onProgress: (String) -> Unit): Result<Receipt> = runCatching {
        onProgress("Recopilando datos del recibo…")
        delay(500)
        onProgress("Maquetando documento PDF…")
        delay(650)
        onProgress("Guardando recibo en almacenamiento local…")
        delay(550)
        val sequence = 1022 + _receipts.value.size - 3
        val receipt = Receipt(
            id = "R-$sequence",
            number = "REC-2026-0$sequence",
            concept = "Nuevo recibo · Servicio presencial",
            amount = "75,00 €",
            signer = "María Elena Rivas",
            issuedAt = formatDateTime(currentTime()),
            fileName = "REC-2026-0$sequence.pdf",
            status = ReceiptStatus.PENDING,
        )
        _receipts.value = listOf(receipt) + _receipts.value
        onProgress("Recibo ${receipt.number} generado correctamente")
        receipt
    }

    override suspend fun signWithToken(
        receipt: Receipt,
        lado: LadoFirma,
        firmante: String?,
        onProgress: (String) -> Unit,
    ): Result<Receipt> = runCatching {
        onProgress("Detectando token físico para ${lado.etiqueta}…")
        delay(600)
        onProgress("Token A3B9-77C1 conectado · verificando PIN…")
        delay(700)
        onProgress("Generando firma avanzada con certificado del token…")
        delay(750)
        onProgress("Recibo firmado y archivado en local")
        val signed = receipt.copy(
            status = ReceiptStatus.SIGNED,
            signedAt = formatDateTime(currentTime()),
            tokenSerial = "A3B9-77C1",
        )
        _receipts.value = _receipts.value.map { if (it.id == receipt.id) signed else it }
        signed
    }

    /** Archiva en local los recibos procedentes de otra estación. */
    fun addImported(imported: List<Receipt>) {
        val entries = imported.map {
            it.copy(
                id = "IMP-${randomSuffix()}-${it.id}",
                status = ReceiptStatus.SIGNED,
                signedAt = it.signedAt ?: formatDateTime(currentTime()),
            )
        }
        _receipts.value = entries + _receipts.value
    }
}

private class DemoReceiptExchangeService(
    private val store: DemoReceiptService,
) : ReceiptExchangeService {
    private val stations = listOf(
        RemoteStation("ST-02", "PC-Recepción-02", "192.168.1.42", true, "hace 2 min"),
        RemoteStation("ST-77", "PC-Almacén-01", "192.168.1.77", true, "hace 8 min"),
        RemoteStation("ST-15", "PC-Dirección", "10.0.0.15", false, "hace 3 h"),
    )

    private val remoteReceipts = mapOf(
        "ST-02" to listOf(
            Receipt(
                id = "REM-2201", number = "REC-2026-02201", concept = "Instalación en Cliente Norte",
                amount = "312,00 €", signer = "R. Salas", issuedAt = "22/11/2026 11:40",
                fileName = "REC-2026-02201.pdf", status = ReceiptStatus.SIGNED,
                signedAt = "22/11/2026 11:46", tokenSerial = "B7C1-20A9",
            ),
            Receipt(
                id = "REM-2202", number = "REC-2026-02202", concept = "Mantenimiento preventivo",
                amount = "64,20 €", signer = "M. Torres", issuedAt = "22/11/2026 13:05",
                fileName = "REC-2026-02202.pdf", status = ReceiptStatus.SIGNED,
                signedAt = "22/11/2026 13:11", tokenSerial = "B7C1-20A9",
            ),
            Receipt(
                id = "REM-2203", number = "REC-2026-02203", concept = "Repuesto de impresora",
                amount = "47,90 €", signer = "A. Ruiz", issuedAt = "21/11/2026 17:22",
                fileName = "REC-2026-02203.pdf", status = ReceiptStatus.SIGNED,
                signedAt = "21/11/2026 17:29", tokenSerial = "B7C1-20A9",
            ),
        ),
        "ST-77" to listOf(
            Receipt(
                id = "REM-3310", number = "REC-2026-03310", concept = "Entrada de material · Lote 44",
                amount = "980,00 €", signer = "S. Gil", issuedAt = "19/11/2026 08:30",
                fileName = "REC-2026-03310.pdf", status = ReceiptStatus.SIGNED,
                signedAt = "19/11/2026 08:41", tokenSerial = "D1E4-5566",
            ),
            Receipt(
                id = "REM-3311", number = "REC-2026-03311", concept = "Devolución de pedido",
                amount = "25,50 €", signer = "S. Gil", issuedAt = "18/11/2026 15:12",
                fileName = "REC-2026-03311.pdf", status = ReceiptStatus.SIGNED,
                signedAt = "18/11/2026 15:19", tokenSerial = "D1E4-5566",
            ),
        ),
        "ST-15" to emptyList(),
    )

    override suspend fun discoverStations(onProgress: (String) -> Unit): Result<List<RemoteStation>> = runCatching {
        onProgress("Buscando estaciones en la red local…")
        delay(700)
        onProgress("Escaneando rango 192.168.1.0/24…")
        delay(650)
        val online = stations.count { it.online }
        onProgress("$online estaciones disponibles para intercambio")
        stations
    }

    override suspend fun synchronize(
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<RemoteStation> = runCatching {
        onProgress("Estableciendo enlace con ${station.name} (${station.ip})…")
        delay(700)
        onProgress("Verificando identidad y certificado de la estación…")
        delay(650)
        onProgress("Sincronizando catálogo de recibos…")
        delay(600)
        onProgress("Sincronización correcta · terminal lista para intercambiar")
        station.copy(lastSeen = "Sincronizada ahora")
    }

    override suspend fun listRemoteReceipts(
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<List<Receipt>> = runCatching {
        onProgress("Consultando recibos en ${station.name}…")
        delay(650)
        val list = remoteReceipts[station.id].orEmpty()
        onProgress("${list.size} recibos disponibles en ${station.ip}")
        list
    }

    override suspend fun exportReceipts(
        receipts: List<Receipt>,
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<ExchangeResult> = runCatching {
        onProgress("Preparando ${receipts.size} recibos seleccionados…")
        delay(550)
        onProgress("Enviando a ${station.name} (${station.ip})…")
        delay(700)
        onProgress("Paquete aceptado por la estación remota")
        ExchangeResult(
            operation = ExchangeOperation.EXPORT,
            processed = receipts.size,
            detail = "recibos_firmados_${randomSuffix()}.zip → ${station.ip}",
            completedAt = formatDateTime(currentTime()),
        )
    }

    override suspend fun importReceipts(
        receipts: List<Receipt>,
        station: RemoteStation,
        onProgress: (String) -> Unit,
    ): Result<ExchangeResult> = runCatching {
        onProgress("Conectando con ${station.name} (${station.ip})…")
        delay(600)
        onProgress("Solicitando ${receipts.size} recibos seleccionados…")
        delay(650)
        onProgress("Validando firmas de los recibos…")
        delay(650)
        store.addImported(receipts)
        onProgress("Importación completada y archivada en local")
        ExchangeResult(
            operation = ExchangeOperation.IMPORT,
            processed = receipts.size,
            detail = "lote_recibos_${randomSuffix()}.zip ← ${station.ip}",
            completedAt = formatDateTime(currentTime()),
        )
    }
}

private fun randomSuffix(): String = Random.nextInt(1000, 9999).toString()
private fun currentTime(): Long = currentTimeMillis()

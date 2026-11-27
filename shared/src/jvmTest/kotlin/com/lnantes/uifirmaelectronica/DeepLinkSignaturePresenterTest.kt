package com.lnantes.uifirmaelectronica

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
import com.lnantes.uifirmaelectronica.ui.presentation.DeepLinkPhase
import com.lnantes.uifirmaelectronica.ui.presentation.DeepLinkSignaturePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.DeepLinkUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pruebas del modo enlace profundo: genera el recibo temporal, firman quien
 * recibe y el operador (cada uno con la modalidad elegida) y se borran los PDF
 * intermedios. Sin hardware ni red: todo está simulado.
 */
class DeepLinkSignaturePresenterTest {

    private val codigo = "6B1E-144A-749B"
    private val base = "recibo_$codigo.pdf"
    private val receptor = "recibo_${codigo}_firmado_recibe_comun.pdf"
    private val operador = "recibo_${codigo}_firmado_entrega_avanzada.pdf"

    @Test
    fun `firma primero quien recibe y despues el operador y limpia temporales`() = runBlocking {
        val servicios = FakeServices(codigo, base, receptor, operador)
        val presenter = DeepLinkSignaturePresenter(this, servicios, codigo, tokenDisponible = true)

        esperar { presenter.state.value.phase == DeepLinkPhase.LISTO }
        assertEquals(codigo, servicios.codigoSolicitado)
        assertEquals(base, presenter.state.value.archivoBase)

        presenter.onEvent(DeepLinkUiEvent.Firmar)
        esperar { presenter.state.value.phase == DeepLinkPhase.FINALIZADO }

        val estado = presenter.state.value
        assertEquals(receptor, estado.archivoReceptor)
        assertEquals(operador, estado.archivoOperador)
        assertEquals(operador, estado.archivoFinal)
        assertTrue(estado.temporalesEliminados)
        assertEquals(
            listOf(
                "generar:$codigo",
                "sincronizar",
                "capturar",
                "firmar_comun:RECIBE",
                "firmar_token:ENTREGA:$receptor",
                "limpiar:$operador",
            ),
            servicios.rutinas,
        )
        assertFalse(estado.toString().contains("token-secreto"))
        assertTrue(estado.tokenDisponible)
    }

    @Test
    fun `cada firmante elige su modalidad y la avanzada se aplica al final`() = runBlocking {
        val servicios = FakeServices(codigo, base, receptor, operador)
        val presenter = DeepLinkSignaturePresenter(this, servicios, codigo, tokenDisponible = true)

        esperar { presenter.state.value.phase == DeepLinkPhase.LISTO }
        presenter.onEvent(DeepLinkUiEvent.SeleccionarMetodoReceptor(
            com.lnantes.uifirmaelectronica.core.model.MetodoFirma.AVANZADA
        ))
        presenter.onEvent(DeepLinkUiEvent.SeleccionarMetodoOperador(
            com.lnantes.uifirmaelectronica.core.model.MetodoFirma.COMUN
        ))
        presenter.onEvent(DeepLinkUiEvent.Firmar)
        esperar { presenter.state.value.phase == DeepLinkPhase.FINALIZADO }

        val indiceAvanzadaReceptor = servicios.rutinas.indexOfFirst { it.startsWith("firmar_token:RECIBE:") }
        assertTrue(indiceAvanzadaReceptor >= 0)
        assertTrue(servicios.rutinas.indexOf("firmar_comun:ENTREGA") < indiceAvanzadaReceptor)
    }

    @Test
    fun `permite reintentar la conexion AOA antes de firmar`() = runBlocking {
        val servicios = FakeServices(codigo, base, receptor, operador).apply {
            fallarPrimeraConexion = true
        }
        val presenter = DeepLinkSignaturePresenter(this, servicios, codigo, tokenDisponible = true)

        esperar { presenter.state.value.phase == DeepLinkPhase.LISTO }
        presenter.onEvent(DeepLinkUiEvent.Firmar)
        esperar { presenter.state.value.conexionAoaFallida }

        presenter.onEvent(DeepLinkUiEvent.ReintentarConexion)
        esperar { !presenter.state.value.conexionAoaFallida && !presenter.state.value.busy }
        assertEquals("reconectar", servicios.rutinas.first { it == "reconectar" })

        presenter.onEvent(DeepLinkUiEvent.Firmar)
        esperar { presenter.state.value.phase == DeepLinkPhase.FINALIZADO }
        assertTrue(presenter.state.value.temporalesEliminados)
    }

    @Test
    fun `no permite firmar dos veces el mismo recibo`() = runBlocking {
        val servicios = FakeServices(codigo, base, receptor, operador)
        val presenter = DeepLinkSignaturePresenter(this, servicios, codigo, tokenDisponible = true)

        esperar { presenter.state.value.phase == DeepLinkPhase.LISTO }
        presenter.onEvent(DeepLinkUiEvent.Firmar)
        esperar { presenter.state.value.phase == DeepLinkPhase.FINALIZADO }

        val rutinasDespuesDeFirmar = servicios.rutinas.toList()
        presenter.onEvent(DeepLinkUiEvent.Firmar)
        delay(50)

        assertFalse(presenter.state.value.puedeFirmar)
        assertEquals(rutinasDespuesDeFirmar, servicios.rutinas)
    }

    private suspend fun CoroutineScope.esperar(condicion: () -> Boolean) {
        withTimeout(5_000) {
            while (!condicion()) delay(10)
        }
    }

    private class FakeServices(
        private val codigo: String,
        private val base: String,
        private val comun: String,
        private val avanzada: String,
    ) : SignatureServices {
        val rutinas = mutableListOf<String>()
        var codigoSolicitado: String? = null
        var fallarPrimeraConexion = false
        private var intentosConexion = 0

        private val _receipts = MutableStateFlow<List<Receipt>>(emptyList())

        override val tabletSync = object : TabletSyncService {
            override suspend fun detectTablet(): Result<TabletDevice> {
                if (fallarPrimeraConexion && intentosConexion++ == 0) {
                    return Result.failure(IllegalStateException("Móvil no conectado"))
                }
                return Result.success(TabletDevice("AOA-1", "Móvil", "Android", 100, "ahora"))
            }

            override suspend fun reconectar(): Result<TabletDevice> {
                rutinas += "reconectar"
                return detectTablet()
            }

            override suspend fun synchronize(
                device: TabletDevice,
                onProgress: (String) -> Unit,
            ): Result<Unit> {
                rutinas += "sincronizar"
                return Result.success(Unit)
            }
        }

        override val capture = object : SignatureCaptureService {
            override suspend fun capture(onProgress: (String) -> Unit): Result<CapturedSignature> {
                rutinas += "capturar"
                return Result.success(CapturedSignature("SIG-1", "Firmante", "ahora", 3))
            }
        }

        override val signing = object : DocumentSigningService {
            override fun currentDocument(): DocumentInfo = DocumentInfo(base, 1, 10, codigo)

            override suspend fun sign(
                document: DocumentInfo,
                signature: CapturedSignature,
                lado: LadoFirma,
                onProgress: (String) -> Unit,
            ): Result<SignedDocument> {
                rutinas += "firmar_comun:${lado.name}"
                return Result.success(
                    SignedDocument(
                        document = document.copy(name = comun),
                        signature = signature,
                        signedAt = "ahora",
                        hash = "SHA-256 · prueba",
                    )
                )
            }
        }

        override val receipts = object : ReceiptService {
            override val receipts: StateFlow<List<Receipt>> = _receipts

            override suspend fun generateReceipt(onProgress: (String) -> Unit): Result<Receipt> =
                generateReceipt(codigo, onProgress)

            override suspend fun generateReceipt(
                code: String?,
                onProgress: (String) -> Unit,
            ): Result<Receipt> {
                codigoSolicitado = code
                rutinas += "generar:$code"
                val recibo = Receipt(
                    id = "R-1",
                    number = code ?: codigo,
                    concept = "Devolución",
                    amount = "—",
                    signer = "LUIS DANIEL NANTES SILVA",
                    entregador = "ADRIAN ALMADA",
                    issuedAt = "15/11/2026",
                    fileName = base,
                    status = ReceiptStatus.PENDING,
                )
                _receipts.value = listOf(recibo)
                return Result.success(recibo)
            }

            override suspend fun signWithToken(
                receipt: Receipt,
                lado: LadoFirma,
                firmante: String?,
                onProgress: (String) -> Unit,
            ): Result<Receipt> {
                rutinas += "firmar_token:${lado.name}:${receipt.fileName}"
                val firmado = receipt.copy(
                    fileName = if (lado == LadoFirma.ENTREGA) avanzada else comun,
                    status = ReceiptStatus.SIGNED,
                    signedAt = "ahora",
                    tokenSerial = "TOKEN",
                )
                _receipts.value = listOf(firmado)
                return Result.success(firmado)
            }

            override suspend fun limpiarTemporales(
                codigo: String,
                archivoFinal: String?,
                onProgress: (String) -> Unit,
            ): Result<Unit> {
                rutinas += "limpiar:$archivoFinal"
                return Result.success(Unit)
            }
        }

        override val exchange = object : ReceiptExchangeService {
            override suspend fun discoverStations(onProgress: (String) -> Unit) = Result.success(emptyList<RemoteStation>())
            override suspend fun synchronize(station: RemoteStation, onProgress: (String) -> Unit) = Result.success(station)
            override suspend fun listRemoteReceipts(station: RemoteStation, onProgress: (String) -> Unit) = Result.success(emptyList<Receipt>())
            override suspend fun exportReceipts(receipts: List<Receipt>, station: RemoteStation, onProgress: (String) -> Unit) =
                Result.success(ExchangeResult(ExchangeOperation.EXPORT, 0, "", "ahora"))
            override suspend fun importReceipts(receipts: List<Receipt>, station: RemoteStation, onProgress: (String) -> Unit) =
                Result.success(ExchangeResult(ExchangeOperation.IMPORT, 0, "", "ahora"))
        }
    }
}

package com.lnantes.uifirmaelectronica.ui.presentation

import com.lnantes.uifirmaelectronica.core.model.CapturedSignature
import com.lnantes.uifirmaelectronica.core.model.LogEntry
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.SignedDocument
import com.lnantes.uifirmaelectronica.core.model.TabletDevice
import com.lnantes.uifirmaelectronica.core.services.SignatureServices
import com.lnantes.uifirmaelectronica.core.util.currentTimeMillis
import com.lnantes.uifirmaelectronica.core.util.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommonPhase {
    IDLE, CONNECTING, SYNCING, SYNCED, CAPTURING, READY, SIGNING, SIGNED
}

data class CommonSignatureUiState(
    val phase: CommonPhase = CommonPhase.IDLE,
    val device: TabletDevice? = null,
    val signature: CapturedSignature? = null,
    val signedDocument: SignedDocument? = null,
    val log: List<LogEntry> = emptyList(),
    val previewVisible: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

sealed interface CommonUiEvent {
    data object SyncTablet : CommonUiEvent
    data object CaptureSignature : CommonUiEvent
    data object OpenPreview : CommonUiEvent
    data object ClosePreview : CommonUiEvent
    data object SignDocument : CommonUiEvent
    data object ClearLog : CommonUiEvent
    data object Reset : CommonUiEvent
}

/**
 * Orquesta el flujo de firma común. No conoce Compose: sólo expone estado y
 * recibe eventos, por lo que es fácilmente testeable con servicios falsos.
 */
class CommonSignaturePresenter(
    private val scope: CoroutineScope,
    private val services: SignatureServices,
) {
    private val _state = MutableStateFlow(CommonSignatureUiState())
    val state: StateFlow<CommonSignatureUiState> = _state.asStateFlow()
    private var logSequence = 0L

    fun onEvent(event: CommonUiEvent) {
        when (event) {
            CommonUiEvent.SyncTablet -> syncTablet()
            CommonUiEvent.CaptureSignature -> captureSignature()
            CommonUiEvent.OpenPreview -> _state.update { it.copy(previewVisible = true) }
            CommonUiEvent.ClosePreview -> _state.update { it.copy(previewVisible = false) }
            CommonUiEvent.SignDocument -> signDocument()
            CommonUiEvent.ClearLog -> _state.update { it.copy(log = emptyList()) }
            CommonUiEvent.Reset -> {
                logSequence = 0
                _state.value = CommonSignatureUiState()
                log("Flujo reiniciado")
            }
        }
    }

    private fun syncTablet() = scope.launch {
        _state.update { it.copy(phase = CommonPhase.CONNECTING, busy = true, error = null, signedDocument = null) }
        log("Buscando tablet de captura disponible…")

        services.tabletSync.detectTablet().fold(
            onSuccess = { device ->
                _state.update { it.copy(device = device, phase = CommonPhase.SYNCING) }
                log("Tablet detectada: ${device.name} · batería ${device.batteryPercent}%", LogLevel.SUCCESS)

                services.tabletSync.synchronize(device) { message -> log(message) }.fold(
                    onSuccess = {
                        _state.update { it.copy(phase = CommonPhase.SYNCED, busy = false) }
                        log("Ahora puede capturar la firma", LogLevel.SUCCESS)
                    },
                    onFailure = { error -> fail("Error de sincronización", error) },
                )
            },
            onFailure = { error -> fail("No se detectó ninguna tablet", error) },
        )
    }

    private fun captureSignature() = scope.launch {
        _state.update { it.copy(phase = CommonPhase.CAPTURING, busy = true, error = null, signature = null) }
        log("Solicitando captura de firma…")

        services.capture.capture { message -> log(message) }.fold(
            onSuccess = { signature ->
                _state.update {
                    it.copy(
                        signature = signature,
                        phase = CommonPhase.READY,
                        busy = false,
                        previewVisible = true,
                    )
                }
                log("Firma ${signature.id} lista para revisar", LogLevel.SUCCESS)
            },
            onFailure = { error -> fail("No se pudo capturar la firma", error) },
        )
    }

    private fun signDocument() = scope.launch {
        val signature = _state.value.signature
        if (signature == null) {
            log("Primero sincronice y capture una firma", LogLevel.WARNING)
            return@launch
        }
        val document = services.signing.currentDocument()
        _state.update { it.copy(phase = CommonPhase.SIGNING, busy = true, error = null) }
        log("Firmando ${document.name}…")

        services.signing.sign(document, signature) { message -> log(message) }.fold(
            onSuccess = { signed ->
                _state.update {
                    it.copy(
                        signedDocument = signed,
                        phase = CommonPhase.SIGNED,
                        busy = false,
                    )
                }
                log("Operación finalizada · ${signed.hash}", LogLevel.SUCCESS)
            },
            onFailure = { error -> fail("No se pudo firmar el documento", error) },
        )
    }

    private fun fail(message: String, error: Throwable) {
        val detail = error.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        _state.update { it.copy(busy = false, error = message + detail) }
        log(message + detail, LogLevel.ERROR)
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _state.update { current ->
            current.copy(
                log = (current.log + LogEntry(++logSequence, formatClock(currentTimeMillis()), message, level)).takeLast(80),
            )
        }
    }
}

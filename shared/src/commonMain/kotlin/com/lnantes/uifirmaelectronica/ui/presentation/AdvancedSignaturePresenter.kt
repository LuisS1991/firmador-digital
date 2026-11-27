package com.lnantes.uifirmaelectronica.ui.presentation

import com.lnantes.uifirmaelectronica.core.model.LogEntry
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.ReceiptStatus
import com.lnantes.uifirmaelectronica.core.services.SignatureServices
import com.lnantes.uifirmaelectronica.core.util.currentTimeMillis
import com.lnantes.uifirmaelectronica.core.util.formatClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdvancedPhase { IDLE, GENERATING, READY, SIGNING, SIGNED }

data class AdvancedSignatureUiState(
    val phase: AdvancedPhase = AdvancedPhase.IDLE,
    val receipt: Receipt? = null,
    val codigo: String = "6B1E-144A-749B",
    val localReceipts: List<Receipt> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

sealed interface AdvancedUiEvent {
    data class CodigoChanged(val value: String) : AdvancedUiEvent
    data object GenerateReceipt : AdvancedUiEvent
    data object SignWithToken : AdvancedUiEvent
    data object Reset : AdvancedUiEvent
    data object ClearLog : AdvancedUiEvent
}

/**
 * Flujo de firma avanzada: generación de recibo local y firma con token físico.
 */
class AdvancedSignaturePresenter(
    private val scope: CoroutineScope,
    private val services: SignatureServices,
    private val codigoInicial: String = "6B1E-144A-749B",
) {
    private val _state = MutableStateFlow(AdvancedSignatureUiState(codigo = codigoInicial))
    val state: StateFlow<AdvancedSignatureUiState> = _state.asStateFlow()
    private var logSequence = 0L
    private var operationJob: Job? = null

    init {
        scope.launch {
            services.receipts.receipts.collect { list ->
                _state.update { it.copy(localReceipts = list) }
            }
        }
    }

    fun onEvent(event: AdvancedUiEvent) {
        when (event) {
            is AdvancedUiEvent.CodigoChanged -> _state.update {
                it.copy(codigo = event.value.uppercase().take(14))
            }
            AdvancedUiEvent.GenerateReceipt -> generateReceipt()
            AdvancedUiEvent.SignWithToken -> signWithToken()
            AdvancedUiEvent.Reset -> {
                // Cancela cualquier firma/generación en curso para que no reaparezca
                // el estado de "firmando" después del reinicio.
                operationJob?.cancel()
                operationJob = null
                _state.value = AdvancedSignatureUiState(
                    codigo = _state.value.codigo.ifBlank { codigoInicial },
                    localReceipts = _state.value.localReceipts
                )
                log("Flujo reiniciado")
            }
            AdvancedUiEvent.ClearLog -> _state.update { it.copy(log = emptyList()) }
        }
    }

    private fun generateReceipt() {
        operationJob?.cancel()
        operationJob = scope.launch {
            _state.update { it.copy(phase = AdvancedPhase.GENERATING, busy = true, error = null, receipt = null) }
            val codigo = _state.value.codigo.trim()
            log("Consultando datos del recibo $codigo…")

            services.receipts.generateReceipt(codigo) { message -> log(message) }.fold(
                onSuccess = { receipt ->
                    _state.update { it.copy(receipt = receipt, phase = AdvancedPhase.READY, busy = false) }
                    log("Recibo ${receipt.number} disponible para firma", LogLevel.SUCCESS)
                },
                onFailure = { error -> fail("No se pudo generar el recibo", error) },
            )
        }
    }

    private fun signWithToken() {
        operationJob?.cancel()
        operationJob = scope.launch {
            val receipt = _state.value.receipt
            if (receipt == null) {
                log("Genere un recibo antes de firmar", LogLevel.WARNING)
                return@launch
            }
            if (_state.value.phase == AdvancedPhase.SIGNED || receipt.status == ReceiptStatus.SIGNED) {
                log("Este recibo ya está firmado; genere otro para firmar de nuevo", LogLevel.WARNING)
                return@launch
            }
            _state.update { it.copy(phase = AdvancedPhase.SIGNING, busy = true, error = null) }

            services.receipts.signWithToken(receipt) { message -> log(message) }.fold(
                onSuccess = { signed ->
                    _state.update { it.copy(receipt = signed, phase = AdvancedPhase.SIGNED, busy = false) }
                    log("Recibo archivado con firma avanzada", LogLevel.SUCCESS)
                },
                onFailure = { error -> fail("No se pudo firmar con el token", error) },
            )
        }
    }

    private fun fail(message: String, error: Throwable) {
        // Una cancelación por "Reiniciar flujo" no es un error de la operación.
        if (error is CancellationException) return
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

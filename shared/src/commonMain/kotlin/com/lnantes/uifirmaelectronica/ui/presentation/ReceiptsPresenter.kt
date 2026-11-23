package com.lnantes.uifirmaelectronica.ui.presentation

import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.ReceiptStatus
import com.lnantes.uifirmaelectronica.core.services.SignatureServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReceiptFilter { ALL, SIGNED, PENDING }

data class ReceiptsUiState(
    val receipts: List<Receipt> = emptyList(),
    val query: String = "",
    val filter: ReceiptFilter = ReceiptFilter.ALL,
    val selectedId: String? = null,
    val notice: String? = null,
)

sealed interface ReceiptsUiEvent {
    data class QueryChanged(val value: String) : ReceiptsUiEvent
    data class FilterChanged(val value: ReceiptFilter) : ReceiptsUiEvent
    data class Select(val id: String) : ReceiptsUiEvent
    data object CloseDetail : ReceiptsUiEvent
    data object ViewPdf : ReceiptsUiEvent
    data object ClearNotice : ReceiptsUiEvent
}

/**
 * Vista de recibos firmados almacenados en local, con búsqueda y filtros.
 */
class ReceiptsPresenter(
    private val scope: CoroutineScope,
    private val services: SignatureServices,
) {
    private val _state = MutableStateFlow(ReceiptsUiState())
    val state: StateFlow<ReceiptsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            services.receipts.receipts.collect { list ->
                _state.update { it.copy(receipts = list) }
            }
        }
    }

    fun onEvent(event: ReceiptsUiEvent) {
        when (event) {
            is ReceiptsUiEvent.QueryChanged -> _state.update { it.copy(query = event.value) }
            is ReceiptsUiEvent.FilterChanged -> _state.update { it.copy(filter = event.value) }
            is ReceiptsUiEvent.Select -> _state.update { it.copy(selectedId = event.id) }
            ReceiptsUiEvent.CloseDetail -> _state.update { it.copy(selectedId = null) }
            ReceiptsUiEvent.ViewPdf -> showNotice("Abriendo PDF en el visor local (simulación)")
            ReceiptsUiEvent.ClearNotice -> _state.update { it.copy(notice = null) }
        }
    }

    fun visibleReceipts(state: ReceiptsUiState): List<Receipt> {
        val query = state.query.trim().lowercase()
        return state.receipts.filter { receipt ->
            val matchesFilter = when (state.filter) {
                ReceiptFilter.ALL -> true
                ReceiptFilter.SIGNED -> receipt.status == ReceiptStatus.SIGNED
                ReceiptFilter.PENDING -> receipt.status == ReceiptStatus.PENDING
            }
            val matchesQuery = query.isBlank() ||
                receipt.number.lowercase().contains(query) ||
                receipt.concept.lowercase().contains(query) ||
                receipt.signer.lowercase().contains(query)
            matchesFilter && matchesQuery
        }
    }

    private fun showNotice(message: String) {
        _state.update { it.copy(notice = message) }
        scope.launch {
            delay(3200)
            _state.update { current -> if (current.notice == message) current.copy(notice = null) else current }
        }
    }
}

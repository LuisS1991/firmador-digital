package com.lnantes.uifirmaelectronica.ui.presentation

import com.lnantes.uifirmaelectronica.core.model.ExchangeOperation
import com.lnantes.uifirmaelectronica.core.model.ExchangeResult
import com.lnantes.uifirmaelectronica.core.model.LogEntry
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.RemoteStation
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

data class ExchangeUiState(
    val stations: List<RemoteStation> = emptyList(),
    val selectedStationId: String? = null,
    val syncedStationId: String? = null,
    val scanning: Boolean = false,
    val synchronizing: Boolean = false,
    val manualName: String = "",
    val manualIp: String = "",
    val localReceipts: List<Receipt> = emptyList(),
    val selectedExportIds: Set<String> = emptySet(),
    val remoteReceipts: List<Receipt> = emptyList(),
    val selectedImportIds: Set<String> = emptySet(),
    val loadingRemote: Boolean = false,
    val running: ExchangeOperation? = null,
    val lastResult: ExchangeResult? = null,
    val completionVisible: Boolean = false,
    val log: List<LogEntry> = emptyList(),
    val error: String? = null,
) {
    val selectedStation: RemoteStation?
        get() = stations.firstOrNull { it.id == selectedStationId }

    val isSynced: Boolean
        get() = selectedStationId != null && selectedStationId == syncedStationId

    val exportSelection: List<Receipt>
        get() = localReceipts.filter { it.id in selectedExportIds }

    val importSelection: List<Receipt>
        get() = remoteReceipts.filter { it.id in selectedImportIds }
}

sealed interface ExchangeUiEvent {
    data object ScanStations : ExchangeUiEvent
    data class ManualNameChanged(val value: String) : ExchangeUiEvent
    data class ManualIpChanged(val value: String) : ExchangeUiEvent
    data object AddManualStation : ExchangeUiEvent
    data class SelectStation(val id: String) : ExchangeUiEvent
    data object SynchronizeStation : ExchangeUiEvent
    data object RefreshRemoteReceipts : ExchangeUiEvent
    data class ToggleExport(val id: String) : ExchangeUiEvent
    data class ToggleImport(val id: String) : ExchangeUiEvent
    data object SelectAllExport : ExchangeUiEvent
    data object ClearExportSelection : ExchangeUiEvent
    data object SelectAllImport : ExchangeUiEvent
    data object ClearImportSelection : ExchangeUiEvent
    data object Export : ExchangeUiEvent
    data object Import : ExchangeUiEvent
    data object DismissCompletion : ExchangeUiEvent
    data object ClearLog : ExchangeUiEvent
}

/**
 * Intercambio de recibos con otra estación: elección de PC (nombre + IP),
 * selección de recibos locales a exportar y remotos a importar.
 */
class ExchangePresenter(
    private val scope: CoroutineScope,
    private val services: SignatureServices,
) {
    private val _state = MutableStateFlow(ExchangeUiState())
    val state: StateFlow<ExchangeUiState> = _state.asStateFlow()
    private var logSequence = 0L
    private var operationJob: Job? = null

    init {
        scope.launch {
            services.receipts.receipts.collect { list ->
                _state.update { it.copy(localReceipts = list) }
            }
        }
    }

    fun onEvent(event: ExchangeUiEvent) {
        when (event) {
            ExchangeUiEvent.ScanStations -> scanStations()
            is ExchangeUiEvent.ManualNameChanged -> _state.update { it.copy(manualName = event.value) }
            is ExchangeUiEvent.ManualIpChanged -> _state.update { it.copy(manualIp = event.value) }
            ExchangeUiEvent.AddManualStation -> addManualStation()
            is ExchangeUiEvent.SelectStation -> selectStation(event.id)
            ExchangeUiEvent.SynchronizeStation -> synchronizeStation()
            ExchangeUiEvent.RefreshRemoteReceipts -> {
                operationJob?.cancel()
                _state.value.selectedStation?.let { loadRemoteReceipts(it) }
            }
            is ExchangeUiEvent.ToggleExport -> toggleId(event.id, export = true)
            is ExchangeUiEvent.ToggleImport -> toggleId(event.id, export = false)
            ExchangeUiEvent.SelectAllExport -> _state.update {
                val all = it.localReceipts.map { receipt -> receipt.id }.toSet()
                it.copy(selectedExportIds = if (it.selectedExportIds == all) emptySet() else all)
            }
            ExchangeUiEvent.ClearExportSelection -> _state.update { it.copy(selectedExportIds = emptySet()) }
            ExchangeUiEvent.SelectAllImport -> _state.update {
                val all = it.remoteReceipts.map { receipt -> receipt.id }.toSet()
                it.copy(selectedImportIds = if (it.selectedImportIds == all) emptySet() else all)
            }
            ExchangeUiEvent.ClearImportSelection -> _state.update { it.copy(selectedImportIds = emptySet()) }
            ExchangeUiEvent.Export -> runOperation(ExchangeOperation.EXPORT)
            ExchangeUiEvent.Import -> runOperation(ExchangeOperation.IMPORT)
            ExchangeUiEvent.DismissCompletion -> _state.update { it.copy(completionVisible = false) }
            ExchangeUiEvent.ClearLog -> _state.update { it.copy(log = emptyList()) }
        }
    }

    private fun scanStations() {
        operationJob?.cancel()
        operationJob = scope.launch {
            _state.update { it.copy(scanning = true, error = null) }
            log("Buscando estaciones en la red local…")

            services.exchange.discoverStations { message -> log(message) }.fold(
                onSuccess = { discovered ->
                    _state.update { current ->
                        val manual = current.stations.filter { it.manual }
                        val merged = (discovered + manual).distinctBy { it.id }
                        val currentSelectionStillValid = merged.any { it.id == current.selectedStationId }
                        current.copy(
                            stations = merged,
                            scanning = false,
                            selectedStationId = if (currentSelectionStillValid) current.selectedStationId
                            else merged.firstOrNull { it.online }?.id,
                            syncedStationId = null,
                            remoteReceipts = emptyList(),
                            selectedImportIds = emptySet(),
                            completionVisible = false,
                        )
                    }
                    log("Seleccione una estación y sincronícela para continuar", LogLevel.SUCCESS)
                },
                onFailure = { error ->
                    _state.update { it.copy(scanning = false) }
                    fail("No se pudieron descubrir estaciones", error)
                },
            )
        }
    }

    private fun addManualStation() {
        val current = _state.value
        val name = current.manualName.trim()
        val ip = current.manualIp.trim()
        if (name.isBlank() || ip.isBlank()) {
            log("Indique el nombre y la IP de la estación", LogLevel.WARNING)
            return
        }
        val station = RemoteStation(
            id = "MAN-$ip",
            name = name,
            ip = ip,
            online = true,
            lastSeen = "Añadida manualmente",
            manual = true,
        )
        _state.update {
            it.copy(
                stations = it.stations.filterNot { existing -> existing.id == station.id } + station,
                selectedStationId = station.id,
                syncedStationId = null,
                remoteReceipts = emptyList(),
                selectedImportIds = emptySet(),
                completionVisible = false,
                manualName = "",
                manualIp = "",
                error = null,
            )
        }
        log("Estación $name ($ip) añadida · sincronícela para continuar", LogLevel.SUCCESS)
    }

    private fun selectStation(id: String) {
        val station = _state.value.stations.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                selectedStationId = id,
                syncedStationId = null,
                remoteReceipts = emptyList(),
                selectedImportIds = emptySet(),
                completionVisible = false,
                error = null,
            )
        }
        log("Estación seleccionada: ${station.name} (${station.ip})")
        if (!station.online) {
            log("La estación no está disponible en este momento", LogLevel.WARNING)
        } else {
            log("Pulse «Sincronizar terminal» para cargar los recibos")
        }
    }

    private fun synchronizeStation() {
        val station = _state.value.selectedStation
        if (station == null) {
            log("Busque y seleccione una terminal primero", LogLevel.WARNING)
            return
        }
        if (!station.online) {
            log("La estación seleccionada no está disponible", LogLevel.WARNING)
            return
        }

        operationJob?.cancel()
        operationJob = scope.launch {
            _state.update { it.copy(synchronizing = true, error = null, completionVisible = false) }

            services.exchange.synchronize(station) { message -> log(message) }.fold(
                onSuccess = { synced ->
                    _state.update {
                        it.copy(
                            stations = it.stations.map { item -> if (item.id == synced.id) synced else item },
                            syncedStationId = synced.id,
                            synchronizing = false,
                        )
                    }
                    log("Terminal sincronizada correctamente", LogLevel.SUCCESS)
                    loadRemoteReceipts(synced)
                },
                onFailure = { error ->
                    _state.update { it.copy(synchronizing = false) }
                    fail("No se pudo sincronizar la terminal", error)
                },
            )
        }
    }

    private fun loadRemoteReceipts(station: RemoteStation) {
        operationJob = scope.launch {
            _state.update { it.copy(loadingRemote = true, remoteReceipts = emptyList(), selectedImportIds = emptySet()) }
            services.exchange.listRemoteReceipts(station) { message -> log(message) }.fold(
                onSuccess = { receipts ->
                    _state.update { it.copy(remoteReceipts = receipts, loadingRemote = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(loadingRemote = false) }
                    fail("No se pudieron cargar los recibos remotos", error)
                },
            )
        }
    }

    private fun toggleId(id: String, export: Boolean) {
        _state.update { state ->
            val current = if (export) state.selectedExportIds else state.selectedImportIds
            val updated = if (id in current) current - id else current + id
            if (export) state.copy(selectedExportIds = updated) else state.copy(selectedImportIds = updated)
        }
    }

    private fun runOperation(operation: ExchangeOperation) {
        val state = _state.value
        val station = state.selectedStation
        if (station == null || !state.isSynced) {
            log("Busque y sincronice la terminal antes de intercambiar recibos", LogLevel.WARNING)
            return
        }
        val receipts = if (operation == ExchangeOperation.EXPORT) state.exportSelection else state.importSelection
        if (receipts.isEmpty()) {
            log("Seleccione al menos un recibo", LogLevel.WARNING)
            return
        }

        operationJob?.cancel()
        operationJob = scope.launch {
            _state.update { it.copy(running = operation, error = null) }
            val result = if (operation == ExchangeOperation.EXPORT) {
                services.exchange.exportReceipts(receipts, station) { message -> log(message) }
            } else {
                services.exchange.importReceipts(receipts, station) { message -> log(message) }
            }

            result.fold(
                onSuccess = { value ->
                    _state.update { current ->
                        current.copy(
                            running = null,
                            lastResult = value,
                            completionVisible = true,
                            selectedImportIds = if (operation == ExchangeOperation.IMPORT) emptySet() else current.selectedImportIds,
                        )
                    }
                    log("Intercambio completado: ${value.processed} recibos", LogLevel.SUCCESS)
                },
                onFailure = { error -> fail("Error en el intercambio", error) },
            )
        }
    }

    private fun fail(message: String, error: Throwable) {
        if (error is CancellationException) return
        val detail = error.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        _state.update {
            it.copy(
                running = null,
                scanning = false,
                synchronizing = false,
                loadingRemote = false,
                error = message + detail,
            )
        }
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

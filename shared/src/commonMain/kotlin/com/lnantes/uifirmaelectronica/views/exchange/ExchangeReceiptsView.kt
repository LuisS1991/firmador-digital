package com.lnantes.uifirmaelectronica.views.exchange

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnantes.uifirmaelectronica.core.model.ExchangeOperation
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.RemoteStation
import com.lnantes.uifirmaelectronica.ui.components.EmptyState
import com.lnantes.uifirmaelectronica.ui.components.InfoRow
import com.lnantes.uifirmaelectronica.ui.components.OperationLogPanel
import com.lnantes.uifirmaelectronica.ui.components.PageHeader
import com.lnantes.uifirmaelectronica.ui.components.PrimaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.ProcessStepper
import com.lnantes.uifirmaelectronica.ui.components.QuietActionButton
import com.lnantes.uifirmaelectronica.ui.components.SecondaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.SectionCard
import com.lnantes.uifirmaelectronica.ui.components.StatusPill
import com.lnantes.uifirmaelectronica.ui.presentation.ExchangePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.ExchangeUiEvent
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

@Composable
fun ExchangeReceiptsView(presenter: ExchangePresenter) {
    val state by presenter.state.collectAsState()
    val send = presenter::onEvent
    val station = state.selectedStation
    val currentStep = when {
        station == null -> 0
        !state.isSynced -> 1
        else -> 2
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        PageHeader(
            eyebrow = "Traspaso entre estaciones",
            title = "Intercambiar recibos con otra PC",
            description = "Busque la terminal, sincronícela y seleccione los recibos que desea exportar o importar.",
            trailing = {
                StatusPill(
                    text = when {
                        state.synchronizing -> "Sincronizando"
                        state.isSynced -> "Terminal sincronizada"
                        station != null -> "Terminal seleccionada"
                        else -> "Sin terminal"
                    },
                    level = when {
                        state.synchronizing -> LogLevel.WARNING
                        state.isSynced -> LogLevel.SUCCESS
                        else -> LogLevel.INFO
                    },
                )
            },
        )

        Spacer(Modifier.height(22.dp))
        ProcessStepper(
            steps = listOf("Buscar terminal", "Sincronizar", "Intercambiar recibos"),
            currentIndex = currentStep,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        AnimatedVisibility(visible = state.completionVisible && state.lastResult != null) {
            Column {
                Spacer(Modifier.height(18.dp))
                CompletionBanner(
                    operation = state.lastResult!!.operation,
                    processed = state.lastResult!!.processed,
                    detail = state.lastResult!!.detail,
                    stationName = station?.name.orEmpty(),
                    onDismiss = { send(ExchangeUiEvent.DismissCompletion) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(modifier = Modifier.weight(1.35f)) {
                // Paso 1 ----------------------------------------------------
                SectionCard(
                    title = "1 · Buscar terminal",
                    subtitle = "Seleccione la PC de destino u origen",
                    icon = AppIcon.SEARCH,
                ) {
                    if (state.stations.isEmpty() && !state.scanning) {
                        Text(
                            "No hay terminales en la lista. Busque en la red o añada una manualmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SecondaryActionButton(
                            "Buscar terminales",
                            AppIcon.SEARCH,
                            { send(ExchangeUiEvent.ScanStations) },
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState())) {
                            state.stations.forEach { item ->
                                StationChip(
                                    station = item,
                                    selected = item.id == state.selectedStationId,
                                    onClick = { send(ExchangeUiEvent.SelectStation(item.id)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        QuietActionButton(
                            if (state.scanning) "Buscando…" else "Volver a buscar terminales",
                            AppIcon.REFRESH,
                            { send(ExchangeUiEvent.ScanStations) },
                            enabled = !state.scanning,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    ManualStationFields(
                        name = state.manualName,
                        ip = state.manualIp,
                        onNameChange = { send(ExchangeUiEvent.ManualNameChanged(it)) },
                        onIpChange = { send(ExchangeUiEvent.ManualIpChanged(it)) },
                        onAdd = { send(ExchangeUiEvent.AddManualStation) },
                    )
                }

                Spacer(Modifier.height(16.dp))
                // Paso 2 ----------------------------------------------------
                SectionCard(
                    title = "2 · Sincronizar terminal",
                    subtitle = station?.let { "${it.name} · ${it.ip}" } ?: "Seleccione una terminal en el paso 1",
                    icon = AppIcon.SYNC,
                ) {
                    ExchangeLinkAnimation(
                        active = state.synchronizing || state.running != null || state.loadingRemote,
                        connected = state.isSynced,
                        remoteLabel = station?.label ?: "Sin seleccionar",
                    )
                    if (state.isSynced) {
                        InfoRow(AppIcon.CHECK, "Estado", "Sincronizada correctamente", valueColor = AppColors.Success)
                        InfoRow(AppIcon.CLOCK, "Última sincronización", station?.lastSeen.orEmpty())
                        Spacer(Modifier.height(12.dp))
                        QuietActionButton(
                            "Volver a sincronizar",
                            AppIcon.REFRESH,
                            onClick = { send(ExchangeUiEvent.SynchronizeStation) },
                            enabled = !state.synchronizing,
                        )
                    } else {
                        PrimaryActionButton(
                            "Sincronizar terminal",
                            AppIcon.SYNC,
                            onClick = { send(ExchangeUiEvent.SynchronizeStation) },
                            enabled = station != null && station.online && !state.synchronizing,
                            loading = state.synchronizing,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Paso 3 ----------------------------------------------------
                if (!state.isSynced) {
                    SectionCard(
                        title = "3 · Intercambiar recibos",
                        subtitle = "Disponible después de la sincronización",
                        icon = AppIcon.EXCHANGE,
                    ) {
                        EmptyState(
                            icon = AppIcon.LOCK,
                            title = "Terminal no sincronizada",
                            message = "Busque y sincronice una terminal para ver los recibos disponibles para exportar e importar.",
                            action = {
                                PrimaryActionButton(
                                    "Sincronizar ahora",
                                    AppIcon.SYNC,
                                    onClick = { send(ExchangeUiEvent.SynchronizeStation) },
                                    enabled = station != null && station.online && !state.synchronizing,
                                    loading = state.synchronizing,
                                )
                            },
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionCard(
                            title = "Exportar recibos",
                            subtitle = "Desde esta estación",
                            icon = AppIcon.UPLOAD,
                            modifier = Modifier.weight(1f),
                        ) {
                            SelectionHeader(
                                selected = state.selectedExportIds.size,
                                total = state.localReceipts.size,
                                onSelectAll = { send(ExchangeUiEvent.SelectAllExport) },
                                onClear = { send(ExchangeUiEvent.ClearExportSelection) },
                            )
                            Spacer(Modifier.height(8.dp))
                            ReceiptSelectionList(
                                receipts = state.localReceipts,
                                selectedIds = state.selectedExportIds,
                                onToggle = { send(ExchangeUiEvent.ToggleExport(it)) },
                                emptyText = "No hay recibos locales",
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryActionButton(
                                "Exportar ${state.selectedExportIds.size} recibo(s)",
                                AppIcon.UPLOAD,
                                onClick = { send(ExchangeUiEvent.Export) },
                                enabled = state.selectedExportIds.isNotEmpty() && state.running == null,
                                loading = state.running == ExchangeOperation.EXPORT,
                            )
                        }

                        SectionCard(
                            title = "Importar recibos",
                            subtitle = station?.name.orEmpty(),
                            icon = AppIcon.DOWNLOAD,
                            modifier = Modifier.weight(1f),
                        ) {
                            SelectionHeader(
                                selected = state.selectedImportIds.size,
                                total = state.remoteReceipts.size,
                                onSelectAll = { send(ExchangeUiEvent.SelectAllImport) },
                                onClear = { send(ExchangeUiEvent.ClearImportSelection) },
                            )
                            if (state.loadingRemote) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Consultando recibos en ${station?.ip}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            ReceiptSelectionList(
                                receipts = state.remoteReceipts,
                                selectedIds = state.selectedImportIds,
                                onToggle = { send(ExchangeUiEvent.ToggleImport(it)) },
                                emptyText = "No hay recibos en la estación remota",
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuietActionButton(
                                    "Recargar recibos",
                                    AppIcon.REFRESH,
                                    onClick = { send(ExchangeUiEvent.RefreshRemoteReceipts) },
                                    enabled = state.running == null && !state.loadingRemote,
                                )
                                Spacer(Modifier.weight(1f))
                                SecondaryActionButton(
                                    "Importar ${state.selectedImportIds.size} recibo(s)",
                                    AppIcon.DOWNLOAD,
                                    onClick = { send(ExchangeUiEvent.Import) },
                                    enabled = state.selectedImportIds.isNotEmpty() && state.running == null,
                                    loading = state.running == ExchangeOperation.IMPORT,
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                OperationLogPanel(logs = state.log)
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = AppColors.Error)
                }
                Spacer(Modifier.height(12.dp))
                QuietActionButton("Limpiar registro", AppIcon.DELETE, onClick = { send(ExchangeUiEvent.ClearLog) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CompletionBanner(
    operation: ExchangeOperation,
    processed: Int,
    detail: String,
    stationName: String,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Success.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(AppColors.Success.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIconGlyph(AppIcon.CHECK, AppColors.Success, Modifier.size(25.dp), 2.6f)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "¡Transacción finalizada!",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.Success,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${if (operation == ExchangeOperation.EXPORT) "Exportación" else "Importación"} de " +
                    "$processed recibo(s) con $stationName",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QuietActionButton("Aceptar", AppIcon.CHECK, onClick = onDismiss)
    }
}

@Composable
private fun StationChip(
    station: RemoteStation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Surface(
        onClick = onClick,
        enabled = station.online,
        color = background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (station.online) AppColors.Success else MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    station.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(station.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                AppIconGlyph(AppIcon.CHECK, MaterialTheme.colorScheme.primary, Modifier.size(18.dp), 2.4f)
            } else {
                Text(
                    if (station.online) station.lastSeen else "Sin conexión",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (station.online) MaterialTheme.colorScheme.onSurfaceVariant else AppColors.Error,
                )
            }
        }
    }
}

@Composable
private fun ManualStationFields(
    name: String,
    ip: String,
    onNameChange: (String) -> Unit,
    onIpChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Text(
        "Añadir terminal manualmente",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallField("Nombre de PC", name, onNameChange, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        SmallField("Dirección IP", ip, onIpChange, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        QuietActionButton("Añadir", AppIcon.PLUS, onClick = onAdd)
    }
}

@Composable
private fun SmallField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectionHeader(
    selected: Int,
    total: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$selected de $total seleccionados",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        QuietActionButton("Todos", AppIcon.CHECK, onClick = onSelectAll)
        QuietActionButton("Ninguno", AppIcon.CLOSE, onClick = onClear)
    }
}

@Composable
private fun ReceiptSelectionList(
    receipts: List<Receipt>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    emptyText: String,
) {
    Box(Modifier.fillMaxWidth().height(210.dp).verticalScroll(rememberScrollState())) {
        if (receipts.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            receipts.forEach { receipt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (receipt.id in selectedIds) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = receipt.id in selectedIds, onCheckedChange = { onToggle(receipt.id) })
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onToggle(receipt.id) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(receipt.number, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${receipt.concept} · ${receipt.amount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExchangeLinkAnimation(
    active: Boolean,
    connected: Boolean,
    remoteLabel: String,
) {
    val accent = if (connected) AppColors.Success else MaterialTheme.colorScheme.primary
    val localLabel = "PC-Recepción-01 · 192.168.1.20"

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PcNode(title = "ESTACIÓN LOCAL", subtitle = localLabel, highlighted = connected || active)
            Spacer(Modifier.width(12.dp))
            if (active) FlowingLinkDots(accent) else StaticLinkDots(connected = connected, accent = accent)
            Spacer(Modifier.width(12.dp))
            PcNode(title = "ESTACIÓN REMOTA", subtitle = remoteLabel, highlighted = connected || active)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                active -> "Sincronizando / transfiriendo información…"
                connected -> "Enlace establecido con ${remoteLabel.substringBefore(" · ")}"
                else -> "Seleccione una terminal para comenzar"
            },
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FlowingLinkDots(accent: Color) {
    val transition = rememberInfiniteTransition()
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
    )
    val phase = flow.toInt() % 4
    LinkDots(litIndexes = setOf(phase, (phase + 3) % 4), litColor = accent)
}

@Composable
private fun StaticLinkDots(connected: Boolean, accent: Color) {
    LinkDots(litIndexes = if (connected) (0..3).toSet() else emptySet(), litColor = accent)
}

@Composable
private fun LinkDots(litIndexes: Set<Int>, litColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            val lit = index in litIndexes
            Box(
                Modifier
                    .size(if (lit) 11.dp else 8.dp)
                    .background(
                        if (lit) litColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(50),
                    ),
            )
            if (index < 3) Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun PcNode(title: String, subtitle: String, highlighted: Boolean) {
    val foreground = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(124.dp)) {
        Box(
            Modifier
                .size(54.dp)
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(13.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppIconGlyph(AppIcon.COMPUTER, foreground, Modifier.size(30.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = foreground)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

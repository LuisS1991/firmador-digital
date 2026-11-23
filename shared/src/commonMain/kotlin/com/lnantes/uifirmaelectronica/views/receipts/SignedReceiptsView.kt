package com.lnantes.uifirmaelectronica.views.receipts

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lnantes.uifirmaelectronica.core.model.AppDestination
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.ReceiptStatus
import com.lnantes.uifirmaelectronica.ui.components.EmptyState
import com.lnantes.uifirmaelectronica.ui.components.InfoRow
import com.lnantes.uifirmaelectronica.ui.components.PageHeader
import com.lnantes.uifirmaelectronica.ui.components.PrimaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.QuietActionButton
import com.lnantes.uifirmaelectronica.ui.components.StatusPill
import com.lnantes.uifirmaelectronica.ui.presentation.ReceiptFilter
import com.lnantes.uifirmaelectronica.ui.presentation.ReceiptsPresenter
import com.lnantes.uifirmaelectronica.ui.presentation.ReceiptsUiEvent
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

@Composable
fun SignedReceiptsView(
    presenter: ReceiptsPresenter,
    onNavigate: (AppDestination) -> Unit,
) {
    val state by presenter.state.collectAsState()
    val send = presenter::onEvent
    val visible = presenter.visibleReceipts(state)
    val selected = state.receipts.firstOrNull { it.id == state.selectedId }

    if (selected != null) {
        ReceiptDetailDialog(
            receipt = selected,
            onDismiss = { send(ReceiptsUiEvent.CloseDetail) },
            onViewPdf = { send(ReceiptsUiEvent.ViewPdf) },
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        PageHeader(
            eyebrow = "Archivo local",
            title = "Recibos físicos firmados",
            description = "Consulte los recibos guardados en esta estación y revise el detalle de su firma.",
            trailing = {
                PrimaryActionButton("Generar recibo", AppIcon.PLUS, onClick = { onNavigate(AppDestination.ADVANCED) })
            },
        )

        if (state.notice != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Teal.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconGlyph(AppIcon.INFO, AppColors.Teal, Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(state.notice!!, style = MaterialTheme.typography.bodySmall, color = AppColors.Teal)
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconGlyph(AppIcon.SEARCH, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (state.query.isEmpty()) {
                            Text(
                                "Buscar por número, concepto o titular…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = { send(ReceiptsUiEvent.QueryChanged(it)) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    FilterButton("Todos", ReceiptFilter.ALL, state.filter) { send(ReceiptsUiEvent.FilterChanged(it)) }
                    FilterButton("Firmados", ReceiptFilter.SIGNED, state.filter) { send(ReceiptsUiEvent.FilterChanged(it)) }
                    FilterButton("Pendientes", ReceiptFilter.PENDING, state.filter) { send(ReceiptsUiEvent.FilterChanged(it)) }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "${visible.size} de ${state.receipts.size} recibos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (visible.isEmpty()) {
                    EmptyState(
                        icon = AppIcon.RECEIPT,
                        title = "No hay recibos que coincidan",
                        message = "Ajuste la búsqueda o el filtro, o genere un nuevo recibo.",
                    )
                } else {
                    visible.forEach { receipt ->
                        ReceiptRow(receipt) { send(ReceiptsUiEvent.Select(receipt.id)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilterButton(
    label: String,
    value: ReceiptFilter,
    current: ReceiptFilter,
    onSelect: (ReceiptFilter) -> Unit,
) {
    val active = value == current
    Box(
        Modifier
            .clickable { onSelect(value) }
            .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIconGlyph(AppIcon.PDF, MaterialTheme.colorScheme.primary, Modifier.size(21.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(receipt.number, style = MaterialTheme.typography.titleMedium)
                Text(receipt.concept, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${receipt.signer} · ${receipt.issuedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(receipt.amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    if (receipt.status == ReceiptStatus.SIGNED) "Firmado" else "Pendiente",
                    if (receipt.status == ReceiptStatus.SIGNED) LogLevel.SUCCESS else LogLevel.WARNING,
                )
            }
            Spacer(Modifier.width(10.dp))
            AppIconGlyph(AppIcon.CHEVRON_RIGHT, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ReceiptDetailDialog(
    receipt: Receipt,
    onDismiss: () -> Unit,
    onViewPdf: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.width(560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconGlyph(AppIcon.RECEIPT, MaterialTheme.colorScheme.primary, Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Detalle del recibo", style = MaterialTheme.typography.titleLarge)
                        Text(receipt.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        AppIconGlyph(AppIcon.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                InfoRow(AppIcon.INFO, "Número", receipt.number)
                InfoRow(AppIcon.DOCUMENT, "Concepto", receipt.concept)
                InfoRow(AppIcon.SAVE, "Importe", receipt.amount)
                InfoRow(AppIcon.PEN, "Titular", receipt.signer)
                InfoRow(AppIcon.CLOCK, "Emitido", receipt.issuedAt)
                InfoRow(AppIcon.TOKEN, "Token", receipt.tokenSerial ?: "Sin firmar")
                InfoRow(AppIcon.CHECK, "Firmado", receipt.signedAt ?: "Pendiente")

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))
                Row {
                    QuietActionButton("Cerrar", AppIcon.CLOSE, onDismiss)
                    Spacer(Modifier.weight(1f))
                    PrimaryActionButton("Ver PDF", AppIcon.PDF, onViewPdf)
                }
            }
        }
    }
}

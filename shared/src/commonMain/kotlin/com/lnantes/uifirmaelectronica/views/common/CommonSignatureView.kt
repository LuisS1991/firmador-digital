package com.lnantes.uifirmaelectronica.views.common

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.ui.components.InfoRow
import com.lnantes.uifirmaelectronica.ui.components.OperationLogPanel
import com.lnantes.uifirmaelectronica.ui.components.PageHeader
import com.lnantes.uifirmaelectronica.ui.components.PrimaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.ProcessStepper
import com.lnantes.uifirmaelectronica.ui.components.QuietActionButton
import com.lnantes.uifirmaelectronica.ui.components.SecondaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.SectionCard
import com.lnantes.uifirmaelectronica.ui.components.SignaturePreviewDialog
import com.lnantes.uifirmaelectronica.ui.components.StatusPill
import com.lnantes.uifirmaelectronica.ui.components.SyncAnimation
import com.lnantes.uifirmaelectronica.ui.presentation.CommonPhase
import com.lnantes.uifirmaelectronica.ui.presentation.CommonSignaturePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.CommonUiEvent
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

@Composable
fun CommonSignatureView(presenter: CommonSignaturePresenter) {
    val state by presenter.state.collectAsState()
    val send = presenter::onEvent

    if (state.previewVisible && state.signature != null) {
        SignaturePreviewDialog(
            signature = state.signature!!,
            onDismiss = { send(CommonUiEvent.ClosePreview) },
            onConfirm = { send(CommonUiEvent.ClosePreview) },
        )
    }

    val currentStep = when (state.phase) {
        CommonPhase.IDLE, CommonPhase.CONNECTING, CommonPhase.SYNCING -> 0
        CommonPhase.SYNCED, CommonPhase.CAPTURING -> 1
        CommonPhase.READY, CommonPhase.SIGNING -> 2
        CommonPhase.SIGNED -> 3
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        PageHeader(
            eyebrow = "Firma digital común",
            title = "Captura desde tablet",
            description = "Sincronice el dispositivo, revise la firma capturada y aplíquela al documento.",
            trailing = {
                StatusPill(
                    text = when (state.phase) {
                        CommonPhase.IDLE -> "En espera"
                        CommonPhase.CONNECTING, CommonPhase.SYNCING -> "Sincronizando"
                        CommonPhase.SYNCED -> "Tablet lista"
                        CommonPhase.CAPTURING -> "Capturando"
                        CommonPhase.READY -> "Firma capturada"
                        CommonPhase.SIGNING -> "Firmando"
                        CommonPhase.SIGNED -> "Completado"
                    },
                    level = when (state.phase) {
                        CommonPhase.IDLE -> LogLevel.INFO
                        CommonPhase.CONNECTING, CommonPhase.SYNCING, CommonPhase.CAPTURING, CommonPhase.SIGNING -> LogLevel.WARNING
                        else -> LogLevel.SUCCESS
                    },
                )
            },
        )

        Spacer(Modifier.height(24.dp))
        ProcessStepper(
            steps = listOf("Sincronizar tablet", "Capturar firma", "Previsualizar", "Firmar documento"),
            currentIndex = currentStep,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(modifier = Modifier.weight(1.35f)) {
                SectionCard(
                    title = "1 · Sincronización con tablet",
                    subtitle = "Enlace con el dispositivo de captura",
                    icon = AppIcon.SYNC,
                ) {
                    SyncAnimation(
                        active = state.phase == CommonPhase.CONNECTING || state.phase == CommonPhase.SYNCING,
                        completed = state.phase != CommonPhase.IDLE &&
                            state.phase != CommonPhase.CONNECTING &&
                            state.phase != CommonPhase.SYNCING,
                    )
                    if (state.device != null) {
                        Spacer(Modifier.height(6.dp))
                        InfoRow(AppIcon.TABLET, "Dispositivo", state.device!!.name)
                        InfoRow(AppIcon.INFO, "Modelo", state.device!!.model)
                        InfoRow(AppIcon.SHIELD, "ID de enlace", state.device!!.id)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        PrimaryActionButton(
                            text = if (state.device == null) "Sincronizar tablet" else "Volver a sincronizar",
                            icon = AppIcon.SYNC,
                            onClick = { send(CommonUiEvent.SyncTablet) },
                            loading = state.phase == CommonPhase.CONNECTING || state.phase == CommonPhase.SYNCING,
                            enabled = !state.busy,
                        )
                        Spacer(Modifier.width(10.dp))
                        QuietActionButton("Limpiar registro", AppIcon.DELETE, { send(CommonUiEvent.ClearLog) })
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(
                    title = "2 · Firma capturada",
                    subtitle = "Trazo recibido desde la tablet",
                    icon = AppIcon.PEN,
                ) {
                    if (state.signature != null) {
                        SignatureCapturedContent(state.signature!!.signerName, state.signature!!.capturedAt, state.signature!!.id)
                        Spacer(Modifier.height(14.dp))
                        Row {
                            SecondaryActionButton(
                                "Previsualizar firma",
                                AppIcon.EYE,
                                { send(CommonUiEvent.OpenPreview) },
                            )
                            Spacer(Modifier.width(10.dp))
                            QuietActionButton(
                                "Capturar de nuevo",
                                AppIcon.REFRESH,
                                { send(CommonUiEvent.CaptureSignature) },
                                enabled = !state.busy && state.device != null,
                            )
                        }
                    } else {
                        CapturedPlaceholder(enabled = state.device != null && !state.busy) {
                            send(CommonUiEvent.CaptureSignature)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(
                    title = "3 · Firmar documento",
                    subtitle = "Aplicación de la firma común",
                    icon = AppIcon.DOCUMENT,
                ) {
                    InfoRow(AppIcon.PDF, "Documento", "Contrato_de_prestacion_2026.pdf")
                    InfoRow(AppIcon.INFO, "Referencia", "DOC-2026-1184 · 4 páginas")
                    Spacer(Modifier.height(16.dp))
                    PrimaryActionButton(
                        text = if (state.signedDocument != null) "Firmar de nuevo" else "Firmar documento",
                        icon = AppIcon.CHECK,
                        onClick = { send(CommonUiEvent.SignDocument) },
                        enabled = state.signature != null,
                        loading = state.phase == CommonPhase.SIGNING,
                    )
                    if (state.signedDocument != null) {
                        Spacer(Modifier.height(14.dp))
                        SuccessBanner("Documento firmado correctamente", state.signedDocument!!.hash)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                SectionCard(title="Estado del proceso", icon = AppIcon.CLOCK) {
                    ProcessTimeline(state.phase)
                }
                Spacer(Modifier.height(16.dp))
                OperationLogPanel(logs = state.log)
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(state.error!!)
                }
                Spacer(Modifier.height(12.dp))
                QuietActionButton("Reiniciar flujo", AppIcon.REFRESH, { send(CommonUiEvent.Reset) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SignatureCapturedContent(name: String, capturedAt: String, id: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(62.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIconGlyph(AppIcon.PEN, MaterialTheme.colorScheme.primary, Modifier.size(32.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(capturedAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CapturedPlaceholder(enabled: Boolean, onCapture: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(60.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIconGlyph(AppIcon.PEN, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Sin firma capturada", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (enabled) "Capture la firma para continuar" else "Sincronice la tablet para continuar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryActionButton("Capturar firma", AppIcon.PEN, onCapture, enabled = enabled)
    }
}

@Composable
private fun ProcessTimeline(phase: CommonPhase) {
    val items = listOf(
        "Enlace con tablet" to setOf(CommonPhase.CONNECTING, CommonPhase.SYNCING, CommonPhase.SYNCED, CommonPhase.CAPTURING, CommonPhase.READY, CommonPhase.SIGNING, CommonPhase.SIGNED),
        "Firma capturada" to setOf(CommonPhase.CAPTURING, CommonPhase.READY, CommonPhase.SIGNING, CommonPhase.SIGNED),
        "Firma aplicada" to setOf(CommonPhase.SIGNING, CommonPhase.SIGNED),
    )
    items.forEach { (label, reachedPhases) ->
        val reached = phase in reachedPhases
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(
                        if (reached) AppColors.Success.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppIconGlyph(
                    if (reached) AppIcon.CHECK else AppIcon.CLOCK,
                    if (reached) AppColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.size(13.dp),
                    2.4f,
                )
            }
            Spacer(Modifier.width(11.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (reached) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun SuccessBanner(title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Success.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconGlyph(AppIcon.CHECK, AppColors.Success, Modifier.size(20.dp), 2.4f)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AppColors.Success, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Error.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconGlyph(AppIcon.WARNING, AppColors.Error, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.Error)
    }
}

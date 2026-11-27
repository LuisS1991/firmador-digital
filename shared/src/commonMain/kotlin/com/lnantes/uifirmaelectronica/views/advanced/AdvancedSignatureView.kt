package com.lnantes.uifirmaelectronica.views.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnantes.uifirmaelectronica.core.model.AppDestination
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.ReceiptStatus
import com.lnantes.uifirmaelectronica.ui.components.InfoRow
import com.lnantes.uifirmaelectronica.ui.components.OperationLogPanel
import com.lnantes.uifirmaelectronica.ui.components.PageHeader
import com.lnantes.uifirmaelectronica.ui.components.PrimaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.ProcessStepper
import com.lnantes.uifirmaelectronica.ui.components.QuietActionButton
import com.lnantes.uifirmaelectronica.ui.components.SectionCard
import com.lnantes.uifirmaelectronica.ui.components.SecondaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.StatusPill
import com.lnantes.uifirmaelectronica.ui.components.TokenAnimation
import com.lnantes.uifirmaelectronica.ui.presentation.AdvancedPhase
import com.lnantes.uifirmaelectronica.ui.presentation.AdvancedSignaturePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.AdvancedUiEvent
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

@Composable
fun AdvancedSignatureView(
    presenter: AdvancedSignaturePresenter,
    onNavigate: (AppDestination) -> Unit,
) {
    val state by presenter.state.collectAsState()
    val send = presenter::onEvent

    val currentStep = when (state.phase) {
        AdvancedPhase.IDLE -> 0
        AdvancedPhase.GENERATING -> 0
        AdvancedPhase.READY -> 1
        AdvancedPhase.SIGNING -> 1
        AdvancedPhase.SIGNED -> 2
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        PageHeader(
            eyebrow = "Firma digital avanzada",
            title = "Recibo local + token físico",
            description = "Genere el comprobante PDF, aplique la firma avanzada con su token y consulte el archivo local.",
            trailing = {
                StatusPill(
                    text = when (state.phase) {
                        AdvancedPhase.IDLE -> "En espera"
                        AdvancedPhase.GENERATING -> "Generando PDF"
                        AdvancedPhase.READY -> "Pendiente de firma"
                        AdvancedPhase.SIGNING -> "Firmando con token"
                        AdvancedPhase.SIGNED -> "Recibo firmado"
                    },
                    level = when (state.phase) {
                        AdvancedPhase.IDLE -> LogLevel.INFO
                        AdvancedPhase.GENERATING, AdvancedPhase.SIGNING -> LogLevel.WARNING
                        else -> LogLevel.SUCCESS
                    },
                )
            },
        )

        Spacer(Modifier.height(24.dp))
        ProcessStepper(
            steps = listOf("Generar recibo PDF", "Firmar con token", "Archivar en local"),
            currentIndex = currentStep,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(modifier = Modifier.weight(1.35f)) {
                SectionCard(
                    title = "1 · Generar recibo PDF en local",
                    subtitle = "Se crea el comprobante antes de aplicar la firma",
                    icon = AppIcon.PDF,
                ) {
                    Text(
                        "Código del recibo (endpoint local)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = state.codigo,
                            onValueChange = { send(AdvancedUiEvent.CodigoChanged(it)) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (state.receipt != null) {
                        ReceiptSummary(
                            number = state.receipt!!.number,
                            concept = state.receipt!!.concept,
                            amount = state.receipt!!.amount,
                            fileName = state.receipt!!.fileName,
                            signer = state.receipt!!.signer,
                            issuedAt = state.receipt!!.issuedAt,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row {
                            SecondaryActionButton(
                                "Generar otro recibo",
                                AppIcon.REFRESH,
                                { send(AdvancedUiEvent.GenerateReceipt) },
                                enabled = !state.busy,
                            )
                            Spacer(Modifier.width(10.dp))
                            QuietActionButton("Ver archivo local", AppIcon.FOLDER, onClick = { onNavigate(AppDestination.RECEIPTS) })
                        }
                    } else {
                        Text(
                            "El recibo se guardará en la carpeta local de la aplicación. " +
                                "Una vez generado podrá firmarlo con el token físico.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryActionButton(
                            "Generar recibo PDF",
                            AppIcon.PDF,
                            { send(AdvancedUiEvent.GenerateReceipt) },
                            loading = state.phase == AdvancedPhase.GENERATING,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(
                    title = "2 · Firmar con token físico",
                    subtitle = "Firma avanzada con certificado del dispositivo",
                    icon = AppIcon.TOKEN,
                ) {
                    TokenAnimation(
                        active = state.phase == AdvancedPhase.SIGNING,
                        completed = state.phase == AdvancedPhase.SIGNED,
                    )
                    val canSign = state.receipt != null &&
                        state.receipt!!.status == ReceiptStatus.PENDING &&
                        !state.busy
                    PrimaryActionButton(
                        text = "Firmar con token",
                        icon = AppIcon.LOCK,
                        onClick = { send(AdvancedUiEvent.SignWithToken) },
                        enabled = canSign,
                        loading = state.phase == AdvancedPhase.SIGNING,
                    )
                    if (state.phase == AdvancedPhase.SIGNED && state.receipt != null) {
                        Spacer(Modifier.height(14.dp))
                        SignedBanner(state.receipt!!.tokenSerial.orEmpty(), state.receipt!!.signedAt.orEmpty())
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                SectionCard(
                    title="Recibos en local",
                    subtitle="${state.localReceipts.size} comprobantes almacenados",
                    icon = AppIcon.FOLDER,
                ) {
                    state.localReceipts.take(4).forEach { receipt ->
                        Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIconGlyph(AppIcon.PDF, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(receipt.number, style = MaterialTheme.typography.bodyMedium)
                                Text(receipt.concept, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                if (receipt.status == ReceiptStatus.SIGNED) "Firmado" else "Pendiente",
                                if (receipt.status == ReceiptStatus.SIGNED) LogLevel.SUCCESS else LogLevel.WARNING,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    QuietActionButton("Abrir gestor de recibos", AppIcon.ARROW_RIGHT, onClick = { onNavigate(AppDestination.RECEIPTS) })
                }

                Spacer(Modifier.height(16.dp))
                OperationLogPanel(logs = state.log)
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = AppColors.Error)
                }
                Spacer(Modifier.height(10.dp))
                QuietActionButton("Reiniciar flujo", AppIcon.REFRESH, onClick = { send(AdvancedUiEvent.Reset) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReceiptSummary(
    number: String,
    concept: String,
    amount: String,
    fileName: String,
    signer: String,
    issuedAt: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconGlyph(AppIcon.PDF, MaterialTheme.colorScheme.primary, Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Text(number, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        InfoRow(AppIcon.INFO, "Concepto", concept)
        if (signer.isNotBlank()) InfoRow(AppIcon.SHIELD, "Titular", signer)
        if (issuedAt.isNotBlank()) InfoRow(AppIcon.CLOCK, "Fecha del recibo", issuedAt)
        InfoRow(AppIcon.SAVE, "Archivo", fileName)
    }
}

@Composable
private fun SignedBanner(token: String, signedAt: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Success.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconGlyph(AppIcon.CHECK, AppColors.Success, Modifier.size(19.dp), 2.4f)
            Spacer(Modifier.width(9.dp))
            Text("Firma avanzada aplicada", color = AppColors.Success, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        InfoRow(AppIcon.TOKEN, "Token", token)
        InfoRow(AppIcon.CLOCK, "Fecha", signedAt)
    }
}

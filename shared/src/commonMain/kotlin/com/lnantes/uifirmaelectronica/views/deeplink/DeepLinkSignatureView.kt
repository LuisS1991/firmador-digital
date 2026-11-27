package com.lnantes.uifirmaelectronica.views.deeplink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.core.model.LadoFirma
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.MetodoFirma
import com.lnantes.uifirmaelectronica.ui.components.InfoRow
import com.lnantes.uifirmaelectronica.ui.components.OperationLogPanel
import com.lnantes.uifirmaelectronica.ui.components.PageHeader
import com.lnantes.uifirmaelectronica.ui.components.PrimaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.ProcessStepper
import com.lnantes.uifirmaelectronica.ui.components.QuietActionButton
import com.lnantes.uifirmaelectronica.ui.components.SecondaryActionButton
import com.lnantes.uifirmaelectronica.ui.components.SectionCard
import com.lnantes.uifirmaelectronica.ui.components.StatusPill
import com.lnantes.uifirmaelectronica.ui.components.SyncAnimation
import com.lnantes.uifirmaelectronica.ui.components.TokenAnimation
import com.lnantes.uifirmaelectronica.ui.presentation.DeepLinkPhase
import com.lnantes.uifirmaelectronica.ui.presentation.DeepLinkSignaturePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.DeepLinkUiEvent
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

/**
 * Pantalla que se abre sólo cuando la aplicación se inicia desde el deep link.
 * Cada firmante elige su modalidad; el orden es recibe → entrega.
 */
@Composable
fun DeepLinkSignatureView(
    presenter: DeepLinkSignaturePresenter,
    onOpenFullApp: () -> Unit,
) {
    val state by presenter.state.collectAsState()
    val send = presenter::onEvent

    val paso = when (state.phase) {
        DeepLinkPhase.CARGANDO -> 0
        DeepLinkPhase.LISTO -> 1
        DeepLinkPhase.FIRMANDO_RECEPTOR,
        DeepLinkPhase.FIRMANDO_OPERADOR -> 2
        DeepLinkPhase.FINALIZADO -> 3
    }
    val bloqueado = state.busy || state.phase == DeepLinkPhase.FINALIZADO

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        PageHeader(
            eyebrow = "Firma desde enlace profundo",
            title = "Recibo ${state.codigo}",
            description = "Los datos llegan de Laravel y el PDF se genera en local. Deben firmar quien recibe la ropa y el funcionario que entrega; cada uno elige su modalidad.",
            trailing = {
                StatusPill(
                    text = when (state.phase) {
                        DeepLinkPhase.CARGANDO -> "Obteniendo recibo"
                        DeepLinkPhase.LISTO -> "Listo para firmar"
                        DeepLinkPhase.FIRMANDO_RECEPTOR -> "Firma de quien recibe"
                        DeepLinkPhase.FIRMANDO_OPERADOR -> "Firma del operador"
                        DeepLinkPhase.FINALIZADO -> "Firma completada"
                    },
                    level = when (state.phase) {
                        DeepLinkPhase.CARGANDO,
                        DeepLinkPhase.FIRMANDO_RECEPTOR,
                        DeepLinkPhase.FIRMANDO_OPERADOR -> LogLevel.WARNING
                        else -> LogLevel.SUCCESS
                    },
                )
            },
        )

        Spacer(Modifier.height(24.dp))
        ProcessStepper(
            steps = listOf("Obtener recibo", "Elegir firmantes", "Firmar", "Guardar y limpiar"),
            currentIndex = paso,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(modifier = Modifier.weight(1.3f)) {
                SectionCard(
                    title = "1 · Recibo temporal",
                    subtitle = "Datos leídos desde Laravel y PDF generado en local",
                    icon = AppIcon.PDF,
                ) {
                    val receipt = state.receipt
                    if (receipt == null) {
                        Text(
                            "Generando el comprobante…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        InfoRow(AppIcon.INFO, "Código", receipt.number)
                        InfoRow(AppIcon.COMMON, "Quien recibe", receipt.signer.ifBlank { "Sin dato" })
                        InfoRow(AppIcon.PEN, "Quien entrega", receipt.entregador.ifBlank { "Sin dato" })
                        InfoRow(AppIcon.INFO, "Concepto", receipt.concept)
                        InfoRow(AppIcon.CLOCK, "Fecha del recibo", receipt.issuedAt)
                        InfoRow(
                            AppIcon.SAVE,
                            "Archivo base",
                            if (state.temporalesEliminados) "Eliminado (temporal)" else state.archivoBase.orEmpty(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    QuietActionButton(
                        "Generar el recibo de nuevo",
                        AppIcon.REFRESH,
                        { send(DeepLinkUiEvent.Regenerar) },
                        enabled = !state.busy,
                    )
                }

                Spacer(Modifier.height(16.dp))
                SectionCard(
                    title = "2 · Firmantes y modalidad",
                    subtitle = "Normativa: ambas opciones para cada firmante",
                    icon = AppIcon.PEN,
                ) {
                    val receptor = state.receipt?.signer.orEmpty().ifBlank { "Quien recibe" }
                    val operador = state.receipt?.entregador.orEmpty().ifBlank { "Funcionario que entrega" }
                    FirmanteOption(
                        orden = "1",
                        titulo = "Quien recibe la ropa",
                        nombre = receptor,
                        lado = LadoFirma.RECIBE,
                        seleccionado = state.metodoReceptor,
                        aplicado = state.firmaReceptorAplicada,
                        habilitado = !bloqueado,
                        onSeleccionar = { send(DeepLinkUiEvent.SeleccionarMetodoReceptor(it)) },
                    )
                    Spacer(Modifier.height(12.dp))
                    FirmanteOption(
                        orden = "2",
                        titulo = "Funcionario que entrega",
                        nombre = operador,
                        lado = LadoFirma.ENTREGA,
                        seleccionado = state.metodoOperador,
                        aplicado = state.firmaOperadorAplicada,
                        habilitado = !bloqueado,
                        onSeleccionar = { send(DeepLinkUiEvent.SeleccionarMetodoOperador(it)) },
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Se captura primero la firma de quien recibe y después la del funcionario que entrega. " +
                            "Cada firma se estampa en su columna. Las firmas avanzadas se aplican al final " +
                            "para que la firma digital cubra el documento completo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.tokenDisponible) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "El token del enlace vence a los 3 minutos: firme enseguida.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Warning,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "El enlace no incluyó token: el envío a Laravel puede ser rechazado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Warning,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (state.conexionAoaFallida) {
                        Spacer(Modifier.height(12.dp))
                        SecondaryActionButton(
                            text = "Reintentar conexión AOA",
                            icon = AppIcon.SYNC,
                            onClick = { send(DeepLinkUiEvent.ReintentarConexion) },
                            enabled = !state.busy,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    PrimaryActionButton(
                        text = when {
                            state.phase == DeepLinkPhase.FINALIZADO -> "Recibo ya firmado"
                            state.firmaReceptorAplicada -> "Continuar con el operador"
                            else -> "Firmar ambos recibos"
                        },
                        icon = AppIcon.LOCK,
                        onClick = { send(DeepLinkUiEvent.Firmar) },
                        enabled = state.puedeFirmar,
                        loading = state.busy && state.phase != DeepLinkPhase.CARGANDO,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                SectionCard(
                    title = "3 · Archivos guardados",
                    subtitle = "Sólo queda el recibo firmado",
                    icon = AppIcon.SAVE,
                ) {
                    ArchivoRow(
                        "Recibo base (temporal)",
                        if (state.temporalesEliminados) "Eliminado" else state.archivoBase,
                    )
                    ArchivoRow(
                        "Firma de quien recibe",
                        if (state.temporalesEliminados && state.firmaOperadorAplicada &&
                            state.archivoReceptor == state.archivoFinal
                        ) "Eliminado (intermedio)" else state.archivoReceptor,
                    )
                    ArchivoRow("Firma del operador", state.archivoOperador)
                    val final = state.archivoFinal
                    if (final != null && state.phase == DeepLinkPhase.FINALIZADO) {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(AppColors.Success.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIconGlyph(AppIcon.CHECK, AppColors.Success, Modifier.size(19.dp), 2.4f)
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    "Archivo final guardado",
                                    color = AppColors.Success,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(final, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Los PDF temporales ya se eliminaron.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Success,
                            )
                        }
                    }
                }

                if (state.phase == DeepLinkPhase.FIRMANDO_RECEPTOR) {
                    Spacer(Modifier.height(16.dp))
                    SyncAnimation(active = true, completed = false)
                }
                if (state.phase == DeepLinkPhase.FIRMANDO_OPERADOR &&
                    state.metodoOperador == MetodoFirma.AVANZADA
                ) {
                    Spacer(Modifier.height(16.dp))
                    TokenAnimation(active = true, completed = false)
                }

                Spacer(Modifier.height(16.dp))
                OperationLogPanel(logs = state.log)
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = AppColors.Error)
                }
                Spacer(Modifier.height(10.dp))
                QuietActionButton("Limpiar registro", AppIcon.REFRESH, { send(DeepLinkUiEvent.LimpiarLog) })
                QuietActionButton("Abrir aplicación completa", AppIcon.ARROW_RIGHT, onOpenFullApp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FirmanteOption(
    orden: String,
    titulo: String,
    nombre: String,
    lado: LadoFirma,
    seleccionado: MetodoFirma,
    aplicado: Boolean,
    habilitado: Boolean,
    onSeleccionar: (MetodoFirma) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                ) {
                    Text(
                        orden,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$nombre · columna ${lado.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (aplicado) {
                    StatusPill(text = "Firmado", level = LogLevel.SUCCESS)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetodoButton(
                    texto = MetodoFirma.COMUN.etiqueta,
                    activo = seleccionado == MetodoFirma.COMUN,
                    habilitado = habilitado && !aplicado,
                    onClick = { onSeleccionar(MetodoFirma.COMUN) },
                    modifier = Modifier.weight(1f),
                )
                MetodoButton(
                    texto = MetodoFirma.AVANZADA.etiqueta,
                    activo = seleccionado == MetodoFirma.AVANZADA,
                    habilitado = habilitado && !aplicado,
                    onClick = { onSeleccionar(MetodoFirma.AVANZADA) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetodoButton(
    texto: String,
    activo: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { if (habilitado) onClick() },
        shape = RoundedCornerShape(9.dp),
        color = if (activo) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier,
    ) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
            color = if (activo) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ArchivoRow(label: String, fileName: String?) {
    InfoRow(
        icon = if (fileName.isNullOrBlank()) AppIcon.FOLDER else AppIcon.SAVE,
        label = label,
        value = fileName?.takeIf { it.isNotBlank() } ?: "Pendiente",
    )
}

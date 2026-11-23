package com.lnantes.uifirmaelectronica.views.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.core.model.AppDestination
import com.lnantes.uifirmaelectronica.core.model.SignatureMode
import com.lnantes.uifirmaelectronica.ui.components.PageHeader
import com.lnantes.uifirmaelectronica.ui.components.StatusPill
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

@Composable
fun HomeView(onNavigate: (AppDestination) -> Unit) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        PageHeader(
            eyebrow = "Panel principal",
            title = "Seleccione el tipo de firma",
            description = "Elija el flujo que necesita. Cada modo tiene su propio asistente, registro de operaciones y gestor de documentos.",
        )

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ModeCard(
                mode = SignatureMode.COMMON,
                icon = AppIcon.COMMON,
                steps = listOf("Sincronizar tablet", "Capturar firma", "Firmar documento"),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(AppDestination.COMMON) },
            )
            ModeCard(
                mode = SignatureMode.ADVANCED,
                icon = AppIcon.ADVANCED,
                steps = listOf("Generar recibo PDF", "Firmar con token", "Archivar en local"),
                accent = AppColors.Teal,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(AppDestination.ADVANCED) },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShortcutCard(
                icon = AppIcon.RECEIPT,
                title = "Recibos firmados",
                subtitle = "Consultar los comprobantes guardados en local",
                onClick = { onNavigate(AppDestination.RECEIPTS) },
                modifier = Modifier.weight(1f),
            )
            ShortcutCard(
                icon = AppIcon.EXCHANGE,
                title = "Intercambiar recibos",
                subtitle = "Enviar y recibir recibos con otra PC",
                onClick = { onNavigate(AppDestination.EXCHANGE) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconGlyph(AppIcon.SHIELD, AppColors.Success, Modifier.size(21.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Estado del sistema", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    StatusPill("Operativo", com.lnantes.uifirmaelectronica.core.model.LogLevel.SUCCESS)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(14.dp))
                Row {
                    StatusMetric("0", "Operaciones hoy")
                    StatusMetric("3", "Recibos en local")
                    StatusMetric("1", "Tablet vinculada")
                    StatusMetric("OK", "Token físico")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ModeCard(
    mode: SignatureMode,
    icon: AppIcon,
    steps: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIconGlyph(icon, accent, Modifier.size(29.dp), 2.1f)
            }
            Spacer(Modifier.height(18.dp))
            Text(mode.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                mode.headline,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                mode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    AppIconGlyph(AppIcon.CHECK, accent, Modifier.size(15.dp), 2.4f)
                    Spacer(Modifier.width(8.dp))
                    Text(step, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Iniciar flujo",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                AppIconGlyph(AppIcon.ARROW_RIGHT, accent, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    icon: AppIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIconGlyph(icon, MaterialTheme.colorScheme.primary, Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppIconGlyph(AppIcon.CHEVRON_RIGHT, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RowScope.StatusMetric(value: String, label: String) {
    Row(modifier = Modifier.weight(1f)) {
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

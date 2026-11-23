package com.lnantes.uifirmaelectronica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.core.model.LogEntry
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(title, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(20.dp))
            trailing()
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    icon: AppIcon? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconGlyph(icon, MaterialTheme.colorScheme.primary, Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    level: LogLevel,
    modifier: Modifier = Modifier,
) {
    val (background, foreground) = when (level) {
        LogLevel.SUCCESS -> AppColors.Success.copy(alpha = 0.12f) to AppColors.Success
        LogLevel.WARNING -> AppColors.Warning.copy(alpha = 0.12f) to AppColors.Warning
        LogLevel.ERROR -> AppColors.Error.copy(alpha = 0.12f) to AppColors.Error
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(foreground))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OperationLogPanel(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    title: String = "Registro de operaciones",
    emptyMessage: String = "Las acciones aparecerán aquí a medida que se ejecuten.",
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconGlyph(AppIcon.CLOCK, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (logs.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(state = listState, modifier = Modifier.height(150.dp)) {
                items(logs, key = { it.id }) { entry ->
                    LogLine(entry)
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.SUCCESS -> AppColors.Success
        LogLevel.WARNING -> AppColors.Warning
        LogLevel.ERROR -> AppColors.Error
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(7.dp).padding(top = 5.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(9.dp))
        Text(
            entry.time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Indicador de pasos del flujo: los completados se marcan en verde y el actual se resalta.
 */
@Composable
fun ProcessStepper(
    steps: List<String>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, label ->
            val done = index < currentIndex
            val active = index == currentIndex
            val circleColor = when {
                done -> AppColors.Success
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = when {
                done || active -> Color.White
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(circleColor),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    AppIconGlyph(AppIcon.CHECK, contentColor, Modifier.size(15.dp), 2.4f)
                } else {
                    Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium, color = contentColor)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (active || done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
            if (index < steps.lastIndex) {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (done) AppColors.Success else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: AppIcon,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AppIconGlyph(icon, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun InfoRow(
    icon: AppIcon,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconGlyph(icon, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

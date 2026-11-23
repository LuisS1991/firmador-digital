package com.lnantes.uifirmaelectronica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.core.model.AppDestination
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

/**
 * Estructura de escritorio: navegación lateral, cabecera de contexto y área de contenido.
 */
@Composable
fun AppScaffold(
    current: AppDestination,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigate: (AppDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SideNavigation(current = current, onNavigate = onNavigate)
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TopContextBar(
                destination = current,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SideNavigation(
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
) {
    Surface(
        modifier = Modifier.width(248.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIconGlyph(AppIcon.SHIELD, Color.White, Modifier.size(24.dp), 2.1f)
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        text = "FIRMA·UI",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Consola de escritorio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
            NavSectionLabel("OPERACIONES")
            Spacer(Modifier.height(8.dp))
            NavItem(AppDestination.HOME, AppIcon.HOME, current, onNavigate)
            NavItem(AppDestination.COMMON, AppIcon.COMMON, current, onNavigate)
            NavItem(AppDestination.ADVANCED, AppIcon.ADVANCED, current, onNavigate)

            Spacer(Modifier.height(22.dp))
            NavSectionLabel("ARCHIVO Y TRASPASO")
            Spacer(Modifier.height(8.dp))
            NavItem(AppDestination.RECEIPTS, AppIcon.RECEIPT, current, onNavigate)
            NavItem(AppDestination.EXCHANGE, AppIcon.EXCHANGE, current, onNavigate)

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).background(AppColors.Success, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Sistema listo", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Motor local · v1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp),
    )
}

@Composable
private fun NavItem(
    destination: AppDestination,
    icon: AppIcon,
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
) {
    val selected = destination == current
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = { onNavigate(destination) },
        color = background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            AppIconGlyph(icon, foreground, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = destination.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun TopContextBar(
    destination: AppDestination,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(74.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(destination.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    destination.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill("Entorno local", com.lnantes.uifirmaelectronica.core.model.LogLevel.SUCCESS)
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onToggleTheme) {
                AppIconGlyph(
                    if (darkTheme) AppIcon.SUN else AppIcon.MOON,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "MR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

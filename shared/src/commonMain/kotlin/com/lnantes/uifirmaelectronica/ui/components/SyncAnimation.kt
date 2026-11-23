package com.lnantes.uifirmaelectronica.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

/**
 * Animación de sincronización PC ↔ Tablet. Los puntos se encienden en secuencia
 * mientras la operación está activa y quedan todos en verde al completarse.
 *
 * La transición infinita se aísla en [FlowingDots] para que desaparezca en
 * cuanto la sincronización termina o se reinicia el flujo.
 */
@Composable
fun SyncAnimation(
    active: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            DeviceGlyph(
                icon = AppIcon.COMPUTER,
                label = "PC",
                highlighted = active || completed,
            )
            Spacer(Modifier.width(10.dp))

            if (active) {
                FlowingDots()
            } else {
                StaticDots(completed = completed)
            }

            Spacer(Modifier.width(10.dp))
            DeviceGlyph(
                icon = AppIcon.TABLET,
                label = "TABLET",
                highlighted = completed,
            )
        }
        Spacer(Modifier.height(12.dp))
        val status = when {
            active -> "Intercambiando información…"
            completed -> "Enlace sincronizado"
            else -> "Sin conexión"
        }
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (completed) AppColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active || completed) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FlowingDots() {
    val transition = rememberInfiniteTransition()
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
    )
    val phase = flow.toInt() % 5
    DotsRow(
        litIndexes = setOf(phase, (phase + 4) % 5),
        litColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StaticDots(completed: Boolean) {
    DotsRow(
        litIndexes = if (completed) (0..4).toSet() else emptySet(),
        litColor = AppColors.Success,
    )
}

@Composable
private fun DotsRow(litIndexes: Set<Int>, litColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { index ->
            val lit = index in litIndexes
            Box(
                Modifier
                    .size(if (lit) 11.dp else 8.dp)
                    .background(
                        if (lit) litColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        CircleShape,
                    ),
            )
            if (index < 4) Spacer(Modifier.width(9.dp))
        }
    }
}

@Composable
private fun DeviceGlyph(
    icon: AppIcon,
    label: String,
    highlighted: Boolean,
) {
    val background = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .background(background, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIconGlyph(icon, foreground, Modifier.size(30.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = foreground, fontWeight = FontWeight.Bold)
    }
}

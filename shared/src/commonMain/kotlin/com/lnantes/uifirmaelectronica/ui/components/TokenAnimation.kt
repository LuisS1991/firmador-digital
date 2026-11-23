package com.lnantes.uifirmaelectronica.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.ui.theme.AppColors
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

/**
 * Animación del token físico: anillos pulsantes y flujo de datos hacia el recibo.
 *
 * Las transiciones infinitas viven en sub-componentes que sólo se componen
 * mientras [active] es true. Así, al reiniciar el flujo se eliminan de la
 * composición y dejan de ejecutarse en segundo plano.
 */
@Composable
fun TokenAnimation(
    active: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = when {
        completed -> AppColors.Success
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIconGlyph(AppIcon.PDF, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(32.dp))
            }

            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).height(48.dp)) {
                if (active) {
                    MovingDataFlow(color = accent)
                } else {
                    StaticDataFlow(color = accent, emphasized = completed)
                }
            }
            Spacer(Modifier.width(14.dp))

            Box(contentAlignment = Alignment.Center) {
                if (active) PulsingHalo(color = accent)
                Box(
                    Modifier
                        .size(58.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIconGlyph(AppIcon.TOKEN, accent, Modifier.size(32.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            when {
                active -> "Leyendo certificado del token físico…"
                completed -> "Firma avanzada aplicada correctamente"
                else -> "Token físico en espera"
            },
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            fontWeight = if (active || completed) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Sólo se compone durante la firma: su transición muere al salir. */
@Composable
private fun MovingDataFlow(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
    )
    DataFlowCanvas(color = color, progress = travel, modifier = modifier)
}

/** Estado en reposo o al completar: puntos fijos, sin ninguna animación activa. */
@Composable
private fun StaticDataFlow(
    color: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    DataFlowCanvas(
        color = if (emphasized) AppColors.Success else color.copy(alpha = 0.45f),
        progress = null,
        modifier = modifier,
    )
}

@Composable
private fun DataFlowCanvas(
    color: Color,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val width = size.width
        val middle = size.height / 2f
        val dotRadius = 4.dp.toPx()

        drawLine(
            color = color.copy(alpha = 0.25f),
            start = Offset(0f, middle),
            end = Offset(width, middle),
            strokeWidth = 2.dp.toPx(),
        )

        repeat(4) { index ->
            val x = when (progress) {
                null -> width * (index + 1f) / 5f
                else -> ((progress + index / 4f) % 1f) * width
            }
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(x, middle),
            )
        }
    }
}

/** Sólo se compone durante la firma: el pulso se detiene al reiniciar. */
@Composable
private fun PulsingHalo(color: Color) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    Box(
        Modifier
            .size(72.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .background(color.copy(alpha = 0.14f), CircleShape),
    )
}

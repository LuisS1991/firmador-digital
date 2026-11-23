package com.lnantes.uifirmaelectronica.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lnantes.uifirmaelectronica.core.model.CapturedSignature
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

/**
 * Ventana modal de previsualización de la firma capturada.
 */
@Composable
fun SignaturePreviewDialog(
    signature: CapturedSignature,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.width(660.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconGlyph(AppIcon.EYE, MaterialTheme.colorScheme.primary, Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Previsualización de firma", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Imagen capturada en la tablet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        AppIconGlyph(AppIcon.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(19.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(208.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    SignatureStroke(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                    )
                }

                Spacer(Modifier.height(18.dp))
                InfoLine("Titular", signature.signerName)
                InfoLine("Identificador", signature.id)
                InfoLine("Capturada", signature.capturedAt)
                InfoLine("Trazos registrados", signature.strokeCount.toString())

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Row {
                    QuietActionButton("Cerrar", AppIcon.CLOSE, onDismiss)
                    Spacer(Modifier.weight(1f))
                    if (onConfirm != null) {
                        PrimaryActionButton("Usar esta firma", AppIcon.CHECK, onConfirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * Trazo de firma dibujado de forma vectorial; sustituible por la imagen
 * realmente capturada por el backend.
 */
@Composable
private fun SignatureStroke(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = 3.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val ink = color

        val flourish = Path().apply {
            moveTo(w * 0.06f, h * 0.62f)
            cubicTo(w * 0.13f, h * 0.18f, w * 0.19f, h * 0.20f, w * 0.20f, h * 0.70f)
            cubicTo(w * 0.21f, h * 0.92f, w * 0.27f, h * 0.86f, w * 0.30f, h * 0.55f)
            cubicTo(w * 0.33f, h * 0.25f, w * 0.37f, h * 0.24f, w * 0.38f, h * 0.66f)
            cubicTo(w * 0.39f, h * 0.86f, w * 0.45f, h * 0.84f, w * 0.49f, h * 0.48f)
        }
        val letters = Path().apply {
            moveTo(w * 0.40f, h * 0.68f)
            cubicTo(w * 0.45f, h * 0.30f, w * 0.52f, h * 0.34f, w * 0.50f, h * 0.68f)
            moveTo(w * 0.50f, h * 0.52f)
            cubicTo(w * 0.56f, h * 0.47f, w * 0.60f, h * 0.52f, w * 0.57f, h * 0.68f)
            moveTo(w * 0.58f, h * 0.46f)
            cubicTo(w * 0.63f, h * 0.40f, w * 0.67f, h * 0.50f, w * 0.64f, h * 0.70f)
            moveTo(w * 0.66f, h * 0.50f)
            cubicTo(w * 0.72f, h * 0.44f, w * 0.76f, h * 0.54f, w * 0.73f, h * 0.70f)
        }
        val underline = Path().apply {
            moveTo(w * 0.08f, h * 0.84f)
            cubicTo(w * 0.30f, h * 0.78f, w * 0.60f, h * 0.88f, w * 0.90f, h * 0.78f)
        }

        drawPath(flourish, ink, style = stroke)
        drawPath(letters, ink, style = stroke)
        drawPath(underline, ink.copy(alpha = 0.6f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = ink.copy(alpha = 0.18f),
            start = Offset(w * 0.05f, h * 0.94f),
            end = Offset(w * 0.95f, h * 0.94f),
            strokeWidth = 1.dp.toPx(),
        )
        drawCircle(ink.copy(alpha = 0.08f), radius = 26.dp.toPx(), center = Offset(w * 0.88f, h * 0.28f))
        drawRect(ink.copy(alpha = 0.04f), topLeft = Offset.Zero, size = Size(w, h))
    }
}

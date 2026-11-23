package com.lnantes.uifirmaelectronica.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Glifos propios dibujados con Canvas: evita depender de librerías de iconos
 * y mantiene el mismo estilo en todas las plataformas.
 */
enum class AppIcon {
    HOME, COMMON, ADVANCED, RECEIPT, EXCHANGE,
    TABLET, COMPUTER, PEN, PDF, TOKEN, SYNC,
    CHECK, CLOSE, EYE, REFRESH, FOLDER,
    DOWNLOAD, UPLOAD, LOCK, SHIELD, DOCUMENT,
    PLUS, SEARCH, INFO, WARNING, CLOCK,
    CHEVRON_RIGHT, ARROW_RIGHT, SUN, MOON,
    SAVE, NETWORK, BACK, DELETE,
}

@Composable
fun AppIconGlyph(
    icon: AppIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.9f,
) {
    Canvas(modifier) {
        val canvasSize = size.minDimension
        val unit = canvasSize / 24f
        val stroke = Stroke(
            width = strokeWidth.dp.toPx().coerceAtLeast(1.4f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), stroke.width)
        fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float = 2f) =
            drawRoundRect(
                color = tint,
                topLeft = p(x, y),
                size = androidx.compose.ui.geometry.Size(w * unit, h * unit),
                cornerRadius = CornerRadius(radius * unit),
                style = stroke,
            )

        fun path(block: Path.() -> Unit) {
            val shape = Path().apply(block)
            drawPath(shape, tint, style = stroke)
        }

        when (icon) {
            AppIcon.HOME -> path {
                moveTo(3f * unit, 11f * unit); lineTo(12f * unit, 4f * unit); lineTo(21f * unit, 11f * unit)
                moveTo(5.5f * unit, 10f * unit); lineTo(5.5f * unit, 20f * unit); lineTo(18.5f * unit, 20f * unit); lineTo(18.5f * unit, 10f * unit)
                moveTo(10f * unit, 20f * unit); lineTo(10f * unit, 14.5f * unit); lineTo(14f * unit, 14.5f * unit); lineTo(14f * unit, 20f * unit)
            }
            AppIcon.COMMON -> path {
                moveTo(4f * unit, 20f * unit); lineTo(4.8f * unit, 16.5f * unit); lineTo(16.8f * unit, 4.5f * unit)
                cubicTo(17.8f * unit, 3.5f * unit, 19.5f * unit, 3.6f * unit, 20.4f * unit, 4.5f * unit)
                cubicTo(21.3f * unit, 5.4f * unit, 21.3f * unit, 7f * unit, 20.3f * unit, 8f * unit)
                lineTo(8.2f * unit, 19.8f * unit); close()
                moveTo(14.8f * unit, 6.5f * unit); lineTo(18.5f * unit, 10.2f * unit)
                moveTo(3f * unit, 22f * unit); lineTo(21f * unit, 22f * unit)
            }
            AppIcon.ADVANCED -> path {
                moveTo(12f * unit, 2.5f * unit); lineTo(20f * unit, 5.8f * unit); lineTo(20f * unit, 12f * unit)
                cubicTo(20f * unit, 17f * unit, 16.8f * unit, 20.2f * unit, 12f * unit, 21.5f * unit)
                cubicTo(7.2f * unit, 20.2f * unit, 4f * unit, 17f * unit, 4f * unit, 12f * unit)
                lineTo(4f * unit, 5.8f * unit); close()
                moveTo(8.5f * unit, 12f * unit); lineTo(11f * unit, 14.5f * unit); lineTo(15.8f * unit, 9.5f * unit)
            }
            AppIcon.RECEIPT -> path {
                moveTo(5f * unit, 2.5f * unit); lineTo(19f * unit, 2.5f * unit); lineTo(19f * unit, 21.5f * unit)
                lineTo(15.5f * unit, 19f * unit); lineTo(12f * unit, 21.5f * unit); lineTo(8.5f * unit, 19f * unit); lineTo(5f * unit, 21.5f * unit); close()
                moveTo(8.5f * unit, 8f * unit); lineTo(15.5f * unit, 8f * unit)
                moveTo(8.5f * unit, 12f * unit); lineTo(15.5f * unit, 12f * unit)
                moveTo(8.5f * unit, 16f * unit); lineTo(12.5f * unit, 16f * unit)
            }
            AppIcon.EXCHANGE -> path {
                moveTo(3.5f * unit, 8.5f * unit); lineTo(18f * unit, 8.5f * unit)
                moveTo(15f * unit, 5.5f * unit); lineTo(18.5f * unit, 8.5f * unit); lineTo(15f * unit, 11.5f * unit)
                moveTo(20.5f * unit, 15.5f * unit); lineTo(6f * unit, 15.5f * unit)
                moveTo(9f * unit, 12.5f * unit); lineTo(5.5f * unit, 15.5f * unit); lineTo(9f * unit, 18.5f * unit)
            }
            AppIcon.TABLET -> {
                rect(4.5f, 2.5f, 15f, 19f, 2f)
                line(10.5f, 18.7f, 13.5f, 18.7f)
            }
            AppIcon.COMPUTER -> {
                rect(3f, 4f, 18f, 12.5f, 1.5f)
                line(8.5f, 20f, 15.5f, 20f)
                line(12f, 16.5f, 12f, 20f)
            }
            AppIcon.PEN -> path {
                moveTo(5f * unit, 19f * unit); lineTo(5.8f * unit, 15.5f * unit); lineTo(16.6f * unit, 4.7f * unit)
                lineTo(19.3f * unit, 7.4f * unit); lineTo(8.5f * unit, 18.2f * unit); close()
                moveTo(3f * unit, 22f * unit); lineTo(21f * unit, 22f * unit)
            }
            AppIcon.PDF -> {
                rect(4f, 2.5f, 16f, 19f, 2f)
                line(8f, 8f, 16f, 8f)
                line(8f, 11.5f, 16f, 11.5f)
                line(8f, 15f, 13f, 15f)
            }
            AppIcon.TOKEN -> {
                rect(8f, 2.5f, 8f, 19f, 2f)
                line(10.5f, 6f, 13.5f, 6f)
                drawCircle(tint, 1.6f * unit, p(12f, 10f))
                line(10f, 15f, 14f, 15f)
                line(10f, 17.5f, 14f, 17.5f)
            }
            AppIcon.SYNC -> path {
                moveTo(4.5f * unit, 9f * unit)
                cubicTo(5.5f * unit, 5f * unit, 9f * unit, 3f * unit, 13f * unit, 3.5f * unit)
                cubicTo(16f * unit, 3.8f * unit, 18.5f * unit, 5.8f * unit, 19.5f * unit, 8f * unit)
                moveTo(17f * unit, 5.5f * unit); lineTo(19.8f * unit, 8.2f * unit); lineTo(16.8f * unit, 9.5f * unit)
                moveTo(19.5f * unit, 15f * unit)
                cubicTo(18.5f * unit, 19f * unit, 15f * unit, 21f * unit, 11f * unit, 20.5f * unit)
                cubicTo(8f * unit, 20.2f * unit, 5.5f * unit, 18.2f * unit, 4.5f * unit, 16f * unit)
                moveTo(7f * unit, 18.5f * unit); lineTo(4.2f * unit, 15.8f * unit); lineTo(7.2f * unit, 14.5f * unit)
            }
            AppIcon.CHECK -> {
                line(4f, 12.5f, 9.5f, 18f)
                line(9.5f, 18f, 20f, 6f)
            }
            AppIcon.CLOSE -> {
                line(5f, 5f, 19f, 19f)
                line(19f, 5f, 5f, 19f)
            }
            AppIcon.EYE -> path {
                moveTo(2f * unit, 12f * unit)
                cubicTo(5f * unit, 7f * unit, 8.5f * unit, 5f * unit, 12f * unit, 5f * unit)
                cubicTo(15.5f * unit, 5f * unit, 19f * unit, 7f * unit, 22f * unit, 12f * unit)
                cubicTo(19f * unit, 17f * unit, 15.5f * unit, 19f * unit, 12f * unit, 19f * unit)
                cubicTo(8.5f * unit, 19f * unit, 5f * unit, 17f * unit, 2f * unit, 12f * unit); close()
            }
            AppIcon.REFRESH -> path {
                moveTo(20f * unit, 11f * unit)
                cubicTo(19.2f * unit, 6.5f * unit, 15.5f * unit, 3.5f * unit, 11f * unit, 4f * unit)
                cubicTo(7f * unit, 4.4f * unit, 4.3f * unit, 7f * unit, 3.5f * unit, 10f * unit)
                moveTo(3f * unit, 5f * unit); lineTo(3.2f * unit, 10.5f * unit); lineTo(8.5f * unit, 10f * unit)
                moveTo(4f * unit, 13f * unit)
                cubicTo(4.8f * unit, 17.5f * unit, 8.5f * unit, 20.5f * unit, 13f * unit, 20f * unit)
                cubicTo(17f * unit, 19.6f * unit, 19.7f * unit, 17f * unit, 20.5f * unit, 14f * unit)
                moveTo(21f * unit, 19f * unit); lineTo(20.8f * unit, 13.5f * unit); lineTo(15.5f * unit, 14f * unit)
            }
            AppIcon.FOLDER -> path {
                moveTo(2.5f * unit, 6f * unit); lineTo(9f * unit, 6f * unit); lineTo(11f * unit, 8.5f * unit)
                lineTo(21.5f * unit, 8.5f * unit); lineTo(21.5f * unit, 19f * unit); lineTo(2.5f * unit, 19f * unit); close()
            }
            AppIcon.DOWNLOAD -> {
                line(12f, 3f, 12f, 16f)
                line(7f, 11.5f, 12f, 16.5f)
                line(17f, 11.5f, 12f, 16.5f)
                line(4f, 20f, 20f, 20f)
            }
            AppIcon.UPLOAD -> {
                line(12f, 16f, 12f, 3f)
                line(7f, 7.5f, 12f, 2.5f)
                line(17f, 7.5f, 12f, 2.5f)
                line(4f, 20f, 20f, 20f)
            }
            AppIcon.LOCK -> {
                rect(5f, 10f, 14f, 10f, 2f)
                path {
                    moveTo(8f * unit, 10f * unit); lineTo(8f * unit, 7f * unit)
                    cubicTo(8f * unit, 4f * unit, 10f * unit, 2.5f * unit, 12f * unit, 2.5f * unit)
                    cubicTo(14f * unit, 2.5f * unit, 16f * unit, 4f * unit, 16f * unit, 7f * unit)
                    lineTo(16f * unit, 10f * unit)
                }
                drawCircle(tint, 1.3f * unit, p(12f, 15f))
            }
            AppIcon.SHIELD -> path {
                moveTo(12f * unit, 2f * unit); lineTo(20f * unit, 5f * unit); lineTo(20f * unit, 11f * unit)
                cubicTo(20f * unit, 16.5f * unit, 16.7f * unit, 20.2f * unit, 12f * unit, 22f * unit)
                cubicTo(7.3f * unit, 20.2f * unit, 4f * unit, 16.5f * unit, 4f * unit, 11f * unit)
                lineTo(4f * unit, 5f * unit); close()
                drawCircle(tint, 2f * unit, p(12f, 11.5f))
            }
            AppIcon.DOCUMENT -> {
                rect(4f, 2.5f, 16f, 19f, 1.5f)
                line(7.5f, 8f, 16.5f, 8f)
                line(7.5f, 12f, 16.5f, 12f)
                line(7.5f, 16f, 13f, 16f)
            }
            AppIcon.PLUS -> {
                line(12f, 4f, 12f, 20f)
                line(4f, 12f, 20f, 12f)
            }
            AppIcon.SEARCH -> {
                drawCircle(tint, 6.5f * unit, p(10.5f, 10.5f), style = stroke)
                line(15.5f, 15.5f, 21f, 21f)
            }
            AppIcon.INFO -> {
                drawCircle(tint, 9f * unit, p(12f, 12f), style = stroke)
                line(12f, 11f, 12f, 17f)
                drawCircle(tint, 1f * unit, p(12f, 7.5f))
            }
            AppIcon.WARNING -> path {
                moveTo(12f * unit, 3f * unit); lineTo(22f * unit, 20f * unit); lineTo(2f * unit, 20f * unit); close()
                moveTo(12f * unit, 9f * unit); lineTo(12f * unit, 14.5f * unit)
                drawCircle(tint, 1f * unit, p(12f, 17.5f))
            }
            AppIcon.CLOCK -> {
                drawCircle(tint, 9f * unit, p(12f, 12f), style = stroke)
                line(12f, 7f, 12f, 12.5f)
                line(12f, 12.5f, 16f, 15f)
            }
            AppIcon.CHEVRON_RIGHT -> {
                line(9f, 5f, 16f, 12f)
                line(16f, 12f, 9f, 19f)
            }
            AppIcon.ARROW_RIGHT -> {
                line(3f, 12f, 20f, 12f)
                line(15f, 7f, 20.5f, 12f)
                line(15f, 17f, 20.5f, 12f)
            }
            AppIcon.SUN -> {
                drawCircle(tint, 4f * unit, p(12f, 12f), style = stroke)
                listOf(0, 45, 90, 135, 180, 225, 270, 315).forEach { angle ->
                    val rad = Math.toRadians(angle.toDouble())
                    val x1 = 12f + 7f * kotlin.math.cos(rad).toFloat()
                    val y1 = 12f + 7f * kotlin.math.sin(rad).toFloat()
                    val x2 = 12f + 9.5f * kotlin.math.cos(rad).toFloat()
                    val y2 = 12f + 9.5f * kotlin.math.sin(rad).toFloat()
                    line(x1, y1, x2, y2)
                }
            }
            AppIcon.MOON -> path {
                moveTo(20f * unit, 14f * unit)
                cubicTo(18.5f * unit, 19f * unit, 14f * unit, 22f * unit, 9f * unit, 20f * unit)
                cubicTo(5f * unit, 18.5f * unit, 3f * unit, 14.5f * unit, 4f * unit, 10f * unit)
                cubicTo(6f * unit, 15f * unit, 10f * unit, 17f * unit, 14f * unit, 15f * unit)
                cubicTo(16.5f * unit, 14f * unit, 18.5f * unit, 12f * unit, 20f * unit, 9f * unit)
                cubicTo(21f * unit, 10.5f * unit, 21f * unit, 12.5f * unit, 20f * unit, 14f * unit); close()
            }
            AppIcon.SAVE -> path {
                moveTo(4f * unit, 4f * unit); lineTo(16f * unit, 4f * unit); lineTo(20f * unit, 8f * unit)
                lineTo(20f * unit, 20f * unit); lineTo(4f * unit, 20f * unit); close()
                moveTo(8f * unit, 4f * unit); lineTo(8f * unit, 10f * unit); lineTo(16f * unit, 10f * unit); lineTo(16f * unit, 4f * unit)
                rect(8f, 13f, 8f, 7f, 1f)
            }
            AppIcon.NETWORK -> {
                rect(8.5f, 2.5f, 7f, 5f, 1f)
                rect(1.5f, 16.5f, 7f, 5f, 1f)
                rect(15.5f, 16.5f, 7f, 5f, 1f)
                line(12f, 7.5f, 12f, 12f)
                line(5f, 16.5f, 5f, 12f)
                line(19f, 16.5f, 19f, 12f)
                line(5f, 12f, 19f, 12f)
            }
            AppIcon.BACK -> {
                line(20f, 12f, 4f, 12f)
                line(9f, 7f, 3.5f, 12f)
                line(9f, 17f, 3.5f, 12f)
            }
            AppIcon.DELETE -> path {
                moveTo(4f * unit, 6f * unit); lineTo(20f * unit, 6f * unit)
                moveTo(9f * unit, 6f * unit); lineTo(9f * unit, 3.5f * unit); lineTo(15f * unit, 3.5f * unit); lineTo(15f * unit, 6f * unit)
                moveTo(6f * unit, 6f * unit); lineTo(7f * unit, 21f * unit); lineTo(17f * unit, 21f * unit); lineTo(18f * unit, 6f * unit)
                moveTo(10f * unit, 10f * unit); lineTo(10f * unit, 17f * unit)
                moveTo(14f * unit, 10f * unit); lineTo(14f * unit, 17f * unit)
            }
        }
    }
}

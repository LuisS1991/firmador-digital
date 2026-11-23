package com.lnantes.uifirmaelectronica.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppColors {
    val Brand = Color(0xFF1D4ED8)
    val BrandDark = Color(0xFF0F2F73)
    val Teal = Color(0xFF0E7490)
    val Success = Color(0xFF15803D)
    val Warning = Color(0xFFB45309)
    val Error = Color(0xFFB91C1C)
    val SurfaceMuted = Color(0xFFF1F5FB)
    val Border = Color(0xFFDCE4F0)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE8FF),
    onPrimaryContainer = Color(0xFF0F2F73),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F1F6),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFF6D28D9),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF172033),
    surface = Color.White,
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFF5A6780),
    outline = Color(0xFFCBD5E5),
    error = Color(0xFFB91C1C),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CB4FF),
    onPrimary = Color(0xFF0B1A3A),
    primaryContainer = Color(0xFF1B3A78),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF67D6E8),
    onSecondary = Color(0xFF062A33),
    secondaryContainer = Color(0xFF0F4753),
    onSecondaryContainer = Color(0xFFD2F4FA),
    tertiary = Color(0xFFC4B5FD),
    background = Color(0xFF0C1322),
    onBackground = Color(0xFFE8EDF7),
    surface = Color(0xFF141D30),
    onSurface = Color(0xFFE8EDF7),
    surfaceVariant = Color(0xFF1E2A41),
    onSurfaceVariant = Color(0xFFA6B2C9),
    outline = Color(0xFF3A4861),
    error = Color(0xFFFFA3A3),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}

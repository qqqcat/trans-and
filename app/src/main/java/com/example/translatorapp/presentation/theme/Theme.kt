package com.example.translatorapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF4B5DFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE2FF),
    onSecondaryContainer = Color(0xFF081357),
    tertiary = Color(0xFF22A699),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB9F4EA),
    onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFF6F8FF),
    onBackground = Color(0xFF0F1326),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F33),
    surfaceVariant = Color(0xFFE0E4F6),
    onSurfaceVariant = Color(0xFF434A63),
    outline = Color(0xFF717899),
    outlineVariant = Color(0xFFC5CAE0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C3FF),
    onPrimary = Color(0xFF001B5C),
    primaryContainer = Color(0xFF09318E),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFBCC2FF),
    onSecondary = Color(0xFF111B68),
    secondaryContainer = Color(0xFF2E3EAF),
    onSecondaryContainer = Color(0xFFE0E6FF),
    tertiary = Color(0xFF7ADACE),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFB9F4EA),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE1E4F6),
    surface = Color(0xFF14192C),
    onSurface = Color(0xFFE1E4F6),
    surfaceVariant = Color(0xFF3B415A),
    onSurfaceVariant = Color(0xFFC5CAE0),
    outline = Color(0xFF8D93AB),
    outlineVariant = Color(0xFF41475F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val TranslatorTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

private val TranslatorShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val spacing = TranslatorSpacing()
    val radius = TranslatorRadius()
    val elevation = TranslatorElevation()

    CompositionLocalProvider(
        LocalSpacing provides spacing,
        LocalRadius provides radius,
        LocalElevation provides elevation
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TranslatorTypography,
            shapes = TranslatorShapes,
            content = content
        )
    }
}

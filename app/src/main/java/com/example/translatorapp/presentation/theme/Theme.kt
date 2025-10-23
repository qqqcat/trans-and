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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.translatorapp.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF355A52),
    onPrimary = Color(0xFFE2EEEB),
    primaryContainer = Color(0xFF56786F),
    onPrimaryContainer = Color(0xFFE9F5F2),
    secondary = Color(0xFF3F6067),
    onSecondary = Color(0xFFDCE7E9),
    secondaryContainer = Color(0xFF5A7C83),
    onSecondaryContainer = Color(0xFFE7F2F4),
    tertiary = Color(0xFF45657B),
    onTertiary = Color(0xFFE1EDF5),
    tertiaryContainer = Color(0xFF5F7D93),
    onTertiaryContainer = Color(0xFFE9F4FB),
    background = Color(0xFFC2D1CC),
    onBackground = Color(0xFF192A27),
    surface = Color(0xFFCDDAD6),
    onSurface = Color(0xFF1E2F2B),
    surfaceVariant = Color(0xFFA4B5B0),
    onSurfaceVariant = Color(0xFF2F3E3B),
    outline = Color(0xFF516360),
    outlineVariant = Color(0xFF859693),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFA9FDEF),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF00363E),
    secondaryContainer = Color(0xFF005F6B),
    onSecondaryContainer = Color(0xFFB8EAFF),
    tertiary = Color(0xFF4FC3F7),
    onTertiary = Color(0xFF002E3B),
    tertiaryContainer = Color(0xFF00506A),
    onTertiaryContainer = Color(0xFFB6EAFF),
    background = Color(0xFF071E26),
    onBackground = Color(0xFFE1F6FF),
    surface = Color(0xFF0D252D),
    onSurface = Color(0xFFE1F6FF),
    surfaceVariant = Color(0xFF244049),
    onSurfaceVariant = Color(0xFFC0D8DF),
    outline = Color(0xFF78959D),
    outlineVariant = Color(0xFF2F4D55),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightGradients = TranslatorGradientPalette(
    background = Brush.linearGradient(
        listOf(
            Color(0xFFD0DED9),
            Color(0xFFBECFCA)
        )
    ),
    card = Brush.linearGradient(
        listOf(
            Color(0xFFD8E4DF),
            Color(0xFFC6D6D1)
        )
    ),
    accent = Brush.linearGradient(
        listOf(
            Color(0xFF5A8177),
            Color(0xFF4F758A)
        )
    )
)

private val DarkGradients = TranslatorGradientPalette(
    background = Brush.linearGradient(
        listOf(
            Color(0xFF04161C),
            Color(0xFF102A43)
        )
    ),
    card = Brush.linearGradient(
        listOf(
            Color(0xFF102B33),
            Color(0xFF143746)
        )
    ),
    accent = Brush.linearGradient(
        listOf(
            Color(0xFF1F6F78),
            Color(0xFF1565C0)
        )
    )
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
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    val colorScheme = if (darkTheme) DarkColors else LightColors
    val spacing = TranslatorSpacing()
    val radius = TranslatorRadius()
    val elevation = TranslatorElevation()
    val gradients = if (darkTheme) DarkGradients else LightGradients

    CompositionLocalProvider(
        LocalSpacing provides spacing,
        LocalRadius provides radius,
        LocalElevation provides elevation,
        LocalGradients provides gradients
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TranslatorTypography,
            shapes = TranslatorShapes,
            content = content
        )
    }
}

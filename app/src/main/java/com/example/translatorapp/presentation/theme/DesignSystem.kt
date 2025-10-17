package com.example.translatorapp.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class TranslatorSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp
) {
    val tiny: Dp get() = xxs
    val extraSmall: Dp get() = xs
    val small: Dp get() = sm
    val medium: Dp get() = md
    val large: Dp get() = lg
    val extraLarge: Dp get() = xl
}

data class TranslatorRadius(
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp
)

data class TranslatorElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 10.dp
)

val LocalSpacing = staticCompositionLocalOf { TranslatorSpacing() }
val LocalRadius = staticCompositionLocalOf { TranslatorRadius() }
val LocalElevation = staticCompositionLocalOf { TranslatorElevation() }

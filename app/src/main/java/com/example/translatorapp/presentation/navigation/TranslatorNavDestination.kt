package com.example.translatorapp.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.translatorapp.R
import com.example.translatorapp.presentation.theme.LocalElevation

sealed class TranslatorDestination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Home : TranslatorDestination("home", R.string.home_title, Icons.Outlined.Translate)
    data object History :
        TranslatorDestination("history", R.string.history_title, Icons.Outlined.History)

    data object Settings :
        TranslatorDestination("settings", R.string.settings_title, Icons.Outlined.Settings)
}

val BottomBarDestinations = listOf(
    TranslatorDestination.Home,
    TranslatorDestination.History,
    TranslatorDestination.Settings
)

class TranslatorAppState(
    val navController: NavHostController,
    val snackbarHostState: SnackbarHostState
) {
    fun navigateTo(destination: TranslatorDestination) {
        val targetRoute = destination.route
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun TranslatorBottomBar(
    destinations: List<TranslatorDestination>,
    currentDestination: TranslatorDestination?,
    onNavigateToDestination: (TranslatorDestination) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val elevation = LocalElevation.current
    NavigationBar(
        tonalElevation = elevation.level1,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurfaceVariant
    ) {
        destinations.forEach { destination ->
            val selected = currentDestination == destination
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = {
                    Text(
                        text = stringResource(destination.titleRes),
                        maxLines = 1
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    selectedTextColor = colorScheme.primary,
                    unselectedIconColor = colorScheme.onSurfaceVariant,
                    unselectedTextColor = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    indicatorColor = colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}

fun NavDestination?.toDestination(): TranslatorDestination? =
    when (this?.route) {
        TranslatorDestination.Home.route -> TranslatorDestination.Home
        TranslatorDestination.History.route -> TranslatorDestination.History
        TranslatorDestination.Settings.route -> TranslatorDestination.Settings
        else -> null
    }

@Composable
fun rememberTranslatorAppState(
    navController: NavHostController = rememberNavController(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
): TranslatorAppState = remember(navController, snackbarHostState) {
    TranslatorAppState(navController, snackbarHostState)
}

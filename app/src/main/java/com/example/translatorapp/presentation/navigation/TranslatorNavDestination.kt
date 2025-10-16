package com.example.translatorapp.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.example.translatorapp.R
import com.example.translatorapp.presentation.history.HistoryRoute
import com.example.translatorapp.presentation.history.HistoryViewModel
import androidx.hilt.navigation.compose.hiltViewModel

enum class TranslatorNavDestination(val route: String, @StringRes val titleResId: Int) {
    Home("home", R.string.home_title),
    Settings("settings", R.string.settings_title),
    History("history", R.string.history_title);

    companion object {
        fun fromRoute(route: String?): TranslatorNavDestination? =
            entries.firstOrNull { it.route == route }
    }
}

class TranslatorAppState(
    val navController: NavController,
) {
    @Composable
    fun BottomBar(current: TranslatorNavDestination?) {
        NavigationBar {
            TranslatorNavDestination.entries
                .filter { it != TranslatorNavDestination.Settings }
                .forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        label = { Text(text = androidx.compose.ui.res.stringResource(destination.titleResId)) },
                        colors = NavigationBarItemDefaults.colors(),
                        icon = {}
                    )
                }
        }
    }

    @Composable
    fun HistoryScreen(padding: PaddingValues) {
        val viewModel: HistoryViewModel = hiltViewModel()
        HistoryRoute(
            viewModel = viewModel,
            paddingValues = padding,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
fun rememberTranslatorAppState(navController: NavController = rememberNavController()): TranslatorAppState =
    remember(navController) { TranslatorAppState(navController) }

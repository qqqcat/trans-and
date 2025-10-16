package com.example.translatorapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.translatorapp.R
import com.example.translatorapp.presentation.home.HomeRoute
import com.example.translatorapp.presentation.home.HomeViewModel
import com.example.translatorapp.presentation.navigation.TranslatorNavDestination
import com.example.translatorapp.presentation.navigation.rememberTranslatorAppState
import com.example.translatorapp.presentation.settings.SettingsRoute
import com.example.translatorapp.presentation.settings.SettingsViewModel
import com.example.translatorapp.presentation.theme.TranslatorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TranslatorTheme {
                TranslatorApp()
            }
        }
    }
}

@Composable
private fun TranslatorApp() {
    val appState = rememberTranslatorAppState()
    val navController = appState.navController
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TranslatorNavDestination.fromRoute(backStackEntry?.destination?.route)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TranslatorAppTopBar(currentDestination?.titleResId ?: R.string.app_name)
        },
        bottomBar = {
            appState.BottomBar(currentDestination)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TranslatorNavDestination.Home.route,
            modifier = Modifier.fillMaxSize(),
            builder = {
                composable(TranslatorNavDestination.Home.route) {
                    val viewModel: HomeViewModel = hiltViewModel()
                    HomeRoute(
                        viewModel = viewModel,
                        paddingValues = padding,
                        onOpenSettings = { navController.navigate(TranslatorNavDestination.Settings.route) },
                        onOpenHistory = { navController.navigate(TranslatorNavDestination.History.route) }
                    )
                }
                composable(TranslatorNavDestination.Settings.route) {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    SettingsRoute(
                        viewModel = viewModel,
                        paddingValues = padding,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(TranslatorNavDestination.History.route) {
                    appState.HistoryScreen(padding)
                }
            }
        )
    }
}

@Composable
private fun TranslatorAppTopBar(titleRes: Int) {
    TopAppBar(
        title = { Text(text = stringResource(id = titleRes)) }
    )
}

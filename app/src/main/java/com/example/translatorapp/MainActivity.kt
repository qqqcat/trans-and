package com.example.translatorapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.translatorapp.data.datasource.UserPreferencesDataSource
import com.example.translatorapp.presentation.AppPreferencesViewModel
import com.example.translatorapp.presentation.LocaleManager
import com.example.translatorapp.presentation.history.HistoryRoute
import com.example.translatorapp.presentation.history.HistoryViewModel
import com.example.translatorapp.presentation.home.HomeRoute
import com.example.translatorapp.presentation.home.HomeViewModel
import com.example.translatorapp.presentation.navigation.BottomBarDestinations
import com.example.translatorapp.presentation.navigation.TranslatorBottomBar
import com.example.translatorapp.presentation.navigation.TranslatorDestination
import com.example.translatorapp.presentation.navigation.rememberTranslatorAppState
import com.example.translatorapp.presentation.navigation.toDestination
import com.example.translatorapp.presentation.settings.SettingsRoute
import com.example.translatorapp.presentation.settings.SettingsViewModel
import com.example.translatorapp.presentation.theme.LocalGradients
import com.example.translatorapp.presentation.theme.TranslatorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesDataSource: UserPreferencesDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialLanguage = runBlocking {
            userPreferencesDataSource.settings.first().appLanguage
        }
        LocaleManager.applyLocale(initialLanguage)

        setContent {
            val preferencesViewModel: AppPreferencesViewModel = hiltViewModel()
            val themeMode by preferencesViewModel.themeMode.collectAsStateWithLifecycle()

            TranslatorTheme(themeMode = themeMode) {
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
    val currentDestination = backStackEntry?.destination?.toDestination()
    val showBottomBar = currentDestination in BottomBarDestinations
    val gradients = LocalGradients.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradients.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TranslatorAppTopBar(
                    destination = currentDestination,
                    canNavigateBack = currentDestination != null && currentDestination !in BottomBarDestinations && navController.previousBackStackEntry != null,
                    onBackClick = { navController.popBackStack(); Unit }
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    TranslatorBottomBar(
                        destinations = BottomBarDestinations,
                        currentDestination = currentDestination,
                        onNavigateToDestination = appState::navigateTo
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = appState.snackbarHostState) }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = TranslatorDestination.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(TranslatorDestination.Home.route) {
                    val viewModel: HomeViewModel = hiltViewModel()
                    HomeRoute(
                        viewModel = viewModel,
                        paddingValues = padding,
                        onOpenHistory = { appState.navigateTo(TranslatorDestination.History) }
                    )
                }
                composable(TranslatorDestination.History.route) {
                    val viewModel: HistoryViewModel = hiltViewModel()
                    HistoryRoute(
                        viewModel = viewModel,
                        paddingValues = padding
                    )
                }
                composable(TranslatorDestination.Settings.route) {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    SettingsRoute(
                        viewModel = viewModel,
                        paddingValues = padding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslatorAppTopBar(
    destination: TranslatorDestination?,
    canNavigateBack: Boolean,
    onBackClick: () -> Unit
) {
    val titleRes = destination?.titleRes ?: R.string.app_name
    CenterAlignedTopAppBar(
        title = { Text(text = stringResource(id = titleRes)) },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.action_back)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

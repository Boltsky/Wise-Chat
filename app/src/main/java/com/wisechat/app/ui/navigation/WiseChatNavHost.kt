package com.wisechat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wisechat.app.ui.WiseChatViewModel
import com.wisechat.app.ui.screens.analysis.AnalysisScreen
import com.wisechat.app.ui.screens.customstyles.CustomStylesScreen
import com.wisechat.app.ui.screens.history.HistoryScreen
import com.wisechat.app.ui.screens.home.HomeScreen
import com.wisechat.app.ui.screens.onboarding.OnboardingScreen
import com.wisechat.app.ui.screens.providers.ProviderConfigScreen
import com.wisechat.app.ui.screens.replies.RepliesScreen
import com.wisechat.app.ui.screens.settings.SettingsScreen
import com.wisechat.app.ui.screens.styles.StyleSelectorScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ANALYSIS = "analysis"
    const val STYLES = "styles"
    const val REPLIES = "replies"
    const val PROVIDERS = "providers"
    const val CUSTOM_STYLES = "custom_styles"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
}

@Composable
fun WiseChatNavHost(
    viewModel: WiseChatViewModel,
    startDestination: String = Routes.HOME,
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val historyEntries by viewModel.historyEntries.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { route ->
            navController.navigate(route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    viewModel.completeOnboarding()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                uiState = uiState,
                onToggleOverlay = { viewModel.setOverlayEnabled(it) },
                onImageSelected = { uri -> viewModel.processImageUri(uri) },
                onStyleSelected = { style -> viewModel.selectStyle(style) },
                onNavigateToAnalysis = { navController.navigate(Routes.ANALYSIS) },
                onNavigateToProviders = { navController.navigate(Routes.PROVIDERS) },
                onNavigateToStyles = { navController.navigate(Routes.STYLES) },
                onNavigateToCustomStyles = { navController.navigate(Routes.CUSTOM_STYLES) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.ANALYSIS) {
            AnalysisScreen(
                uiState = uiState,
                onTargetMessageSelected = { id -> viewModel.setTargetMessage(id) },
                onMessageEdited = { id, text -> viewModel.updateMessageText(id, text) },
                onContinueToStyles = { navController.navigate(Routes.STYLES) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STYLES) {
            StyleSelectorScreen(
                uiState = uiState,
                onStyleCustomizationChanged = { cust -> viewModel.updateStyleCustomization(cust) },
                onGenerateClicked = {
                    viewModel.generateReplies()
                    navController.navigate(Routes.REPLIES)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REPLIES) {
            RepliesScreen(
                uiState = uiState,
                onCopyReply = { suggestion -> viewModel.onReplyCopied(suggestion) },
                onRefineReply = { original, type -> viewModel.refineReply(original, type) },
                onRegenerate = { viewModel.generateReplies() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROVIDERS) {
            ProviderConfigScreen(
                providers = providers,
                onSaveProvider = { config -> viewModel.saveProvider(config) },
                onSetDefaultProvider = { id -> viewModel.setDefaultProvider(id) },
                onDeleteProvider = { id -> viewModel.deleteProvider(id) },
                onTestConnection = { config -> viewModel.testProviderConnection(config) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CUSTOM_STYLES) {
            CustomStylesScreen(
                presets = customPresets,
                onSavePreset = { preset -> viewModel.saveCustomPreset(preset) },
                onDeletePreset = { id -> viewModel.deleteCustomPreset(id) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                uiState = uiState,
                onToggleOverlay = { viewModel.setOverlayEnabled(it) },
                onClearHistory = { viewModel.clearAllHistory() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                entries = historyEntries,
                onDeleteEntry = { entry -> viewModel.deleteHistoryEntry(entry) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

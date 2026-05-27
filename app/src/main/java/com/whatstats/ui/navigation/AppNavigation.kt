package com.whatstats.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.whatstats.R
import com.whatstats.data.repository.WhatsAppRepository
import com.whatstats.ui.MainViewModel
import com.whatstats.ui.screen.calls.CallsStatsScreen
import com.whatstats.ui.screen.chatdetail.ChatDetailScreen
import com.whatstats.ui.screen.chatlist.ChatListScreen
import com.whatstats.ui.screen.dashboard.DashboardScreen
import com.whatstats.ui.screen.history.GroupHistoryScreen
import com.whatstats.ui.screen.home.AppScaffold
import com.whatstats.ui.screen.media.mediaStatsScreen
import com.whatstats.ui.screen.setup.LiteSetupViewModel
import com.whatstats.ui.screen.setup.senderSelectionDialog
import com.whatstats.ui.screen.setup.setupScreen
import com.whatstats.ui.screen.tutorial.TutorialOverlay
import com.whatstats.ui.screen.wordsearch.WordSearchScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Setup : Screen("setup?autoNavigate={autoNavigate}") {
        fun createRoute(autoNavigate: Boolean = true) = "setup?autoNavigate=$autoNavigate"
    }

    data object LiteSetup : Screen("lite_setup") {
        fun createRoute(zipUri: String? = null): String {
            return if (zipUri != null) {
                "lite_setup?zipUri=${Uri.encode(zipUri)}"
            } else {
                route
            }
        }
    }

    data object Dashboard : Screen("dashboard")

    data object ChatList : Screen("chat_list")

    data object LiteSetupAdd : Screen("lite_setup_add") {
        fun createRoute(zipUri: String? = null): String {
            return if (zipUri != null) {
                "lite_setup_add?zipUri=${Uri.encode(zipUri)}"
            } else {
                route
            }
        }
    }

    data object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: Long) = "chat_detail/$chatId"
    }

    data object WordSearch : Screen("word_search") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "word_search/$chatId" else route
    }

    data object CallsStats : Screen("calls_stats") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "calls_stats/$chatId" else route
    }

    data object MediaStats : Screen("media_stats") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "media_stats/$chatId" else route
    }

    data object GroupHistory : Screen("group_history") {
        fun createRoute(chatId: Long) = "group_history/$chatId"
    }
}

object NavGraphRoute {
    const val SETUP = "setup_graph"
    const val ROOT = "root_mode_graph"
    const val LITE = "lite_mode_graph"

    // sub-graph shared keys
    const val OVERVIEW = "overview_graph"
    const val CHATS = "chats_graph"
    const val MEDIA = "media_graph"
    const val RADAR = "radar_graph"
    const val CALLS = "calls_graph"
}

@Composable
fun appNavigation(
    navController: NavHostController,
    repository: WhatsAppRepository,
    mainViewModel: MainViewModel,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isLiteMode by mainViewModel.isLiteMode.collectAsState()
    val adsUiState by mainViewModel.adsUiState.collectAsState()
    val hasSeenTutorial by mainViewModel.hasSeenTutorial.collectAsState()
    val showTutorial by mainViewModel.showTutorial.collectAsState()

    // Quando arriva un URI da share intent e NON siamo su Setup,
    // naviga a Setup per consumare l'URI.
    LaunchedEffect(Unit) {
        mainViewModel.incomingZipUri.collect { uri ->
            val currentRoute = navController.currentDestination?.route ?: ""
            if (uri != null && !currentRoute.startsWith("setup")) {
                navController.navigate(Screen.Setup.createRoute(autoNavigate = true)) {
                    popUpTo("app_graph") { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(hasSeenTutorial, showTutorial) {
        if (!hasSeenTutorial && !showTutorial) {
            mainViewModel.requestTutorial()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            navController = navController,
            currentDestination = currentDestination,
            isLiteMode = isLiteMode,
            showBannerAd = adsUiState.shouldShowBanner,
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = NavGraphRoute.SETUP,
                route = "app_graph",
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
                setupGraph(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    resolveTargetGraph = {
                        if (mainViewModel.isLiteMode.value) NavGraphRoute.LITE else NavGraphRoute.ROOT
                    },
                )
                rootModeGraph(
                    navController = navController,
                    repository = repository,
                    mainViewModel = mainViewModel,
                )
                liteModeGraph(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    onResetLite = { mainViewModel.resetLiteMode() },
                )
            }
        }

        if (showTutorial) {
            TutorialOverlay(
                onComplete = { mainViewModel.completeTutorial() },
            )
        }
    }
}

private fun NavGraphBuilder.setupGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    resolveTargetGraph: () -> String,
) {
    navigation(
        startDestination = Screen.Setup.route,
        route = NavGraphRoute.SETUP,
    ) {
        composable(
            route = Screen.Setup.route,
            arguments =
                listOf(
                    navArgument("autoNavigate") {
                        type = NavType.BoolType
                        defaultValue = true
                    },
                ),
        ) { backStackEntry ->
            val autoNavigate = backStackEntry.arguments?.getBoolean("autoNavigate", true) ?: true
            val incomingZipUri by mainViewModel.incomingZipUri.collectAsState()
            val context = LocalContext.current
            val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
            setupScreen(
                autoNavigate = autoNavigate,
                incomingZipUri = incomingZipUri,
                onUriConsumed = { mainViewModel.setIncomingZipUri(null) },
                showBannerAd = areAdsEnabled,
                onSetupComplete = {
                    val target = resolveTargetGraph()
                    if (target == NavGraphRoute.ROOT) {
                        context.findActivity()?.let { activity ->
                            mainViewModel.tryShowInterstitialAfterLongOperation(
                                activity = activity,
                                source = "root_analysis_completed",
                            )
                        }
                    }
                    navController.navigate(target) {
                        popUpTo(NavGraphRoute.SETUP) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToDashboard = {
                    val target = resolveTargetGraph()
                    navController.navigate(target) {
                        popUpTo(NavGraphRoute.SETUP) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

// ── Root Mode Graph ─────────────────────────────────────────────────────────────

private fun NavGraphBuilder.rootModeGraph(
    navController: NavHostController,
    repository: WhatsAppRepository,
    mainViewModel: MainViewModel,
) {
    navigation(
        startDestination = NavGraphRoute.OVERVIEW,
        route = NavGraphRoute.ROOT,
    ) {
        navigation(
            startDestination = Screen.Dashboard.route,
            route = NavGraphRoute.OVERVIEW,
        ) {
            composable(Screen.Dashboard.route) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                val context = LocalContext.current
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    context.findActivity()?.let { activity ->
                        mainViewModel.tryShowInterstitialAfterLongOperation(
                            activity = activity,
                            source = "dashboard_arrived",
                        )
                    }
                }
                DashboardScreen(
                    showNativeAds = areAdsEnabled,
                    onNavigateToMedia = {
                        navController.navigate(Screen.MediaStats.route) {
                            popUpTo(NavGraphRoute.ROOT) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToChatDetail = { chatId ->
                        navController.navigate(Screen.ChatDetail.createRoute(chatId))
                    },
                    onNavigateBack = {
                        scope.launch {
                            repository.resetState()
                            navController.navigate(Screen.Setup.createRoute(autoNavigate = false)) {
                                popUpTo("app_graph") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        }

        navigation(
            startDestination = Screen.ChatList.route,
            route = NavGraphRoute.CHATS,
        ) {
            composable(Screen.ChatList.route) {
                BackHandler {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(NavGraphRoute.ROOT) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                ChatListScreen(
                    onChatSelected = { chatId ->
                        navController.navigate(Screen.ChatDetail.createRoute(chatId))
                    },
                    onNavigateBack = { navController.popBackStack() },
                    // non disponibile in Root mode
                    onImportChat = {},
                    onAllChatsDeleted = {},
                    showNativeAds = areAdsEnabled,
                )
            }

            sharedChatDetailRoutes(navController, mainViewModel)
        }

        navigation(
            startDestination = Screen.MediaStats.route,
            route = NavGraphRoute.MEDIA,
        ) {
            composable(Screen.MediaStats.route) {
                BackHandler {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(NavGraphRoute.ROOT) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                mediaStatsScreen(chatId = null, onNavigateBack = {}, showNativeAds = areAdsEnabled)
            }
        }

        navigation(
            startDestination = Screen.WordSearch.route,
            route = NavGraphRoute.RADAR,
        ) {
            composable(Screen.WordSearch.route) {
                BackHandler {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(NavGraphRoute.ROOT) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                WordSearchScreen(chatId = null, onNavigateBack = {}, showNativeAds = areAdsEnabled)
            }
        }

        navigation(
            startDestination = Screen.CallsStats.route,
            route = NavGraphRoute.CALLS,
        ) {
            composable(Screen.CallsStats.route) {
                BackHandler {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(NavGraphRoute.ROOT) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                CallsStatsScreen(chatId = null, onNavigateBack = {}, showNativeAds = areAdsEnabled)
            }
        }
    }
}

// ── Lite Mode Graph ─────────────────────────────────────────────────────────────

private fun NavGraphBuilder.liteModeGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    onResetLite: () -> Unit,
) {
    navigation(
        startDestination = NavGraphRoute.OVERVIEW,
        route = NavGraphRoute.LITE,
    ) {
        navigation(
            startDestination = Screen.Dashboard.route,
            route = NavGraphRoute.OVERVIEW,
        ) {
            composable(Screen.Dashboard.route) {
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                val context = LocalContext.current
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    context.findActivity()?.let { activity ->
                        mainViewModel.tryShowInterstitialAfterLongOperation(
                            activity = activity,
                            source = "dashboard_arrived",
                        )
                    }
                }
                DashboardScreen(
                    showNativeAds = areAdsEnabled,
                    onNavigateToMedia = {
                        navController.navigate(Screen.MediaStats.route) {
                            popUpTo(NavGraphRoute.LITE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToChatDetail = { chatId ->
                        navController.navigate(Screen.ChatDetail.createRoute(chatId))
                    },
                    onNavigateBack = {
                        mainViewModel.setIncomingZipUri(null)
                        navController.navigate(Screen.Setup.createRoute(autoNavigate = false)) {
                            popUpTo("app_graph") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        navigation(
            startDestination = Screen.ChatList.route,
            route = NavGraphRoute.CHATS,
        ) {
            composable(Screen.ChatList.route) {
                BackHandler {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(NavGraphRoute.LITE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                ChatListScreen(
                    onChatSelected = { chatId ->
                        navController.navigate(Screen.ChatDetail.createRoute(chatId))
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onImportChat = {
                        navController.navigate(Screen.LiteSetupAdd.route)
                    },
                    onAllChatsDeleted = {
                        navController.navigate(Screen.Setup.route) {
                            popUpTo("app_graph") { inclusive = false }
                        }
                    },
                    showNativeAds = areAdsEnabled,
                )
            }

            // Route esclusiva Lite per aggiungere una chat ZIP
            composable(
                route = "lite_setup_add?zipUri={zipUri}",
                arguments =
                    listOf(
                        navArgument("zipUri") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
            ) { backStackEntry ->
                val zipUri = backStackEntry.arguments?.getString("zipUri")?.let { Uri.decode(it) }
                AddChatZipScreen(
                    incomingZipUri = zipUri,
                    mainViewModel = mainViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onImportComplete = { navController.popBackStack() },
                )
            }

            sharedChatDetailRoutes(navController, mainViewModel)
        }

        navigation(
            startDestination = Screen.MediaStats.route,
            route = NavGraphRoute.MEDIA,
        ) {
            composable(Screen.MediaStats.route) {
                BackHandler {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(NavGraphRoute.LITE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                mediaStatsScreen(chatId = null, onNavigateBack = {}, showNativeAds = areAdsEnabled)
            }
        }

        navigation(
            startDestination = Screen.WordSearch.route,
            route = NavGraphRoute.RADAR,
        ) {
            composable(Screen.WordSearch.route) {
                BackHandler {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(NavGraphRoute.LITE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
                WordSearchScreen(chatId = null, onNavigateBack = {}, showNativeAds = areAdsEnabled)
            }
        }
    }
}

// ── Route condivise ChatDetail (usate da entrambi i graph) ─────────────────────

private fun NavGraphBuilder.sharedChatDetailRoutes(
    navController: NavHostController,
    mainViewModel: MainViewModel,
) {
    composable(
        route = Screen.ChatDetail.route,
        arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
        val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
        val context = LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            context.findActivity()?.let { activity ->
                mainViewModel.tryShowInterstitialAfterLongOperation(
                    activity = activity,
                    source = "chat_detail_enter",
                )
            }
        }
        ChatDetailScreen(
            chatId = chatId,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToWordSearch = { targetChatId ->
                navController.navigate(Screen.WordSearch.createRoute(targetChatId))
            },
            onNavigateToCalls = { targetChatId ->
                navController.navigate(Screen.CallsStats.createRoute(targetChatId))
            },
            onNavigateToMedia = { targetChatId ->
                navController.navigate(Screen.MediaStats.createRoute(targetChatId))
            },
            onNavigateToHistory = { targetChatId ->
                navController.navigate(Screen.GroupHistory.createRoute(targetChatId))
            },
            showNativeAds = areAdsEnabled,
        )
    }

    composable(
        route = "${Screen.WordSearch.route}/{chatId}",
        arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getLong("chatId")
        val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
        WordSearchScreen(chatId = chatId, onNavigateBack = { navController.popBackStack() }, showNativeAds = areAdsEnabled)
    }

    composable(
        route = "${Screen.CallsStats.route}/{chatId}",
        arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getLong("chatId")
        val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
        CallsStatsScreen(chatId = chatId, onNavigateBack = { navController.popBackStack() }, showNativeAds = areAdsEnabled)
    }

    composable(
        route = "${Screen.MediaStats.route}/{chatId}",
        arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getLong("chatId")
        val areAdsEnabled = mainViewModel.adsUiState.collectAsState().value.areAdsEnabled
        mediaStatsScreen(chatId = chatId, onNavigateBack = { navController.popBackStack() }, showNativeAds = areAdsEnabled)
    }

    composable(
        route = "${Screen.GroupHistory.route}/{chatId}",
        arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getLong("chatId")
        GroupHistoryScreen(chatId = chatId, onNavigateBack = { navController.popBackStack() })
    }
}

// ── Schermata di aggiunta ZIP dalla ChatList ────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
private fun AddChatZipScreen(
    incomingZipUri: String? = null,
    mainViewModel: MainViewModel,
    viewModel: LiteSetupViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    androidx.activity.compose.BackHandler(enabled = state.isLoading || state.showSenderSelection) {}

    androidx.compose.runtime.LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            context.findActivity()?.let { activity ->
                mainViewModel.tryShowInterstitialAfterLongOperation(
                    activity = activity,
                    source = "lite_import_completed",
                )
            }
            onImportComplete()
            viewModel.consumeImportComplete()
        }
    }

    // Lancia il picker automaticamente all'apertura della schermata.
    // L'URI rimane nell'Activity context — nessuna serializzazione nel route.
    val zipLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { viewModel.onZipSelected(it) } }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (incomingZipUri == null && !viewModel.state.value.isLoading) {
            zipLauncher.launch(arrayOf("application/zip"))
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text(stringResource(R.string.add_zip_title)) },
                navigationIcon = {
                    if (!state.isLoading && !state.showSenderSelection) {
                        androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                            androidx.compose.material3.Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_button_desc),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier =
                androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier =
                    androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                if (state.isLoading) {
                    androidx.compose.material3.Text(
                        text = state.progressLabel.ifEmpty { stringResource(R.string.processing_label) },
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progressPercent },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.Text(
                        text = "${(state.progressPercent * 100).toInt()}%",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                } else {
                    androidx.compose.material3.Button(
                        onClick = { zipLauncher.launch(arrayOf("application/zip")) },
                        modifier =
                            androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ) {
                        androidx.compose.material3.Text(stringResource(R.string.select_zip_file))
                    }
                }
                state.error?.let {
                    androidx.compose.material3.Text(
                        it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (state.showSenderSelection) {
        senderSelectionDialog(
            senders = state.identifiedSenders,
            onSenderSelected = { viewModel.onSenderSelected(it) },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

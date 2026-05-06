package com.whatstat.reborn.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navigation
import android.net.Uri
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.ui.screen.calls.CallsStatsScreen
import com.whatstat.reborn.ui.screen.chatdetail.ChatDetailScreen
import com.whatstat.reborn.ui.screen.chatlist.ChatListScreen
import com.whatstat.reborn.ui.screen.dashboard.DashboardScreen
import com.whatstat.reborn.ui.screen.home.AppScaffold
import com.whatstat.reborn.ui.screen.media.MediaStatsScreen
import com.whatstat.reborn.ui.screen.setup.SetupScreen
import com.whatstat.reborn.ui.screen.setup.LiteSetupViewModel
import com.whatstat.reborn.ui.screen.setup.SenderSelectionDialog
import com.whatstat.reborn.ui.screen.wordsearch.WordSearchScreen
import com.whatstat.reborn.ui.screen.history.GroupHistoryScreen
import com.whatstat.reborn.ui.MainViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
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
    const val Setup = "setup_graph"
    const val Main = "main_graph"
    const val Overview = "overview_graph"
    const val Chats = "chats_graph"
    const val Media = "media_graph"
    const val Radar = "radar_graph"
    const val Calls = "calls_graph"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    repository: WhatsAppRepository,
    mainViewModel: MainViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val isLiteMode by mainViewModel.isLiteMode.collectAsState()
    val incomingZipUri by mainViewModel.incomingZipUri.collectAsState()

    LaunchedEffect(incomingZipUri, navController) {
        incomingZipUri?.let { uri ->
            if (isLiteMode) {
                // App già in Lite Mode: aggiunge un'altra chat dalla ChatList
                snapshotFlow { navController.currentBackStackEntry }
                    .filterNotNull()
                    .first()
                navController.navigate(Screen.LiteSetupAdd.createRoute(uri)) {
                    launchSingleTop = true
                }
                mainViewModel.setIncomingZipUri(null)
            }
            // isLiteMode=false: la SetupScreen consumerà l'URI direttamente
            // tramite il suo LiteSetupViewModel senza navigare altrove.
        }
    }

    AppScaffold(
        navController = navController,
        currentDestination = currentDestination,
        isLiteMode = isLiteMode
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavGraphRoute.Setup,
            route = "root_graph",
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            setupGraph(navController, incomingZipUri, onUriConsumed = { mainViewModel.setIncomingZipUri(null) })
            mainGraph(navController, repository, onResetLite = { mainViewModel.resetLiteMode() })
        }
    }
}

private fun NavGraphBuilder.setupGraph(
    navController: NavHostController,
    incomingZipUri: String?,
    onUriConsumed: () -> Unit
) {
    navigation(
        startDestination = Screen.Setup.route,
        route = NavGraphRoute.Setup
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                incomingZipUri = incomingZipUri,
                onUriConsumed = onUriConsumed,
                onSetupComplete = {
                    navController.navigate(NavGraphRoute.Main) {
                        popUpTo(NavGraphRoute.Setup) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(NavGraphRoute.Main) {
                        popUpTo(NavGraphRoute.Setup) { inclusive = true }
                    }
                }
            )
        }
    }
}

private fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    repository: WhatsAppRepository,
    onResetLite: () -> Unit = {}
) {
    navigation(
        startDestination = NavGraphRoute.Overview,
        route = NavGraphRoute.Main
    ) {
        navigation(
            startDestination = Screen.Dashboard.route,
            route = NavGraphRoute.Overview
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateBack = {
                        onResetLite()
                        repository.resetState()
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        navigation(
            startDestination = Screen.ChatList.route,
            route = NavGraphRoute.Chats
        ) {
            composable(Screen.ChatList.route) {
                ChatListScreen(
                    onChatSelected = { chatId ->
                        navController.navigate(Screen.ChatDetail.createRoute(chatId))
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onImportChat = { uriString ->
                        navController.navigate(Screen.LiteSetupAdd.createRoute(uriString))
                    }
                )
            }

            composable(
                route = "lite_setup_add?zipUri={zipUri}",
                arguments = listOf(
                    navArgument("zipUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val zipUri = backStackEntry.arguments?.getString("zipUri")?.let {
                    Uri.decode(it)
                }
                AddChatZipScreen(
                    incomingZipUri = zipUri,
                    onNavigateBack = { navController.popBackStack() },
                    onImportComplete = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ChatDetail.route,
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                ChatDetailScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWordSearch = {
                        navController.navigate(Screen.WordSearch.createRoute(chatId))
                    },
                    onNavigateToCalls = {
                        navController.navigate(Screen.CallsStats.createRoute(chatId))
                    },
                    onNavigateToMedia = {
                        navController.navigate(Screen.MediaStats.createRoute(chatId))
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.GroupHistory.createRoute(chatId))
                    }
                )
            }

            composable(
                route = "${Screen.WordSearch.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                WordSearchScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Screen.CallsStats.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                CallsStatsScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Screen.MediaStats.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                MediaStatsScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Screen.GroupHistory.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                GroupHistoryScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        navigation(
            startDestination = Screen.MediaStats.route,
            route = NavGraphRoute.Media
        ) {
            composable(Screen.MediaStats.route) {
                MediaStatsScreen(
                    chatId = null,
                    onNavigateBack = {}
                )
            }
        }

        navigation(
            startDestination = Screen.WordSearch.route,
            route = NavGraphRoute.Radar
        ) {
            composable(Screen.WordSearch.route) {
                WordSearchScreen(
                    chatId = null,
                    onNavigateBack = {}
                )
            }
        }

        navigation(
            startDestination = Screen.CallsStats.route,
            route = NavGraphRoute.Calls
        ) {
            composable(Screen.CallsStats.route) {
                CallsStatsScreen(
                    chatId = null,
                    onNavigateBack = {}
                )
            }
        }
    }
}

// ── Schermata di aggiunta ZIP dalla ChatList ────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddChatZipScreen(
    incomingZipUri: String? = null,
    viewModel: LiteSetupViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    androidx.activity.compose.BackHandler(enabled = state.isLoading || state.showSenderSelection) {}

    androidx.compose.runtime.LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onImportComplete()
            viewModel.consumeImportComplete()
        }
    }

    androidx.compose.runtime.LaunchedEffect(incomingZipUri) {
        incomingZipUri?.let { viewModel.onZipSelected(android.net.Uri.parse(it)) }
    }

    val zipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onZipSelected(it) } }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Aggiungi ZIP") },
                navigationIcon = {
                    if (!state.isLoading && !state.showSenderSelection) {
                        androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Default.ArrowBack,
                                contentDescription = "Indietro"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                if (state.isLoading) {
                    androidx.compose.material3.Text(
                        text = state.progressLabel.ifEmpty { "Elaborazione in corso..." },
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progressPercent },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    )
                    androidx.compose.material3.Text(
                        text = "${(state.progressPercent * 100).toInt()}%",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                } else {
                    androidx.compose.material3.Button(
                        onClick = { zipLauncher.launch("application/zip") },
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        androidx.compose.material3.Text("SELEZIONA FILE ZIP")
                    }
                }
                state.error?.let {
                    androidx.compose.material3.Text(
                        it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (state.showSenderSelection) {
        SenderSelectionDialog(
            senders = state.identifiedSenders,
            onSenderSelected = { viewModel.onSenderSelected(it) }
        )
    }
}

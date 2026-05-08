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
import androidx.navigation.NavDestination.Companion.hierarchy
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull

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
            val currentHierarchy = navController.currentBackStackEntry
                ?.destination?.hierarchy?.map { it.route }?.toList()
            
            val isInMainGraph = currentHierarchy?.contains(NavGraphRoute.Main) == true
            
            if (isInMainGraph) {
                // Aspettiamo che il backstack sia pronto
                snapshotFlow { navController.currentBackStackEntry }.filterNotNull().first()
                navController.navigate(Screen.LiteSetupAdd.createRoute(uri)) {
                    launchSingleTop = true
                }
                mainViewModel.setIncomingZipUri(null)
            }
            // Se siamo nel SetupGraph, SetupScreen consumerà l'URI via LaunchedEffect
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
            mainGraph(navController, repository, mainViewModel, onResetLite = { mainViewModel.resetLiteMode() })
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
        composable(
            route = Screen.Setup.route,
            arguments = listOf(
                navArgument("autoNavigate") {
                    type = NavType.BoolType
                    defaultValue = true
                }
            )
        ) {
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
    mainViewModel: MainViewModel,
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
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                DashboardScreen(
                    onNavigateBack = {
                        scope.launch {
                            // Non resettiamo più Lite mode qui, ci pensa autoNavigate=false a fermare il loop
                            // mantenendo l'utente sulla tab corretta.
                            repository.resetState() // Reset dello stato UI per tornare al setup
                            navController.navigate(Screen.Setup.createRoute(autoNavigate = false)) {
                                popUpTo(0) { inclusive = true }
                            }
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
                    onImportChat = {
                        navController.navigate(Screen.LiteSetupAdd.route)
                    },
                    onAllChatsDeleted = {
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(0) { inclusive = true }
                        }
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
                    android.net.Uri.decode(it)
                }
                AddChatZipScreen(
                    incomingZipUri = zipUri,
                    onUriConsumed = { mainViewModel.setIncomingZipUri(null) },
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
    onUriConsumed: () -> Unit = {},
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
    // Lancia il picker automaticamente all'apertura della schermata.
    // L'URI rimane nell'Activity context — nessuna serializzazione nel route.
    val zipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onZipSelected(it) } }

    androidx.compose.runtime.LaunchedEffect(incomingZipUri) {
        if (incomingZipUri != null) {
            // Se riceviamo un URI, lo processiamo immediatamente!
            viewModel.onZipSelected(android.net.Uri.parse(incomingZipUri))
            onUriConsumed() // Consumiamo l'URI per evitare ri-trigger
        } else if (!viewModel.state.value.isLoading) {
            // Altrimenti lanciamo il picker manuale
            zipLauncher.launch(arrayOf("application/zip"))
        }
    }

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
                        onClick = { zipLauncher.launch(arrayOf("application/zip")) },
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

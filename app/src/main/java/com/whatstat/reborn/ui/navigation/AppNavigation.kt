package com.whatstat.reborn.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import com.whatstat.reborn.ui.screen.setup.LiteSetupScreen
import com.whatstat.reborn.ui.screen.wordsearch.WordSearchScreen
import com.whatstat.reborn.ui.screen.history.GroupHistoryScreen
import com.whatstat.reborn.ui.MainViewModel
import androidx.compose.runtime.collectAsState
import com.whatstat.reborn.util.DebugLogManager
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
            // Attendi che il NavHost sia completamente inizializzato
            snapshotFlow { navController.currentBackStackEntry }
                .filterNotNull()
                .first()

            val targetRoute = if (isLiteMode) {
                Screen.LiteSetupAdd.createRoute(uri)
            } else {
                Screen.LiteSetup.createRoute(uri)
            }
            DebugLogManager.log("AppNavigation incoming zip route | isLite=$isLiteMode target=$targetRoute")
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
            mainViewModel.setIncomingZipUri(null)
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
            setupGraph(navController)
            mainGraph(navController, repository, onResetLite = { mainViewModel.resetLiteMode() })
        }
    }
}

private fun NavGraphBuilder.setupGraph(navController: NavHostController) {
    navigation(
        startDestination = Screen.Setup.route,
        route = NavGraphRoute.Setup
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(NavGraphRoute.Main) {
                        popUpTo(NavGraphRoute.Setup) { inclusive = true }
                    }
                },
                onNavigateToLite = {
                    navController.navigate(Screen.LiteSetup.route)
                },
                onNavigateToDashboard = {
                    navController.navigate(NavGraphRoute.Main) {
                        popUpTo(NavGraphRoute.Setup) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "lite_setup?zipUri={zipUri}",
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
            LiteSetupScreen(
                incomingZipUri = zipUri,
                onNavigateBack = { navController.popBackStack() },
                onImportComplete = {
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
                        DebugLogManager.log("Dashboard onNavigateBack invoked")
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
                    onImportChat = {
                        navController.navigate(Screen.LiteSetupAdd.createRoute())
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
                LiteSetupScreen(
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

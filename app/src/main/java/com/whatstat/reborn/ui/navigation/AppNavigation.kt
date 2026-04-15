package com.whatstat.reborn.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.ui.screen.calls.CallsStatsScreen
import com.whatstat.reborn.ui.screen.chatdetail.ChatDetailScreen
import com.whatstat.reborn.ui.screen.chatlist.ChatListScreen
import com.whatstat.reborn.ui.screen.dashboard.DashboardScreen
import com.whatstat.reborn.ui.screen.media.MediaStatsScreen
import com.whatstat.reborn.ui.screen.setup.SetupScreen
import com.whatstat.reborn.ui.screen.wordsearch.WordSearchScreen

/**
 * Routes dell'app.
 */
sealed class Screen(val route: String) {
    data object Setup        : Screen("setup")
    data object Dashboard   : Screen("dashboard")
    data object ChatList   : Screen("chat_list")
    data object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: Long) = "chat_detail/$chatId"
    }
    data object WordSearch  : Screen("word_search") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "word_search/$chatId" else "word_search"
    }
    data object CallsStats : Screen("calls_stats") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "calls_stats/$chatId" else "calls_stats"
    }
    data object MediaStats : Screen("media_stats") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "media_stats/$chatId" else "media_stats"
    }
}

/**
 * Navigazione principale dell'app.
 * Setup → Dashboard → ChatList → ChatDetail
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    repository: WhatsAppRepository
) {
    val animDuration = 350

    NavHost(
        navController = navController,
        startDestination = Screen.Setup.route,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(animDuration)) +
                fadeIn(tween(animDuration))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(animDuration)) +
                fadeOut(tween(animDuration))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(animDuration)) +
                fadeIn(tween(animDuration))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(animDuration)) +
                fadeOut(tween(animDuration))
        }
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToChatList = {
                    navController.navigate(Screen.ChatList.route)
                },
                onNavigateToWordSearch = {
                    navController.navigate(Screen.WordSearch.route)
                },
                onNavigateToCalls = {
                    navController.navigate(Screen.CallsStats.route)
                },
                onNavigateToMedia = {
                    navController.navigate(Screen.MediaStats.route)
                },
                onNavigateBack = {
                    repository.resetState()
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.WordSearch.route + "/{chatId}",
            arguments = listOf(navArgument("chatId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId")?.takeIf { it != -1L }
            WordSearchScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.CallsStats.route + "/{chatId}",
            arguments = listOf(navArgument("chatId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId")?.takeIf { it != -1L }
            CallsStatsScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.MediaStats.route + "/{chatId}",
            arguments = listOf(navArgument("chatId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId")?.takeIf { it != -1L }
            MediaStatsScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Also support routes without chatId
        composable(Screen.WordSearch.route) {
            WordSearchScreen(
                chatId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CallsStats.route) {
            CallsStatsScreen(
                chatId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MediaStats.route) {
            MediaStatsScreen(
                chatId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                onChatSelected = { chatId ->
                    navController.navigate(Screen.ChatDetail.createRoute(chatId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
            ChatDetailScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToWordSearch = {
                    navController.navigate(Screen.WordSearch.createRoute(chatId))
                },
                onNavigateToCalls = {
                    navController.navigate(Screen.CallsStats.createRoute(chatId))
                },
                onNavigateToMedia = {
                    navController.navigate(Screen.MediaStats.createRoute(chatId))
                }
            )
        }
    }
}

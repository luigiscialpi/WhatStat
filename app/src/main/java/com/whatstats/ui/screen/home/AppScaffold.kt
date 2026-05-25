package com.whatstats.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.whatstats.ui.component.AppDrawerContent
import com.whatstats.ui.component.LocalDrawerState
import com.whatstats.ui.component.ads.BottomBannerAd
import com.whatstats.ui.navigation.BottomNavigationBar
import com.whatstats.ui.navigation.NavGraphRoute
import com.whatstats.ui.navigation.Screen

@Composable
@Suppress("FunctionName")
fun AppScaffold(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isLiteMode: Boolean = false,
    showBannerAd: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val route = currentDestination?.route.orEmpty()
    val isLiteImportRoute = route.contains("lite_setup")
    val isOnSetupScreen =
        currentDestination
            ?.hierarchy
            ?.any { it.route == NavGraphRoute.SETUP } == true
    val showBottomBar =
        currentDestination
            ?.hierarchy
            ?.any { destination ->
                destination.route == NavGraphRoute.ROOT || destination.route == NavGraphRoute.LITE
            } == true && !isLiteImportRoute

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    CompositionLocalProvider(LocalDrawerState provides drawerState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    isOnSetupScreen = isOnSetupScreen,
                    onNavigateToSetup = {
                        navController.navigate(Screen.Setup.createRoute(autoNavigate = false)) {
                            popUpTo("app_graph") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            },
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (showBottomBar) {
                        Column {
                            if (showBannerAd) {
                                BottomBannerAd()
                                HorizontalDivider()
                            }
                            BottomNavigationBar(
                                navController = navController,
                                currentDestination = currentDestination,
                                isLiteMode = isLiteMode,
                            )
                        }
                    }
                },
            ) { innerPadding ->
                content(innerPadding)
            }
        }
    }
}

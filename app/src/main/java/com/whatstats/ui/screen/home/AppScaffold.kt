package com.whatstats.ui.screen.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
                    if (showBottomBar && (!isLandscape || showBannerAd)) {
                        Column {
                            if (showBannerAd) {
                                BottomBannerAd()
                                HorizontalDivider()
                            }
                            if (!isLandscape) {
                                BottomNavigationBar(
                                    navController = navController,
                                    currentDestination = currentDestination,
                                    isLiteMode = isLiteMode,
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
                        ),
                ) {
                    if (showBottomBar && isLandscape) {
                        BottomNavigationBar(
                            navController = navController,
                            currentDestination = currentDestination,
                            isLiteMode = isLiteMode,
                            isLandscape = true,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        content(innerPadding)
                    }
                }
            }
        }
    }
}

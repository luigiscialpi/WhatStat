package com.whatstat.ui.screen.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.whatstat.ui.component.AppDrawerContent
import com.whatstat.ui.component.LocalDrawerState
import com.whatstat.ui.navigation.BottomNavigationBar
import com.whatstat.ui.navigation.NavGraphRoute

@Composable
@Suppress("FunctionName")
fun AppScaffold(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isLiteMode: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val route = currentDestination?.route.orEmpty()
    val isLiteImportRoute = route.contains("lite_setup")
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
            drawerContent = { AppDrawerContent() },
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (showBottomBar) {
                        BottomNavigationBar(
                            navController = navController,
                            currentDestination = currentDestination,
                            isLiteMode = isLiteMode,
                        )
                    }
                },
            ) { innerPadding ->
                content(innerPadding)
            }
        }
    }
}

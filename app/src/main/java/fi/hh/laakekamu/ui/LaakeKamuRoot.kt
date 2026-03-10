package fi.hh.laakekamu.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fi.hh.laakekamu.ui.etusivu.EtusivuScreen
import fi.hh.laakekamu.ui.laakelista.LaakelistaScreen
import fi.hh.laakekamu.ui.navigation.BottomNavBar
import fi.hh.laakekamu.ui.navigation.MainDestination
import fi.hh.laakekamu.ui.oirepaivakirja.OirepaivakirjaScreen

/**
 * App shell: the navigation graph plus the bottom bar, which is shown only on the main screens
 * (forms are full screen). Etusivu is the start destination.
 */
@Composable
fun LaakeKamuRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Null only before the first destination is set; the start screen is a main one.
            if (currentRoute == null || MainDestination.isMain(currentRoute)) {
                BottomNavBar(
                    currentRoute = currentRoute ?: MainDestination.ETUSIVU.route,
                    onSelect = { destination -> navController.navigateToTab(destination) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.ETUSIVU.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainDestination.LAAKELISTA.route) { LaakelistaScreen() }
            composable(MainDestination.ETUSIVU.route) { EtusivuScreen() }
            composable(MainDestination.OIREPAIVAKIRJA.route) { OirepaivakirjaScreen() }
        }
    }
}

/**
 * Standard bottom-bar navigation: one copy of each tab on the back stack, back from a tab
 * returns to Etusivu, and a tab keeps its state when revisited.
 */
private fun NavHostController.navigateToTab(destination: MainDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

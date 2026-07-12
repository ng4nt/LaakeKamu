package fi.hh.laakekamu.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.etusivu.EtusivuScreen
import fi.hh.laakekamu.ui.laakelista.LaakelistaScreen
import fi.hh.laakekamu.ui.lisaalaake.LisaaLaakeScreen
import fi.hh.laakekamu.ui.lisaaoire.LisaaOireScreen
import fi.hh.laakekamu.ui.lisaaoire.LisaaOireViewModel
import fi.hh.laakekamu.ui.navigation.BottomNavBar
import fi.hh.laakekamu.ui.navigation.FormRoute
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
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            composable(MainDestination.LAAKELISTA.route) { entry ->
                // "Lääke lisätty" comes through the entry's saved state, so it survives
                // rotation. It's cleared once shown or when the user leaves the
                // list, but not when the activity is only being recreated.
                val handle = entry.savedStateHandle
                val lisatty by handle.getStateFlow(KEY_LAAKE_LISATTY, false)
                    .collectAsStateWithLifecycle()
                val viesti = stringResource(R.string.laake_lisatty)
                val activity = LocalActivity.current
                LaunchedEffect(lisatty) {
                    if (lisatty) {
                        try {
                            snackbarHostState.showSnackbar(viesti)
                        } finally {
                            if (activity?.isChangingConfigurations != true) {
                                handle[KEY_LAAKE_LISATTY] = false
                            }
                        }
                    }
                }
                LaakelistaScreen(
                    onAdd = {
                        // A double tap mustn't push two wizards.
                        navController.navigate(FormRoute.LISAA_LAAKE) { launchSingleTop = true }
                    },
                    // The edit selector is not built yet.
                    onEdit = {}
                )
            }
            composable(MainDestination.ETUSIVU.route) {
                EtusivuScreen(
                    onAdd = {
                        navController.navigate(FormRoute.LISAA_LAAKE) { launchSingleTop = true }
                    },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(MainDestination.OIREPAIVAKIRJA.route) { entry ->
                SavedFlagSnackbar(
                    entry.savedStateHandle,
                    KEY_OIRE_TALLENNETTU,
                    stringResource(R.string.oire_tallennettu),
                    snackbarHostState
                )
                OirepaivakirjaScreen(
                    onAdd = { day ->
                        navController.navigate(FormRoute.lisaaOire(day)) { launchSingleTop = true }
                    },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(
                FormRoute.LISAA_OIRE,
                arguments = listOf(
                    navArgument(LisaaOireViewModel.ARG_ALKAA) { type = NavType.LongType }
                )
            ) { entry ->
                LisaaOireScreen(
                    onClose = dropUnlessResumed { navController.popBackStack() },
                    onSaved = {
                        // Takaisin during the save has already left the form; popping again
                        // would leave the diary too.
                        if (navController.currentBackStackEntry?.id == entry.id) {
                            navController.popBackStack()
                            navController.getBackStackEntry(MainDestination.OIREPAIVAKIRJA.route)
                                .savedStateHandle[KEY_OIRE_TALLENNETTU] = true
                        }
                    }
                )
            }
            composable(FormRoute.LISAA_LAAKE) { entry ->
                LisaaLaakeScreen(
                    // A double tap mustn't pop the list too.
                    onClose = dropUnlessResumed { navController.popBackStack() },
                    onSaved = {
                        // Only while the wizard is on top, as for the symptom form above.
                        if (navController.currentBackStackEntry?.id == entry.id) {
                            // Pop first, so the wizard isn't saved into the list tab's back stack.
                            navController.popBackStack()
                            navController.navigateToTab(MainDestination.LAAKELISTA)
                            navController.getBackStackEntry(MainDestination.LAAKELISTA.route)
                                .savedStateHandle[KEY_LAAKE_LISATTY] = true
                        }
                    }
                )
            }
        }
    }
}

/** Lääkelista's saved-state flag: show "Lääke lisätty". */
private const val KEY_LAAKE_LISATTY = "laakeLisatty"

/** Oirepäiväkirja's saved-state flag: show "Oire tallennettu". */
private const val KEY_OIRE_TALLENNETTU = "oireTallennettu"

/**
 * Shows [message] once when the [key] flag in [handle] is set, as Lääkelista does for "Lääke
 * lisätty": the flag survives rotation and is cleared once shown.
 */
@Composable
private fun SavedFlagSnackbar(
    handle: SavedStateHandle,
    key: String,
    message: String,
    snackbarHostState: SnackbarHostState
) {
    val flag by handle.getStateFlow(key, false).collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(flag) {
        if (flag) {
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                if (activity?.isChangingConfigurations != true) handle[key] = false
            }
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

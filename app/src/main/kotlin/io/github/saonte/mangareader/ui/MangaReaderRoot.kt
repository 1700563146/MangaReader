package io.github.saonte.mangareader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.saonte.mangareader.MainViewModel
import io.github.saonte.mangareader.R
import io.github.saonte.mangareader.designsystem.theme.MangaReaderTheme
import io.github.saonte.mangareader.feature.browse.BrowseScreen
import io.github.saonte.mangareader.feature.history.HistoryScreen
import io.github.saonte.mangareader.feature.settings.SettingsScreen
import io.github.saonte.mangareader.feature.shelf.ShelfScreen
import io.github.saonte.mangareader.model.ThemeMode
import io.github.saonte.mangareader.navigation.BrowseRoute
import io.github.saonte.mangareader.navigation.HistoryRoute
import io.github.saonte.mangareader.navigation.SettingsRoute
import io.github.saonte.mangareader.navigation.ShelfRoute
import kotlin.reflect.KClass

@Composable
fun MangaReaderRoot(mainViewModel: MainViewModel = hiltViewModel()) {
    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MangaReaderTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        Scaffold(
            bottomBar = { MangaBottomBar(navController) },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = ShelfRoute,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<ShelfRoute> { ShelfScreen() }
                composable<HistoryRoute> { HistoryScreen() }
                composable<BrowseRoute> { BrowseScreen() }
                composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun MangaBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    NavigationBar {
        BottomItem(
            selected = destination.isOn(ShelfRoute::class),
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = stringResource(R.string.nav_shelf),
            onClick = { navController.navigateTopLevel(ShelfRoute) },
        )
        BottomItem(
            selected = destination.isOn(HistoryRoute::class),
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = stringResource(R.string.nav_history),
            onClick = { navController.navigateTopLevel(HistoryRoute) },
        )
        BottomItem(
            selected = destination.isOn(BrowseRoute::class),
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = stringResource(R.string.nav_browse),
            onClick = { navController.navigateTopLevel(BrowseRoute) },
        )
        BottomItem(
            selected = destination.isOn(SettingsRoute::class),
            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
            label = stringResource(R.string.nav_me),
            onClick = { navController.navigateTopLevel(SettingsRoute) },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomItem(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label) },
    )
}

private fun NavDestination?.isOn(route: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

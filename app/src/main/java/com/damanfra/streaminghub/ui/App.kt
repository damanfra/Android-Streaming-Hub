package com.damanfra.streaminghub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.damanfra.streaminghub.AppViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Filled.Home),
    Tab("search", "Buscar", Icons.Filled.Search),
    Tab("list", "Minha lista", Icons.Filled.Bookmark),
    Tab("apps", "Apps", Icons.Filled.Apps),
)

@Composable
fun StreamingHubApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    if (LocalIsTv.current) TvApp(vm, nav) else PhoneApp(vm, nav)
}

/** Troca de aba sem empilhar telas: volta ao Início e reaproveita o estado de cada aba. */
private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PhoneApp(vm: AppViewModel, nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.goToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavHost(vm, nav, Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

/**
 * TV: painel fixo à esquerda (seções, Filmes/Séries, dial de serviços, filtros) e o conteúdo à direita.
 * Voltar fora do painel leva o foco até ele; só sai do app quando o foco já está no painel.
 */
@Composable
private fun TvApp(vm: AppViewModel, nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val withPanel = route in tvPanelRoutes
    val focus = remember { TvPanelFocus() }
    var panelHasFocus by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalTvPanelFocus provides focus) {
            Row {
                if (withPanel) {
                    TvSidePanel(
                        vm = vm,
                        route = route,
                        focus = focus,
                        onNavigate = { if (it == "settings") nav.navigate("settings") else nav.goToTab(it) },
                        onFilters = { showFilters = true },
                        modifier = Modifier.onFocusChanged { panelHasFocus = it.hasFocus },
                    )
                }
                AppNavHost(vm, nav, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }

    // Ao abrir o app, o foco começa no dial do Início.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.request("home")
    }
    BackHandler(enabled = withPanel && !panelHasFocus) { focus.request(route) }

    if (showFilters) TvFilterDialog(vm) { showFilters = false }
}

@Composable
private fun AppNavHost(vm: AppViewModel, nav: NavHostController, modifier: Modifier) {
    val openDetail: (String, Int) -> Unit = { type, id -> nav.navigate("detail/$type/$id") }
    val openJellyfin: (String) -> Unit = { id -> nav.navigate("jf/$id") }
    val openSettings: () -> Unit = { nav.navigate("settings") }
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(navController = nav, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen(vm, openDetail, openJellyfin, openSettings) }
        composable("search") { SearchScreen(vm, openDetail, openJellyfin) }
        composable("list") { ListScreen(vm, openDetail) }
        composable("apps") { AppsScreen(openSettings) }
        composable("settings") { SettingsScreen(vm, back) }
        composable(
            "detail/{type}/{id}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
            ),
        ) { entry ->
            val type = entry.arguments?.getString("type") ?: "movie"
            val id = entry.arguments?.getInt("id") ?: 0
            DetailScreen(vm, type, id, back, openJellyfin)
        }
        composable("jf/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            JellyfinDetailScreen(vm, entry.arguments?.getString("id").orEmpty(), back, openDetail)
        }
    }
}

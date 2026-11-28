package de.mediclever.schichtclever

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.ui.screens.EinsaetzeScreen
import de.mediclever.schichtclever.ui.screens.EinsatzEditorScreen
import de.mediclever.schichtclever.ui.screens.FilterScreen
import de.mediclever.schichtclever.ui.screens.FocusScreen
import de.mediclever.schichtclever.ui.screens.MoreScreen
import de.mediclever.schichtclever.ui.screens.OnboardingScreen
import de.mediclever.schichtclever.ui.screens.ShiftScreen
import de.mediclever.schichtclever.ui.screens.SkillDetailScreen
import de.mediclever.schichtclever.ui.screens.SkillsScreen
import de.mediclever.schichtclever.ui.screens.StatsScreen
import de.mediclever.schichtclever.ui.screens.TemplatesScreen
import de.mediclever.schichtclever.ui.screens.einsatzWord
import de.mediclever.schichtclever.ui.theme.LocalExtra
import de.mediclever.schichtclever.ui.theme.SchichtCleverTheme
import de.mediclever.schichtclever.ui.theme.useDark

private data class Tab(val route: String, val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    /** Ziel aus Kachel oder Benachrichtigung, z. B. "einsatz/<id>". */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        de.mediclever.schichtclever.focus.FocusController.sync(this, Repository.data.value)
        setContent {
            val data by Repository.data.collectAsStateWithLifecycle()
            val dark = data.useDark()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                enableEdgeToEdge(style, style)
                onDispose {}
            }
            SchichtCleverTheme(dark) {
                if (!data.settings.onboardingDone) OnboardingScreen(data, dark)
                else AppScaffold(dark)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    @Composable
    private fun AppScaffold(dark: Boolean) {
        val data by Repository.data.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        val role = data.settings.role
        val tabs = listOf(
            Tab("schicht", "Schicht", Icons.Outlined.Schedule),
            Tab("einsaetze", role.einsatzWord(true), Icons.AutoMirrored.Outlined.FormatListBulleted),
            Tab("statistik", "Statistik", Icons.Outlined.BarChart),
            Tab("skills", "Skills", Icons.Outlined.Psychology),
            Tab("mehr", "Mehr", Icons.Outlined.MoreHoriz),
        )
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route
        val showBar = tabs.any { it.route == current }

        val target = pendingRoute.value
        LaunchedEffect(target) {
            if (target != null) { nav.navigate(target); pendingRoute.value = null }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBar) {
                    Box {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                            tabs.forEach { t ->
                                NavigationBarItem(
                                    selected = current == t.route,
                                    onClick = { nav.switchTab(t.route) },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = LocalExtra.current.accent,
                                        selectedTextColor = LocalExtra.current.accent,
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = LocalExtra.current.muted,
                                        unselectedTextColor = LocalExtra.current.muted,
                                    ),
                                )
                            }
                        }
                        HorizontalDivider(color = LocalExtra.current.line)
                    }
                }
            },
        ) { pad ->
            NavHost(nav, startDestination = "schicht", modifier = Modifier.padding(bottom = if (showBar) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)).padding(top = if (showBar) pad.calculateTopPadding() else androidx.compose.ui.unit.Dp(0f))) {
                composable("schicht") {
                    ShiftScreen(
                        data,
                        onNewEinsatz = { nav.navigate("einsatz/neu") },
                        onOpenEinsatz = { nav.navigate("einsatz/$it") },
                        onOpenFocus = { nav.navigate("fokus") },
                        onOpenFilter = { nav.navigate("filter") },
                        onOpenTemplates = { nav.navigate("vorlagen") },
                    )
                }
                composable("einsaetze") { EinsaetzeScreen(data, dark, onOpen = { nav.navigate("einsatz/$it") }, onNew = { nav.navigate("einsatz/neu") }) }
                composable("statistik") { StatsScreen(data) }
                composable("skills") { SkillsScreen(data, dark, onOpen = { nav.navigate("skill/$it") }) }
                composable("mehr") { MoreScreen(data, dark, onOpenFocus = { nav.navigate("fokus") }, onOpenFilter = { nav.navigate("filter") }, onOpenTemplates = { nav.navigate("vorlagen") }) }
                composable("vorlagen") { TemplatesScreen(data, onBack = { nav.popBackStack() }) }
                composable("einsatz/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.takeIf { it != "neu" }
                    EinsatzEditorScreen(data, id, onClose = { nav.popBackStack() })
                }
                composable("skill/{id}") { entry -> SkillDetailScreen(data, entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() }) }
                composable("fokus") { FocusScreen(data, onBack = { nav.popBackStack() }) }
                composable("filter") { FilterScreen(data, onBack = { nav.popBackStack() }) }
            }
        }
    }

    private fun NavHostController.switchTab(route: String) = navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

package de.mediclever.tagebuch.ui

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.mediclever.tagebuch.core.model.MidnightRule
import de.mediclever.tagebuch.ui.entry.EntryDraft
import de.mediclever.tagebuch.ui.entry.EntryEditScreen
import de.mediclever.tagebuch.ui.entry.Midnight
import de.mediclever.tagebuch.ui.home.HomeScreen
import de.mediclever.tagebuch.ui.home.MenuScreen
import de.mediclever.tagebuch.ui.home.StartScreen
import de.mediclever.tagebuch.ui.more.*
import de.mediclever.tagebuch.ui.setup.SetupScreen

/** Alle Bildschirme und ihre Routen. startRoute kommt z. B. aus einer Benachrichtigung oder dem Widget. */
@Composable
fun AppNav(startRoute: String? = null, presetLevel: Int? = null, onRouteConsumed: () -> Unit = {}) {
    val nav: NavHostController = rememberNavController()
    val env = LocalEnv.current
    val navApi = remember(nav) { Nav(go = { nav.navigate(it) { launchSingleTop = true } }, back = { nav.popBackStack() }, home = { nav.popBackStack(Routes.HOME, false) }) }
    LaunchedEffect(startRoute) { if (!startRoute.isNullOrEmpty()) { nav.navigate(startRoute) { launchSingleTop = true }; onRouteConsumed() } }

    CompositionLocalProvider(LocalNav provides navApi) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { StartScreen() }
            composable("section/{tab}", arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { HomeScreen(startTab = it.arguments?.getInt("tab") ?: 0) }
            composable(Routes.MENU) { MenuScreen() }
            composable(Routes.SEARCH) { SearchScreen() }
            composable("entry/new?date={date}&score={score}&voice={voice}", arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = "" },
                navArgument("score") { type = NavType.IntType; defaultValue = 0 }, navArgument("voice") { type = NavType.BoolType; defaultValue = false })) { entry ->
                val date = entry.arguments?.getString("date").orEmpty()
                val score = entry.arguments?.getInt("score") ?: 0
                NewEntry(date, if (score in 1..5) 6 - score else presetLevel, navApi, entry.arguments?.getBoolean("voice") == true)
            }
            composable("entry/edit/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                // Beim ersten Anzeigen merken, damit der Bildschirm waehrend des Speicherns nicht springt
                val draft = remember(id) { env.diary.entries.firstOrNull { it.id == id }?.let { e -> EntryDraft.from(e, env.diary.goalChecks.filter { it.date == e.localDate }.map { it.goalId }) } }
                if (draft == null) LaunchedEffect(Unit) { nav.popBackStack() }
                else EntryEditScreen(draft, onClose = { nav.popBackStack() }, onOpenSetup = { nav.navigate(if (it == "shifts") Routes.SHIFTS else Routes.setup(it)) })
            }
            composable("setup?section={section}", arguments = listOf(navArgument("section") { type = NavType.StringType; defaultValue = "" })) { SetupScreen(it.arguments?.getString("section").orEmpty()) { nav.popBackStack() } }
            composable(Routes.SHIFTS) { ShiftsScreen() }
            composable(Routes.PERSONAL) { PersonalScreen() }
            composable(Routes.PLUS) { PlusScreen() }
            composable(Routes.KEYWORDS) { KeywordsListScreen() }
            composable("keywords/{id}") { KeywordsScreen(it.arguments?.getString("id").orEmpty()) }
            composable(Routes.GOALS) { GoalsScreen() }
            composable("goal/edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { GoalEditScreen(it.arguments?.getString("id")?.ifEmpty { null }) }
            composable("goal/{id}") { GoalDetailScreen(it.arguments?.getString("id").orEmpty()) }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.MEMORIES) { MemoriesScreen() }
            composable(Routes.DAYS) { ImportantDaysScreen() }
            composable("day/edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { ImportantDayEditScreen(it.arguments?.getString("id")?.ifEmpty { null }) }
            composable(Routes.FAVORITES) { FavoritesScreen() }
            composable(Routes.PHOTOS) { PhotosScreen() }
            composable("photo/{id}") { PhotoViewerScreen(it.arguments?.getString("id").orEmpty()) }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen() }
            composable(Routes.REMINDERS) { RemindersScreen() }
            composable(Routes.APPEARANCE) { AppearanceScreen() }
            composable(Routes.SECURITY) { SecurityScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.DATA) { DataScreen() }
            composable(Routes.ACCOUNT) { AccountScreen() }
            composable(Routes.ABOUT) { AboutScreen() }
        }
    }
}

/** Neuer Eintrag. Ohne Datum gilt heute; kurz nach Mitternacht greift die Einstellung dazu. */
@Composable
private fun NewEntry(date: String, presetLevel: Int?, nav: Nav, voice: Boolean = false) {
    val env = LocalEnv.current
    val now = remember { env.now() }
    val rule = env.diary.settings.midnightRule
    var choice by remember { mutableStateOf<Boolean?>(if (date.isNotEmpty() || !Midnight.applies(now)) false else when (rule) { MidnightRule.ASK -> null; MidnightRule.PREVIOUS_DAY -> true; MidnightRule.CURRENT_DAY -> false }) }
    val previous = choice
    if (previous == null) {
        ConfirmDialog("Für welchen Tag?", "Es ist nach Mitternacht. Gehört der Eintrag noch zu gestern oder schon zum neuen Tag? In den Einstellungen kannst du das dauerhaft festlegen.", "Gestern",
            onConfirm = { choice = true }, onDismiss = { nav.back() }, alternative = "Heute", onAlternative = { choice = false })
        return
    }
    val draft = remember(previous) {
        val mood = presetLevel?.let { l -> env.diary.moods.firstOrNull { it.level == l && it.archivedAt == null } }
        when {
            date.isNotEmpty() -> EntryDraft(date = date, minuteOfDay = if (date == now.toLocalDate().toString()) now.hour * 60 + now.minute else 20 * 60, moodId = mood?.id)
            previous -> Midnight.previousDay(now).let { (d, m) -> EntryDraft(date = d.toString(), minuteOfDay = m, moodId = mood?.id) }
            else -> EntryDraft(date = now.toLocalDate().toString(), minuteOfDay = now.hour * 60 + now.minute, moodId = mood?.id)
        }
    }
    EntryEditScreen(draft, onClose = { nav.back() }, onOpenSetup = { nav.go(if (it == "shifts") Routes.SHIFTS else Routes.setup(it)) }, startVoice = voice)
}

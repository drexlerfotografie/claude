package de.mediclever.tagebuch

import android.app.Application
import de.mediclever.tagebuch.cloud.AuthManager
import de.mediclever.tagebuch.cloud.SyncManager
import de.mediclever.tagebuch.data.AppDatabase
import de.mediclever.tagebuch.data.AssetStore
import de.mediclever.tagebuch.data.DataOps
import de.mediclever.tagebuch.data.DiaryRepository
import de.mediclever.tagebuch.data.LocalPrefs
import de.mediclever.tagebuch.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Haelt die langlebigen Bausteine der App zusammen. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.open(app)
    val prefs = LocalPrefs(app)
    val assets = AssetStore(app)
    val repo = DiaryRepository(db, prefs, assets, scope)
    val auth = AuthManager(app)
    val sync = SyncManager(repo, auth, scope)
    val reminders = ReminderScheduler(app)
    val dataOps = DataOps(app, repo)

    init {
        repo.onLocalChange = { sync.requestSoon() }
        // Hinweise neu planen, sobald sich etwas aendert, das sie betrifft
        scope.launch {
            repo.diary.filterNotNull().map { d -> listOf(d.reminders, d.goals, d.importantDays, d.settings.reportNotifications, d.settings.weekStart, d.entries.isEmpty()) }
                .distinctUntilChanged().collect { repo.diary.value?.let { reminders.reschedule(it) } }
        }
        scope.launch { auth.account.collect { if (it != null) sync.syncNow() } }
        // Widget aktuell halten
        scope.launch { repo.diary.filterNotNull().map { d -> d.entries.firstOrNull()?.let { it.id to it.updatedAt } }.distinctUntilChanged().collect { de.mediclever.tagebuch.widget.TodayWidget.refresh(app) } }
    }
}

class TagebuchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannels(this)
        container = AppContainer(this)
    }
}

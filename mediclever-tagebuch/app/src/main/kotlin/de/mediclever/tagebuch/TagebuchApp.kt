package de.mediclever.tagebuch

import android.app.Application
import de.mediclever.tagebuch.data.AppDatabase
import de.mediclever.tagebuch.data.AssetStore
import de.mediclever.tagebuch.data.DiaryRepository
import de.mediclever.tagebuch.data.LocalPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Haelt die langlebigen Bausteine der App zusammen. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.open(app)
    val prefs = LocalPrefs(app)
    val assets = AssetStore(app)
    val repo = DiaryRepository(db, prefs, assets, scope)
}

class TagebuchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

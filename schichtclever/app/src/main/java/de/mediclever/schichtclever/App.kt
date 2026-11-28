package de.mediclever.schichtclever

import android.app.Application
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.focus.FocusController

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        Repository.addListener { FocusController.sync(this, it) }
    }
}

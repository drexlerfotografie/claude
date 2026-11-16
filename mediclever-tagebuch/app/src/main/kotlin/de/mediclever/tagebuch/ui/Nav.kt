package de.mediclever.tagebuch.ui

import androidx.compose.runtime.compositionLocalOf

/** Navigation zwischen Bildschirmen. Ziele sind die Routen aus AppNav. */
class Nav(val go: (String) -> Unit = {}, val back: () -> Unit = {})

val LocalNav = compositionLocalOf { Nav() }

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    fun newEntry(date: String? = null) = "entry/new?date=${date ?: ""}"
    fun editEntry(id: String) = "entry/edit/$id"
    fun setup(section: String = "") = "setup?section=$section"
    const val GOALS = "goals"
    fun goal(id: String) = "goal/$id"
    fun goalEdit(id: String? = null) = "goal/edit?id=${id ?: ""}"
    const val REPORTS = "reports"
    const val MEMORIES = "memories"
    const val DAYS = "days"
    fun dayEdit(id: String? = null) = "day/edit?id=${id ?: ""}"
    const val FAVORITES = "favorites"
    const val PHOTOS = "photos"
    fun photo(id: String) = "photo/$id"
    const val ACHIEVEMENTS = "achievements"
    const val REMINDERS = "reminders"
    const val APPEARANCE = "appearance"
    const val SECURITY = "security"
    const val SETTINGS = "settings"
    const val DATA = "data"
    const val ACCOUNT = "account"
    const val ABOUT = "about"
}

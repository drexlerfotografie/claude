package app.fotosortierer.data

import android.content.Context
import android.os.Environment

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val storageRoot: String = Environment.getExternalStorageDirectory().path

    var sources: List<String>
        get() = prefs.getString("sources", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = prefs.edit().putString("sources", v.joinToString("\n")).apply()

    var targetRoot: String
        get() = prefs.getString("target", null) ?: "$storageRoot/Pictures/Sortiert"
        set(v) = prefs.edit().putString("target", v).apply()

    var categories: List<String>
        get() = prefs.getString("categories", null)?.split('\n')?.filter { it.isNotBlank() }
            ?: app.fotosortierer.core.Suggest.DEFAULT_CATEGORIES
        set(v) = prefs.edit().putString("categories", v.joinToString("\n")).apply()

    var gapHours: Int
        get() = prefs.getInt("gapHours", 3)
        set(v) = prefs.edit().putInt("gapHours", v).apply()

    var usePlaces: Boolean
        get() = prefs.getBoolean("usePlaces", true)
        set(v) = prefs.edit().putBoolean("usePlaces", v).apply()

    var recursive: Boolean
        get() = prefs.getBoolean("recursive", true)
        set(v) = prefs.edit().putBoolean("recursive", v).apply()
}

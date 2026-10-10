package io.github.menadion.magus

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// What is left of the language switch after Filipino was dropped on 2026-10-10 (M's call, having
// asked both parents, who said the Tagalog localisation was not necessary). The Settings row and
// the values-fil resources are gone, so there is nothing to choose between any more.
//
// The object stays for one reason: phones in the family may have "fil" sitting in their prefs from
// an earlier version, and wrap() applied whatever was stored on every launch. Removing only the row
// would have left such a phone in Filipino permanently, with no UI left to change it back. load()
// now reads a stored "fil" as SYSTEM, and with values-fil deleted there is no Filipino to fall into
// regardless. It can go altogether once no phone in the family is running 0.8 or older; that means
// unpicking attachBaseContext in MainActivity and ShareService, plus Updates and the language field
// Feedback reports.
object LanguageSetting {
    const val SYSTEM = "system"
    const val ENGLISH = "en"

    // Only ever read from a phone's saved prefs now, never written again.
    private const val RETIRED_FILIPINO = "fil"

    // Intent extra: the relaunched screen opens on Settings, where the switch was.
    const val OPEN_SETTINGS = "openSettings"

    var mode by mutableStateOf(SYSTEM)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).getString("language", SYSTEM) ?: SYSTEM
        // A phone that picked Filipino before the switch was removed goes back to following itself.
        mode = if (saved == RETIRED_FILIPINO) SYSTEM else saved
    }

    fun set(activity: Activity, value: String) {
        mode = value
        activity.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().putString("language", value).apply()
        activity.startActivity(Intent(activity, activity.javaClass).putExtra(OPEN_SETTINGS, true))
        activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    // The base context with the chosen language, or the base itself on Auto.
    fun wrap(base: Context): Context {
        load(base)
        if (mode == SYSTEM) return base
        val locale = Locale.forLanguageTag(mode)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}

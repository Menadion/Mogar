package io.github.menadion.magus

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

// How close people must be, in metres, to show as one group on the map. Per phone: each person sets
// their own, so the family has no shared number (M's call, 2026-11-02). 0 means "overlap only": people
// group only while their dots touch on screen. Read from the map as state, so Save regroups at once.
object GroupRadius {
    const val MAX = 500

    // A first guess, to be tuned on a real street once it has been used (M, 2026-11-02).
    const val DEFAULT = 50

    var meters by mutableIntStateOf(DEFAULT)
        private set

    fun load(context: Context) {
        meters = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).getInt("group_radius", DEFAULT).coerceIn(0, MAX)
    }

    fun set(context: Context, value: Int) {
        meters = value.coerceIn(0, MAX)
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().putInt("group_radius", meters).apply()
    }
}

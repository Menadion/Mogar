package io.github.menadion.magus

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Rain on the map: RainViewer's radar tiles painted under the family dots. Free and keyless, the
// same arrangement as the base tiles from OpenFreeMap, so nothing here needs an account or a quota.
//
// Off by default and per phone, because most of the time there is nothing to see and a painted map
// is harder to read than a plain one.
//
// What this cannot do, known and accepted when it was built (2026-11-05): radar is ground hardware
// with a range of a couple of hundred kilometres, so the picture only exists where a national
// network reaches. RainViewer's coverage list has nine radars for the Philippines and none at all
// for Libya, so M's father sees an unchanged map with the switch on. What the app should SAY in
// that case is not ruled yet.
object Rain {
    private const val CATALOGUE = "https://api.rainviewer.com/public/weather-maps.json"

    // RainViewer's colour ramp 4, the one closest to a television forecast, with their smoothing
    // and separate snow colours on (the "1_1" options pair).
    private const val SCHEME = "4"
    private const val OPTIONS = "1_1"
    private const val TILE_SIZE = 256

    // A frame covers ten minutes. Asking a little sooner means a stale one is never left up.
    const val REFRESH_MS = 8L * 60 * 1000

    var on by mutableStateOf(false)
        private set

    fun load(context: Context) {
        on = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    // The newest radar frame, as a tile template MapLibre can take. Null when the catalogue cannot
    // be reached or carries no frames. Blocking, so call it off the main thread.
    fun latestTiles(context: Context): String? {
        val connection = (URL(CATALOGUE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Mogar/${Diagnostics.appVersion(context)}")
        }
        try {
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val host = json.getString("host")
            val past = json.getJSONObject("radar").optJSONArray("past") ?: return null
            if (past.length() == 0) return null
            val path = past.getJSONObject(past.length() - 1).getString("path")
            return "$host$path/$TILE_SIZE/{z}/{x}/{y}/$SCHEME/$OPTIONS.png"
        } finally {
            connection.disconnect()
        }
    }

    private const val KEY = "rain_overlay"
}

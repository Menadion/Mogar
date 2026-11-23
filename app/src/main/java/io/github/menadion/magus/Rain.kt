package io.github.menadion.magus

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Weather on the map: two pictures of the same sky, stacked. Both free and keyless, the same
// arrangement as the base tiles from OpenFreeMap, so nothing here needs an account or a quota.
//
// Off by default and per phone, because a painted map is harder to read than a plain one.
//
// SATELLITE underneath -- Himawari-9, parked over 140.7E, so it looks almost straight down on the
// Philippines. NASA's GIBS serves band 13 (clean infrared: how cold each cloud top is) as ordinary
// web-mercator PNG tiles, and serves it with the enhancement curve already applied -- grey for warm
// low cloud, then blue, green, yellow and red as the tops get colder and the weather under them
// gets more violent. It is a satellite, so it has no holes at all: the opposite failure mode from
// radar, and the reason it carries the country-wide view.
//
// RADAR on top -- RainViewer, as before. Radar is ground hardware with a range of a couple of
// hundred kilometres, so it only exists where a national network reaches: nine dishes for the
// Philippines. What it gives back is rain actually reaching the ground, sharp at any zoom, where
// the satellite only ever sees the top of the cloud.
//
// The two colour scales mean different things on purpose. Satellite colour is a cold cloud top,
// which is a storm's shape and height. Radar colour is water falling on somebody. Together they
// answer "is that big thing going to hit us" and "is it raining on Mamie right now".
object Rain {
    private const val CATALOGUE = "https://api.rainviewer.com/public/weather-maps.json"

    // Himawari band 13, newest frame. GIBS takes the literal word "default" where a timestamp
    // would go and answers with the latest frame it holds, so nothing here needs a clock, a
    // timestamp format, or a guess at how far behind the satellite is running (measured
    // 2026-11-16: about 38 minutes, on a 10-minute cadence).
    //
    // GIBS puts y before x in the path, which is not the order the radar host uses. MapLibre fills
    // the placeholders by name so the order does not matter -- do not "correct" it.
    const val SATELLITE = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
        "Himawari_AHI_Band13_Clean_Infrared/default/default/GoogleMapsCompatible_Level6/" +
        "{z}/{y}/{x}.png"

    // The ceiling is zoom 6 -- zoom 7 is a flat HTTP 400 -- so the source is told, and MapLibre
    // stretches the last real tiles instead of asking for one that does not exist.
    const val SATELLITE_MAX_ZOOM = 6f

    // Radar has a ceiling too, and it is the nastier kind: above zoom 7 RainViewer answers 200 and
    // hands back a picture of the words "Zoom Level Not Supported" -- black at alpha 140 with white
    // lettering, byte-identical at zoom 8, 12 and 16 (measured 2026-11-16, 1370 bytes, md5
    // 2cc6649e1f). Nothing in the response says it is an error, so the map simply painted it: grey
    // slabs of error text lying over the streets wherever the switch was on and the map was closer
    // than a city. It was there from the day the overlay shipped and is nothing to do with the
    // satellite. Capping the source stops the request being made at all, and MapLibre stretches the
    // zoom-7 tile instead -- blocky, which is honest, since a radar pixel is about a kilometre wide.
    const val RADAR_MAX_ZOOM = 7f

    // The radar frame, plus the satellite that goes under it.
    data class Tiles(val radar: String, val satellite: String = SATELLITE)

    // RainViewer's colour ramp 4, the one closest to a television forecast, with their smoothing
    // and separate snow colours on (the "1_1" options pair).
    private const val SCHEME = "4"
    private const val OPTIONS = "1_1"
    private const val TILE_SIZE = 256

    // A frame covers ten minutes. Asking a little sooner means a stale one is never left up.
    const val REFRESH_MS = 8L * 60 * 1000

    // The dim over the map while weather loads gives up after this long (M, 2026-11-23, down from
    // the 15 s first floated: on a slow link it is the map the family is looking at that is dimmed).
    const val LOAD_CAP_MS = 10_000L

    // Himawari-9's view of the Earth, measured 2026-11-23 by asking GIBS for tiles along the equator
    // and comparing sizes: a tile with picture is 17 KB or more, one with nothing is 334 or 1189 bytes.
    // Real picture runs from about 60E eastward across the date line to about 140W; 0 to 56E and
    // 135W to 90W (everything from Libya to the Americas) came back empty. These are the edges nudged
    // inward to where the picture is solid. Used for one thing: the message that says weather may
    // not show. NOTE: RainViewer's radar reaches places this does not (Europe, the US), so the
    // message over-claims there; the family is in the Philippines and Libya, where it holds.
    private const val SATELLITE_WEST_EDGE_E = 65.0
    private const val SATELLITE_EAST_EDGE_W = -140.0
    fun satelliteCovers(lng: Double) = lng >= SATELLITE_WEST_EDGE_E || lng <= SATELLITE_EAST_EDGE_W

    var on by mutableStateOf(false)
        private set

    fun load(context: Context) {
        on = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    // The newest radar frame, as a tile template MapLibre can take, paired with the satellite,
    // which is a fixed URL and needs no asking. Null when the catalogue cannot be reached or
    // carries no frames. Blocking, so call it off the main thread.
    fun latest(context: Context): Tiles? {
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
            return Tiles(radar = "$host$path/$TILE_SIZE/{z}/{x}/{y}/$SCHEME/$OPTIONS.png")
        } finally {
            connection.disconnect()
        }
    }

    private const val KEY = "rain_overlay"
}

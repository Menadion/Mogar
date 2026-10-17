package io.github.menadion.magus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

// Is there a newer Mogar? GitHub's latest release of Menadion/Mogar is the one source of truth:
// its tag is the version, its .apk asset is the download, its notes are the one line of changes.
// Asked once a day when the map opens, and every time the Settings row is tapped (M's calls,
// 2026-09-25). A newer release puts the red dot on the gear and on the row.
// The new version is downloaded inside Mogar and handed to Android's installer from the same
// button: Download, Downloading… 45%, Install (M's flow, 2026-09-26).
object Updates {
    private const val LATEST = "https://api.github.com/repos/Menadion/Mogar/releases/latest"
    private const val CHANNEL_ID = "updates"
    // The sharing reminder is 1; the download's progress is its own notification beside it.
    private const val NOTIFICATION_ID = 2

    class Release(val version: String, val notes: String, val url: String)

    // BUSY: GitHub refused because too many checks came from this internet address this hour.
    enum class State { UNKNOWN, CHECKING, CHECKED, FAILED, BUSY }

    private class TooManyChecks : Exception()

    var state by mutableStateOf(State.UNKNOWN)
        private set

    // The newest release GitHub has told us about, kept across opens.
    var latest by mutableStateOf<Release?>(null)
        private set

    // A version whose download turned out to hold different bytes than its tag claimed. Kept in
    // prefs, because forgetting it means fetching the same 25 MB lie again tomorrow, and daily after.
    var broken by mutableStateOf<String?>(null)
        private set

    private var installed = "0"

    // FAILED: the last download broke off; the button offers it again.
    // BROKEN: the file arrived whole but is not the version the release promised, so it was thrown
    // away and nothing is offered.
    enum class Download { IDLE, DOWNLOADING, FAILED, BROKEN }

    var download by mutableStateOf(Download.IDLE)
        private set

    // 0 to 100 while downloading.
    var percent by mutableStateOf(0)
        private set

    // Outlives the Settings screen: leaving the app doesn't stop a download (M's call, 2026-09-26).
    // Mogar's sharer keeps the app running in the background, so the download finishes there.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The release to offer, or null when this phone already runs the newest one.
    val newer: Release? get() = latest?.takeIf { isNewer(it.version, installed) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // On app start: remembers the last answer, so the dot shows without waiting for today's check.
    fun load(context: Context) {
        installed = Diagnostics.appVersion(context)
        cleanUp(context)
        val p = prefs(context)
        broken = p.getString("updateBroken", null)
        val version = p.getString("updateLatest", null) ?: return
        val url = p.getString("updateUrl", null) ?: return
        latest = Release(version, p.getString("updateNotes", "") ?: "", url)
        state = State.CHECKED
    }

    // Once a day, when the map opens. A failed check stays quiet and the next open tries again.
    suspend fun checkDaily(context: Context) {
        if (prefs(context).getString("updateCheckedDay", null) == LocalDate.now().toString()) return
        check(context)
    }

    // Asks GitHub now and records what it said.
    suspend fun check(context: Context) {
        installed = Diagnostics.appVersion(context)
        state = State.CHECKING
        val result = withContext(Dispatchers.IO) { runCatching { fetchLatest(context) } }
        result.onSuccess { found ->
            val editor = prefs(context).edit().putString("updateCheckedDay", LocalDate.now().toString())
            if (found != null) {
                latest = found
                editor.putString("updateLatest", found.version)
                    .putString("updateNotes", found.notes)
                    .putString("updateUrl", found.url)
            } else {
                // GitHub says there is no release at all. That is an answer, not a missing one, so
                // forget whatever was last offered: only the found branch used to write, and an
                // offer for a release that has since been deleted would otherwise stand for ever.
                // Not hypothetical — v0.9 was deleted on 2026-10-11, and had it been the only
                // release every phone would still be offering it.
                //
                // Its download goes too. cleanUp keeps anything newer than what is installed, so a
                // file for a release that no longer exists is newer than everything for ever and
                // would sit in the cache at 25 MB with nothing left to install it.
                latest?.let { apkFile(context, it.version).delete() }
                latest = null
                editor.remove("updateLatest").remove("updateNotes").remove("updateUrl")
            }
            editor.apply()
            state = State.CHECKED
        }.onFailure {
            state = if (it is TooManyChecks) State.BUSY else State.FAILED
        }
    }

    // Null when there is no release yet: GitHub answers 404 until the first one is published.
    private fun fetchLatest(context: Context): Release? {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Mogar/${Diagnostics.appVersion(context)}")
        }
        try {
            if (connection.responseCode == 404) return null
            // GitHub allows 60 checks an hour per internet address without an account, and a whole
            // home Wi-Fi or a carrier's shared address counts as one. When they're used up it answers
            // 403 or 429 with no requests remaining.
            if (connection.responseCode in listOf(403, 429) && connection.getHeaderField("x-ratelimit-remaining") == "0") {
                throw TooManyChecks()
            }
            if (connection.responseCode != 200) throw IllegalStateException("GitHub answered ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val version = json.getString("tag_name").removePrefix("v")
            val notes = json.optString("body").lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            var url = json.getString("html_url")
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        url = asset.getString("browser_download_url")
                        break
                    }
                }
            }
            return Release(version, notes, url)
        } finally {
            connection.disconnect()
        }
    }

    // "0.10" is newer than "0.9": compared number by number, not as text.
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val b = installed.split(".").map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // Opens the release page in the browser: only when a release has no .apk to download.
    private fun open(context: Context, release: Release) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
    }

    // Downloaded updates live in Mogar's own storage, one file per version.
    private fun folder(context: Context) = File(context.cacheDir, "updates")
    private fun apkFile(context: Context, version: String) = File(folder(context), "Mogar-$version.apk")

    // True when this release is downloaded and waiting for Install.
    fun isReady(context: Context, release: Release) =
        download != Download.DOWNLOADING && apkFile(context, release.version).exists()

    // What the downloaded file really is, read out of the file itself. The tag is a promise and the
    // APK is the fact: v0.9 was published holding a 0.8 build (2026-10-10), so every phone installed
    // it, still read 0.8, and was offered the same update again the next day.
    private fun versionInside(context: Context, file: File): String? =
        context.packageManager.getPackageArchiveInfo(file.path, 0)?.versionName

    // Throws a download away unless it really is newer than what is installed, and remembers the
    // version that lied so the daily check stops fetching it. Called once, when the file lands,
    // never from isReady: reading a 25 MB archive on every redraw would stutter the screen.
    private suspend fun trustworthy(context: Context, release: Release): Boolean {
        val file = apkFile(context, release.version)
        val inside = versionInside(context, file)
        // Two things have to hold: the file is the version its release promised, and that version is
        // really ahead of this phone. Checking only the second lets a release tagged 0.11 that holds
        // a 0.10 build through, which installs 0.10 and is then offered 0.11 again the next day.
        if (inside == release.version && isNewer(inside, Diagnostics.appVersion(context))) return true
        Log.w("Mogar", "release ${release.version} holds ${inside ?: "an unreadable APK"}")
        file.delete()
        prefs(context).edit().putString("updateBroken", release.version).apply()
        withContext(Dispatchers.Main) { broken = release.version }
        return false
    }

    fun startDownload(context: Context, release: Release) {
        if (download == Download.DOWNLOADING) return
        if (!release.url.endsWith(".apk")) {
            open(context, release)
            return
        }
        // The download mustn't hold the screen.
        val app = context.applicationContext
        download = Download.DOWNLOADING
        percent = 0
        scope.launch {
            val result = runCatching { fetchApk(app, release) }
            app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            val trusted = result.isSuccess && trustworthy(app, release)
            withContext(Dispatchers.Main) {
                download = when {
                    result.isFailure -> Download.FAILED
                    !trusted -> Download.BROKEN
                    else -> Download.IDLE
                }
            }
        }
    }

    // startDownload fires the download off and returns at once, which is right for a button and
    // wrong for the background check: that has to know whether the file actually arrived before it
    // can decide which notification to post. Same work, awaited.
    suspend fun downloadAndWait(context: Context, release: Release): Boolean {
        if (!release.url.endsWith(".apk")) return false
        val app = context.applicationContext
        withContext(Dispatchers.Main) {
            download = Download.DOWNLOADING
            percent = 0
        }
        val result = runCatching { fetchApk(app, release) }
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        val trusted = result.isSuccess && trustworthy(app, release)
        withContext(Dispatchers.Main) {
            download = when {
                result.isFailure -> Download.FAILED
                !trusted -> Download.BROKEN
                else -> Download.IDLE
            }
        }
        return trusted
    }

    // Writes to a .part file and renames it when complete, so a broken-off download never looks ready.
    private suspend fun fetchApk(context: Context, release: Release) {
        folder(context).mkdirs()
        val target = apkFile(context, release.version)
        val part = File(target.path + ".part")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.update_notification, release.version))
            .setContentIntent(openApp)
            // Its own group, headed by itself, or Android folds it under the sharing reminder
            // instead of beside it. A group member with no header is hidden altogether.
            .setGroup(CHANNEL_ID)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        val connection = (URL(release.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Mogar/${Diagnostics.appVersion(context)}")
        }
        try {
            if (connection.responseCode != 200) throw IOException("GitHub answered ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = -1
                    var notifiedAt = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = if (total > 0) (done * 100 / total).toInt() else 0
                        if (now != shown) {
                            shown = now
                            withContext(Dispatchers.Main) { percent = now }
                        }
                        // Once a second at most: past 5 a second Android throws the updates away.
                        val time = System.currentTimeMillis()
                        if (time - notifiedAt >= 1000) {
                            notifiedAt = time
                            manager.notify(NOTIFICATION_ID, notification.setProgress(100, now, total <= 0).build())
                        }
                    }
                }
            }
            if (total > 0 && part.length() != total) throw IOException("Download cut short")
            if (!part.renameTo(target)) throw IOException("Couldn't save the download")
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    // True when this phone lets Mogar hand an update to the installer ("Install unknown apps").
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    // Android's "Install unknown apps" page, opened on Mogar's own switch.
    fun permissionPage(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    // Hands the downloaded file to Android's installer, which asks "Update this app?" and ends on Open.
    fun install(context: Context, release: Release) {
        val file = apkFile(context, release.version)
        if (!file.exists()) {
            download = Download.IDLE
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    // Deletes downloaded updates this phone no longer needs: any version not newer than the one
    // installed, and a broken-off download. Runs when Mogar opens and right after it's updated.
    fun cleanUp(context: Context) {
        val installed = Diagnostics.appVersion(context)
        folder(context).listFiles()?.forEach { file ->
            when {
                file.name.endsWith(".part") -> if (download != Download.DOWNLOADING) file.delete()
                !isNewer(file.name.removePrefix("Mogar-").removeSuffix(".apk"), installed) -> file.delete()
            }
        }
    }
}

// The red dot that says "a newer Mogar exists": on the gear's upper-right corner and on the update row.
@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(11.dp)
            .background(colors.error, CircleShape)
            .border(2.dp, colors.surfaceContainerLowest, CircleShape),
    )
}

package io.github.menadion.magus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// Noticing a new Mogar without anyone opening Mogar.
//
// Why: checkDaily only ever ran from MainActivity, so a phone whose owner never opens the app
// never checked, and M ended up being the reminder in person. This checks on its own, roughly
// daily, and tells them when an update is ready.
//
// What this cannot do, and no app on an ordinary phone can: install it for them. Android hands the
// final "Update this app?" to the person, and REQUEST_INSTALL_PACKAGES only buys the right to ask.
// Everything before that tap is what this removes.
//
// Downloads wait for Wi-Fi (M's call, 2026-10-10). The check itself is a few KB of JSON and runs on
// any connection, but the APK is around 25 MB, and spending that silently on a prepaid SIM would
// burn the very thing this family runs out of; his father is also overseas, where it may be
// roaming. On mobile data they get told instead, and choose.
object AutoUpdate {
    private const val WORK = "mogar-update-check"
    private const val CHANNEL_ID = "update-ready"
    private const val NOTIFICATION_ID = 4

    // MainActivity reads this off the notification's intent and takes them to the installer.
    const val INSTALL_UPDATE = "installUpdate"

    // Set when they tap the notification, cleared once the installer has been handed the file.
    //
    // The extra alone was not enough. The notification brings the activity that is already running
    // back to the front rather than making a new one, and getIntent() then still answers with the
    // intent that first launched the app, so the extra was never seen. onNewIntent now sets this,
    // and the screen watches it, which also solves the second half: a LaunchedEffect keyed on Unit
    // cannot fire again on a screen that is already composed.
    var installRequested by mutableStateOf(false)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // On unless they turn it off: the people this is for are the ones who would never find the
    // setting, which is the whole reason it exists.
    fun enabled(context: Context) = prefs(context).getBoolean("autoUpdate", true)

    fun set(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("autoUpdate", on).apply()
        if (on) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    // KEEP, so reopening the app does not reset the clock and start the wait again.
    fun schedule(context: Context) {
        if (!enabled(context)) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build(),
        )
    }

    // Wi-Fi, or anything else the phone does not count against an allowance.
    fun unmetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun notify(context: Context, title: String, body: String, install: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_update_ready),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(INSTALL_UPDATE, install)
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .setAutoCancel(true)
                .build(),
        )
    }

    fun notifyReady(context: Context, release: Updates.Release) = notify(
        context,
        context.getString(R.string.update_ready_title, release.version),
        context.getString(R.string.update_ready_body),
        install = true,
    )

    fun notifyWaiting(context: Context, release: Updates.Release) = notify(
        context,
        context.getString(R.string.update_waiting_title, release.version),
        context.getString(R.string.update_waiting_body),
        install = false,
    )
}

// One run of the check. Returns success even when nothing worked: a failed check is not worth
// retrying hard, the next run is a day away, and WorkManager backing off would only delay it.
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AutoUpdate.enabled(context)) return Result.success()

        // Updates keeps its answer in Compose state, which the screen reads, so the parts that
        // touch it stay on the main thread even though nothing is on screen here.
        val release = withContext(Dispatchers.Main) {
            Updates.load(context)
            runCatching { Updates.check(context) }
            Updates.newer
        } ?: return Result.success()

        // A release that already proved it holds the wrong build: no notification, no download.
        // Reported on the Settings row instead, where there is room to say it.
        if (release.version == Updates.broken) return Result.success()

        if (Updates.isReady(context, release)) {
            AutoUpdate.notifyReady(context, release)
            return Result.success()
        }
        if (!AutoUpdate.unmetered(context)) {
            AutoUpdate.notifyWaiting(context, release)
            return Result.success()
        }
        if (Updates.downloadAndWait(context, release)) {
            AutoUpdate.notifyReady(context, release)
        } else {
            AutoUpdate.notifyWaiting(context, release)
        }
        return Result.success()
    }
}

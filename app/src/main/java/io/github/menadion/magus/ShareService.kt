package io.github.menadion.magus

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// Every 5 minutes, even with the app closed.
private const val SHARE_EVERY_MS = 5 * 60_000L

private const val CHANNEL_ID = "sharing"
private const val NOTIFICATION_ID = 1

// The "sharing stopped, open Mogar" notice, its own channel so it can make a sound.
private const val STOPPED_CHANNEL_ID = "stopped"
private const val STOPPED_NOTIFICATION_ID = 3

// Keeps sending my location in the background. Android only allows this with a permanent notification,
// which doubles as the reminder that sharing is on.
class ShareService : Service() {
    // The notification's words follow the language switch too.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSetting.wrap(newBase))
    }

    private lateinit var client: FusedLocationProviderClient
    private var listening = false

    private val onLocation = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { Family.sendLocation(this@ShareService, it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        clearStoppedNotice(this)
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission") // start() only runs this after checking the permission
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: SecurityException) {
            // Android let the sharer start from the background (after a restart) but won't give it
            // location there: on Android 14+ that needs the battery exemption from 'Keep Mogar running'.
            // Don't crash; ask them to open Mogar, which starts it from the foreground.
            showStoppedNotice(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) {
            listening = true
            // Balanced: Wi-Fi and cell towers, roughly 100 m, light on battery.
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, SHARE_EVERY_MS)
                .setMinUpdateIntervalMillis(SHARE_EVERY_MS)
                .build()
            client.requestLocationUpdates(request, onLocation, Looper.getMainLooper())
            // Send one right away, so switching sharing on shows up without a 5-minute wait.
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location -> location?.let { Family.sendLocation(this, it) } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        client.removeLocationUpdates(onLocation)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_sharing), NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.sharing_with, Family.familyLabel(this)))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        // True while the background sharer is alive in this process.
        @Volatile var isRunning = false

        // True when the phone is in a family with sharing switched on, whether or not the sharer runs.
        fun wanted(context: Context): Boolean =
            Family.savedCode(context) != null && Family.isSharing(context)

        // Starts sharing if it's switched on and location is allowed. Safe to call more than once.
        // fromBackground: called with no screen open (after a restart), which needs "Allow all the time".
        // Returns whether the sharer was asked to start.
        fun start(context: Context, fromBackground: Boolean = false): Boolean {
            if (!wanted(context)) return false
            if (!hasLocationPermission(context)) return false
            if (fromBackground && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) return false
            // A phone with Mogar set to "Restricted" battery use refuses a background start outright.
            if (fromBackground && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted) {
                return false
            }
            return try {
                ContextCompat.startForegroundService(context, Intent(context, ShareService::class.java))
                true
            } catch (e: IllegalStateException) {
                // Android 12+ throws ForegroundServiceStartNotAllowedException (an IllegalStateException)
                // when a background start is refused for a reason the check above didn't catch, such as
                // a vendor skin's own limits. Before this catch, the throw crashed the boot receiver.
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShareService::class.java))
        }

        // "Sharing stopped. Open Mogar to turn it back on." Posted by the boot receiver when sharing was
        // on but couldn't be started from the background; opening the app starts it and clears this.
        fun showStoppedNotice(context: Context) {
            val app = LanguageSetting.wrap(context)
            val manager = app.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(STOPPED_CHANNEL_ID, app.getString(R.string.channel_stopped), NotificationManager.IMPORTANCE_DEFAULT)
            )
            val openApp = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            manager.notify(
                STOPPED_NOTIFICATION_ID,
                NotificationCompat.Builder(app, STOPPED_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle(app.getString(R.string.sharing_stopped_title))
                    .setContentText(app.getString(R.string.sharing_stopped_text))
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .build()
            )
        }

        fun clearStoppedNotice(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(STOPPED_NOTIFICATION_ID)
        }

        private fun hasLocationPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
}

package io.github.menadion.magus

import android.app.Application
import android.content.Context
import android.os.Build

// What this phone is and how its setup went, sent with every location so a quiet dot can be read
// from afar: which phone, which Mogar, how each keep-running step stands, whether the sharer was
// alive the last time the app opened, and the last crash if there was one.
object Diagnostics {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    // Called when the map screen opens: was the background sharer already running on its own?
    fun noteAppOpened(context: Context) {
        prefs(context).edit().putBoolean("sharerRunningAtOpen", ShareService.isRunning).apply()
    }

    fun snapshot(context: Context): Map<String, Any?> = mapOf(
        "brand" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "android" to Build.VERSION.RELEASE,
        "app" to appVersion(context),
        "unrestricted" to KeepRunning.isUnrestricted(context),
        "brandStepDone" to (KeepRunning.brand == KeepRunning.Brand.OTHER || KeepRunning.brandStepDone(context)),
        // Did this phone have any brand page we could open? A tick with this false was made blind,
        // off written directions, which is legitimate but worth being able to tell apart.
        "brandPageFound" to (KeepRunning.brand == KeepRunning.Brand.OTHER || KeepRunning.firstWorking(context) != null),
        // Their own report that the button led nowhere useful.
        "brandPageFailed" to KeepRunning.pageFailed(context),
        // Step 3, and the only step the app can settle by itself rather than take their word for.
        // It went unreported until 2026-10-10, when a vivo 1910 turned out to have it undone and
        // the only way that surfaced was a photo of the phone.
        "neverPaused" to KeepRunning.neverPaused(context),
        "sharerRunningAtOpen" to prefs(context).getBoolean("sharerRunningAtOpen", false),
        "lastCrash" to prefs(context).getString("lastCrash", null),
    )

    // Catches a crash, keeps its first lines for the next send, then lets Android handle it as usual.
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val head = error.stackTraceToString().lineSequence().take(4).joinToString(" | ").take(600)
                prefs(app).edit().putString("lastCrash", head).commit()
            } catch (ignored: Exception) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    // One grey line for the member card: "Xiaomi 23021RAA2Y · Android 14 · Mogar 0.2 · steps done".
    fun summary(diag: Map<String, Any?>?): String? {
        if (diag == null) return null
        val phone = listOfNotNull(diag["brand"]?.toString()?.replaceFirstChar { it.uppercase() }, diag["model"]?.toString())
            .joinToString(" ")
        val steps = when {
            diag["unrestricted"] == false -> "battery restricted"
            diag["brandStepDone"] == false -> "brand step not done"
            else -> "steps done"
        }
        return listOf(phone, "Android ${diag["android"] ?: "?"}", "Mogar ${diag["app"] ?: "?"}", steps)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
}

// The file every setting on this phone is saved in, still named after the app's first name.
// Renaming it would wipe the family code and name on phones that already have Mogar.
const val PREFS_FILE = "magus"

class MogarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
    }
}

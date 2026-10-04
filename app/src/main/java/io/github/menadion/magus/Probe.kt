package io.github.menadion.magus

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// What the brand's settings pages are really called ON THIS PHONE.
//
// Why this exists: the Keep running steps deep-link into pages that belong to the manufacturer, not
// to Android. The names are undocumented and move between ROM versions, and the phone that showed
// the problem (M's father's Vivo V2427 on Android 16) is overseas and cannot be plugged in. So the
// phone answers the question itself and sends the answer back, once.
//
// Only runs with consent (see Consent), and only once per Mogar version, so a ROM that changes
// under a later release gets re-read. Writes to probes/{uid}: one document per phone, replaced
// rather than piled up. No phone can read them; tools/read-phones.mjs does, on the laptop.
object Probe {
    // The manufacturers' own settings apps. Must match the <queries> block in the manifest, or
    // Android 11 and up hide them and every answer below comes back empty.
    private val PACKAGES = listOf(
        "com.miui.securitycenter",
        "com.miui.powerkeeper",
        "com.vivo.permissionmanager",
        "com.iqoo.secure",
        "com.vivo.abe",
        "com.samsung.android.lool",
        "com.samsung.android.sm",
        "com.samsung.android.sm_cn",
    )

    // A settings app holds hundreds of activities; these are the words the ones we want contain.
    private val WORDS = listOf(
        "autostart", "bgstart", "background", "power", "battery", "whitelist", "permission", "sleep",
    )

    private const val PER_PACKAGE = 40

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun dueNow(context: Context): Boolean =
        Consent.allowed(context) &&
            prefs(context).getString("probedVersion", null) != Diagnostics.appVersion(context)

    private fun markDone(context: Context) =
        prefs(context).edit().putString("probedVersion", Diagnostics.appVersion(context)).apply()

    // Each candidate we might send someone to, and what this phone actually has:
    //   ok           → the page is there and can be opened
    //   not exported → the page is there but closed to other apps, so NO name will ever reach it
    //   missing      → this ROM does not have that page at all
    private fun candidateStates(context: Context): Map<String, String> =
        KeepRunning.allCandidates().associate { component ->
            val key = "${component.packageName}/${component.className}"
            key to try {
                if (context.packageManager.getActivityInfo(component, 0).exported) "ok" else "not exported"
            } catch (e: Exception) {
                "missing"
            }
        }

    // Everything in each settings app whose name looks like the page we want, so a page that was
    // renamed can be spotted rather than guessed at.
    private fun matchingActivities(context: Context): Map<String, List<String>> {
        val found = mutableMapOf<String, List<String>>()
        for (pkg in PACKAGES) {
            val activities = try {
                context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities
            } catch (e: Exception) {
                null // not installed on this phone, which is itself worth knowing by its absence
            } ?: continue
            val names = activities
                .map { it.name }
                .filter { name -> WORDS.any { name.lowercase().contains(it) } }
                .sorted()
                .take(PER_PACKAGE)
            found[pkg] = names
        }
        return found
    }

    // Withdraws this phone's report, and forgets that it ever sent one so a later yes probes again.
    // Called when someone answers no, so "no" reaches the probe as well as the diagnostics.
    fun clear(context: Context) {
        prefs(context).edit().remove("probedVersion").apply()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("probes").document(uid).delete()
            .addOnFailureListener { Log.w("Mogar", "could not withdraw probe", it) }
    }

    fun run(context: Context) {
        if (!dueNow(context)) return
        // Already signed in by the time the map is up; no point probing before there is a family.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val report = mapOf(
            "brand" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android" to Build.VERSION.RELEASE,
            // The manufacturer's own ROM version, which is what actually moves these pages around.
            "rom" to Build.DISPLAY,
            "romBuild" to Build.VERSION.INCREMENTAL,
            "app" to Diagnostics.appVersion(context),
            "candidates" to candidateStates(context),
            "found" to matchingActivities(context),
            "sentAt" to FieldValue.serverTimestamp(),
        )
        FirebaseFirestore.getInstance().collection("probes").document(uid).set(report)
            .addOnSuccessListener { markDone(context) }
            .addOnFailureListener { Log.w("Mogar", "probe failed to send", it) }
    }
}

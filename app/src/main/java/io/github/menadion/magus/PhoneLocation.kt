package io.github.menadion.magus

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat

// The two switches outside Mogar that decide whether this phone can report where it is: the
// location permission Mogar was given, and the phone's own location switch, which belongs to every
// app at once. Mogar's own sharing pill is a third, separate thing and lives in Family.
//
// Keeping them apart is the whole point: the pill is what they want, these two are what the phone
// will allow, and before this they could disagree with nothing on screen saying so.
object PhoneLocation {

    // Can this phone report a position at all right now? Both outside switches have to be on.
    fun canReport(context: Context): Boolean = hasPermission(context) && isSwitchedOn(context)

    fun hasPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    // isLocationEnabled would answer this in one call but needs API 28, and Mogar goes back to 26.
    // Asking the two providers works on every version and gives the same answer.
    fun isSwitchedOn(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // Watches the phone's location switch being turned on or off, by any app or from the quick
    // settings shade, and says which way it went. Returns the receiver, to unregister later.
    //
    // Registered in code, not in the manifest. Both of these actions are implicit broadcasts, and
    // since Android 8 those are not delivered to a manifest-declared receiver at all: tested on
    // the emulator, the system never even considered Mogar's. Every other app listening for them
    // registers at runtime too, which is what gave it away.
    //
    // That leaves two live watchers and one gap, which the gap's own case already covers:
    //   - ShareService registers one, so location going off is caught whenever sharing is on,
    //     app open or closed. This is the half that matters, because it is the half that lies.
    //   - The map screen registers one, so both directions are caught while they are looking.
    //   - With sharing off and Mogar closed, nothing is running to catch location coming back.
    //     The check when Mogar is next opened picks it up instead, which is the same moment they
    //     would have seen it anyway.
    fun watchSwitch(context: Context, onChange: (canReport: Boolean) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                val allowed = canReport(context)
                Log.d("Mogar", "location switch changed: canReport=$allowed")
                onChange(allowed)
            }
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return receiver
    }
}

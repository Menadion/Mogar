package io.github.menadion.magus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

// After the phone restarts, or Mogar is updated, gets background sharing going again if it was on.
// After an update it also deletes the downloaded file the update came from.
//
// On Android 11 and up a location service started from here runs but gets no location at all
// (Android's while-in-use rule: a boot or update broadcast is not one of its exemptions, and neither
// the battery exemption nor "Allow all the time" lifts it). Its notification would say "Sharing your
// location" while nothing is sent. So there we don't start it: we post "Sharing stopped. Open Mogar
// to turn it back on." Opening Mogar starts the sharer from the foreground, with full access.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Updates.cleanUp(context)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!ShareService.wanted(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ShareService.showStoppedNotice(context)
            return
        }
        // Android 8 to 10: a background start still gets location. If Android refuses the start
        // anyway (a "Restricted" battery setting), fall back to the notice.
        if (!ShareService.start(context, fromBackground = true)) ShareService.showStoppedNotice(context)
    }
}

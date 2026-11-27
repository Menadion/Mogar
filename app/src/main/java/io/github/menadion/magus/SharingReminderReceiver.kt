package io.github.menadion.magus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// The sharing reminder alarm landing (SharingReminders.arm sets it). Does what is due, posts the
// notification, and sets the next alarm. Not exported: only Mogar's own alarm reaches it.
class SharingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SharingReminders.run(context)
    }
}

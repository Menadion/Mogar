package io.github.menadion.magus

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.app.NotificationCompat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

// Sharing reminders (M, 2026-11-27): at a time he sets, on days he sets, a notification asks
// "Turn on location sharing?" or "Turn it off?", and his tap does it. Per phone, never synced.
// Only his tap changes the state, so a reminder that never arrives leaves things as they are.
//
// Two halves. The pure half (nextDue, missed, encode, decode) takes values in and gives values
// out, with no clock and no Android, so it is tested on the desktop. The Android half (run, arm,
// post, saveRules) is the alarm, the saved rules and the notification, built on top of it.

enum class ReminderAction { ON, OFF }

// One rule is one time, a set of days and one action, so "on" and "off" are separate rules (M's
// pick, 2026-11-27). Days are java.time's, Monday to Sunday: Calendar counts Sunday as 1, which
// would bite when the day chips are wired up.
data class ReminderRule(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val action: ReminderAction,
) {
    init {
        require(hour in 0..23 && minute in 0..59) { "not a time of day: $hour:$minute" }
    }
}

// The rules that fall due at one moment. A list, because two rules can share a minute, and the
// moment is kept so a re-arm can walk forward from it rather than from the clock.
data class DueReminders(val atMs: Long, val rules: List<ReminderRule>)

object SharingReminders {
    // How many calendar days ahead to look. A rule set only for today's weekday whose time has
    // already passed is next due a week from today, which is the eighth date counting today: a
    // scan of today plus six would find nothing for it.
    private const val LOOK_AHEAD_DAYS = 7L

    // The next moment strictly after afterMs at which any rule is due, with every rule due then.
    // Null when no rule can ever fire (none saved, or each has no days).
    //
    // STRICTLY after, so that arming for the moment a rule fires does not find the same moment
    // again. And callers re-arm from the moment they were armed for, not from the clock: inexact
    // alarms can land minutes late, and "next after now" at 10:07 would skip the 10:00 rule.
    //
    // Built in wall-clock time, a date plus a time of day in the phone's zone, never by adding
    // 24 hours of milliseconds, which drifts across a daylight-saving change. Neither family zone
    // has one, so this is a guard. A time that does not exist on a spring-forward day (02:30)
    // moves to the same length later (03:30), so the rule still fires that day.
    fun nextDue(
        rules: List<ReminderRule>,
        afterMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DueReminders? {
        val today = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate()
        val candidates = ArrayList<Pair<Long, ReminderRule>>()
        for (offset in 0..LOOK_AHEAD_DAYS) {
            val date = today.plusDays(offset)
            for (rule in rules) {
                if (date.dayOfWeek !in rule.days) continue
                val at = ZonedDateTime.of(date, LocalTime.of(rule.hour, rule.minute), zone).toInstant().toEpochMilli()
                if (at > afterMs) candidates += at to rule
            }
        }
        val first = candidates.minOfOrNull { it.first } ?: return null
        return DueReminders(first, candidates.filter { it.first == first }.map { it.second }.sortedBy { it.id })
    }

    // How late a missed moment can still be acted on. Past this it is stale and dropped: a phone
    // that was off all weekend should not come back to a pile of old questions. Twelve hours keeps
    // "10:00 turn on" useful at 14:00 and useless the next morning.
    const val CATCH_UP_MS = 12L * 60 * 60 * 1000

    // Every moment in (lastRunMs, nowMs] at which a rule fell due, oldest first, none older than
    // CATCH_UP_MS. This is what makes a late alarm safe: the alarm armed for 10:00 may land at
    // 10:07, or the phone may only wake at 10:40, and the moments passed over are still found,
    // because the walk starts from the last time this ran and not from the clock. A last run in
    // the future (the clock was set back) is treated as now.
    fun missed(
        rules: List<ReminderRule>,
        lastRunMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DueReminders> {
        var cursor = maxOf(minOf(lastRunMs, nowMs), nowMs - CATCH_UP_MS)
        val out = ArrayList<DueReminders>()
        while (true) {
            val due = nextDue(rules, cursor, zone) ?: break
            if (due.atMs > nowMs) break
            out += due
            cursor = due.atMs
        }
        return out
    }

    // The saved form: one line of text in the same preferences file as every other setting, one
    // "id:hour:minute:days:ACTION" per rule joined by ";", days as ISO numbers (1 Monday to 7
    // Sunday) joined by ",". Hand-rolled so it can be tested on the desktop, where Android's JSON
    // classes are stubs. decode drops an entry it cannot read rather than losing the others.
    fun encode(rules: List<ReminderRule>): String = rules.joinToString(";") { r ->
        "${r.id}:${r.hour}:${r.minute}:${r.days.map { it.value }.sorted().joinToString(",")}:${r.action.name}"
    }

    fun decode(text: String): List<ReminderRule> = text.split(";").mapNotNull { part ->
        runCatching {
            val f = part.split(":")
            if (f.size != 5) return@runCatching null
            ReminderRule(
                id = f[0].toInt(),
                hour = f[1].toInt(),
                minute = f[2].toInt(),
                days = f[3].split(",").filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
                action = ReminderAction.valueOf(f[4]),
            )
        }.getOrNull()
    }

    // ---- The Android half ----

    private const val KEY_RULES = "reminderRules"
    private const val KEY_LAST_RUN = "reminderLastRun"
    private const val CHANNEL_ID = "sharing-reminders"

    // One notification slot per action, so a second "turn on" replaces the first instead of
    // stacking. 1 to 4 are taken by the sharer, the updater and the stopped notice.
    private const val ID_ON = 10
    private const val ID_OFF = 11
    private const val ALARM_REQUEST = 20

    // Put on the notification's intent: "on" or "off". Read by MainActivity when it is tapped.
    const val EXTRA_ACTION = "sharingReminder"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun rules(context: Context): List<ReminderRule> = decode(prefs(context).getString(KEY_RULES, "") ?: "")

    // Saving counts as having run: an edit never fires what it just made overdue. Adding a 10:00
    // rule at 10:30 does not post a reminder at once, it waits for the next 10:00.
    fun saveRules(context: Context, rules: List<ReminderRule>, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putString(KEY_RULES, encode(rules)).putLong(KEY_LAST_RUN, nowMs).apply()
        arm(context, rules, nowMs)
    }

    // Does what is due and sets the next alarm. Called by the alarm itself, and every time Mogar is
    // opened, which also re-arms an alarm that Android dropped (a force stop clears them). With
    // no last run on record there is nothing to catch up on, so it starts from now.
    fun run(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val rules = rules(context)
        val last = prefs(context).getLong(KEY_LAST_RUN, nowMs)
        for (due in missed(rules, last, nowMs)) for (rule in due.rules) post(context, rule, due.atMs)
        prefs(context).edit().putLong(KEY_LAST_RUN, nowMs).apply()
        arm(context, rules, nowMs)
    }

    // One alarm, for the soonest moment. Inexact on purpose (M's pick, 2026-11-27): it can land a
    // few minutes late, which a "turn it on?" question does not mind, and it needs no permission.
    // The exact kind needs "Alarms & reminders" switched on per phone from Android 14.
    private fun arm(context: Context, rules: List<ReminderRule>, afterMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            Intent(context, SharingReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val due = nextDue(rules, afterMs)
        if (due == null) alarms.cancel(pending) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due.atMs, pending)
    }

    // A question, never an action: only his tap changes sharing. Skipped when sharing is already
    // in the state the rule asks for (M's pick), and a newer instruction cancels the other action's
    // notification still sitting in the tray, so "turn on" and "turn off" never wait side by side.
    private fun post(context: Context, rule: ReminderRule, atMs: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val on = rule.action == ReminderAction.ON
        manager.cancel(if (on) ID_OFF else ID_ON)
        val mine = if (on) ID_ON else ID_OFF
        if (Family.savedCode(context) == null || Family.isSharing(context) == on) {
            manager.cancel(mine)
            return
        }
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_sharing_reminders), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(atMs))
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_ACTION, if (on) "on" else "off")
        manager.notify(
            mine,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(context.getString(if (on) R.string.reminder_on_title else R.string.reminder_off_title, time))
                .setContentText(context.getString(if (on) R.string.reminder_on_body else R.string.reminder_off_body))
                .setContentIntent(PendingIntent.getActivity(context, mine, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true)
                .build(),
        )
    }

    // Debug builds only, for testing without the settings screen: `am start ... --es
    // reminderRules "1:10:0:1,2,3,4,5,6,7:ON"` saves that rule, an empty string clears them.
    // Ignored outright in a release build, the same guard as KeepRunning.overrideBrand.
    fun debugSeed(context: Context, spec: String?) {
        if (spec == null) return
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        saveRules(context, decode(spec))
    }
}

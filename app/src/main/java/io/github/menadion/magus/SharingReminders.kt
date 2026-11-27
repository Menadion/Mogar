package io.github.menadion.magus

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// Sharing reminders (M, 2026-11-27): at a time he sets, on days he sets, a notification asks
// "Turn on location sharing?" or "Turn it off?", and his tap does it. Per phone, never synced.
// Only his tap changes the state, so a reminder that never arrives leaves things as they are.
//
// This file is the pure half so far: which rule is due next. No clock, no preferences, no Android.
// The alarm, the saved rules and the notification are built on top of it.

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
}

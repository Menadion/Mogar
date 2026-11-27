package io.github.menadion.magus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDateTime
import java.time.ZoneId

// Which sharing reminder is due next (SharingReminders.kt, M 2026-11-27). The pure half: a list of
// rules and a moment in, the next due moment and the rules due then out. Dates used: 2026-11-27 is
// a Friday, 2026-11-29 a Sunday. Manila, because that is where his phone is; it has no daylight
// saving, so one case runs in New York to guard the wall-clock arithmetic.
class SharingRemindersTest {
    private val manila = ZoneId.of("Asia/Manila")

    private fun ms(local: String, zone: ZoneId = manila) =
        LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun rule(
        id: Int,
        time: String,
        days: Set<DayOfWeek>,
        action: ReminderAction = ReminderAction.ON,
    ): ReminderRule {
        val (h, m) = time.split(":").map { it.toInt() }
        return ReminderRule(id, h, m, days, action)
    }

    private val every = DayOfWeek.values().toSet()

    @Test fun aRuleLaterTodayIsDueToday() {
        val due = SharingReminders.nextDue(listOf(rule(1, "10:00", setOf(FRIDAY))), ms("2026-11-27T09:00:00"), manila)!!
        assertEquals(ms("2026-11-27T10:00:00"), due.atMs)
    }

    @Test fun aRuleWhoseTimeHasPassedWaitsForTheNextMatchingDay() {
        // Friday 11:00 is past 10:00, so Tuesday-and-Friday rules next fire on Tuesday 2026-12-01.
        val due = SharingReminders.nextDue(listOf(rule(1, "10:00", setOf(TUESDAY, FRIDAY))), ms("2026-11-27T11:00:00"), manila)!!
        assertEquals(ms("2026-12-01T10:00:00"), due.atMs)
    }

    @Test fun aRuleOnlyOnTodaysWeekdayWhoseTimeHasPassedIsDueInAWeek() {
        // The eighth date: a scan of today plus six days would return null here.
        val due = SharingReminders.nextDue(listOf(rule(1, "10:00", setOf(FRIDAY))), ms("2026-11-27T11:00:00"), manila)!!
        assertEquals(ms("2026-12-04T10:00:00"), due.atMs)
    }

    @Test fun theMomentItselfIsNotDueAgain() {
        // Strictly after: re-arming at the moment a rule fires must not find the same moment.
        val due = SharingReminders.nextDue(listOf(rule(1, "10:00", setOf(FRIDAY))), ms("2026-11-27T10:00:00"), manila)!!
        assertEquals(ms("2026-12-04T10:00:00"), due.atMs)
    }

    @Test fun oneMillisecondBeforeIsStillDueAtTheMoment() {
        val due = SharingReminders.nextDue(listOf(rule(1, "10:00", setOf(FRIDAY))), ms("2026-11-27T10:00:00") - 1, manila)!!
        assertEquals(ms("2026-11-27T10:00:00"), due.atMs)
    }

    @Test fun aLateAlarmDoesNotSkipItsRuleWhenReArmedFromTheArmedTime() {
        // The alarm armed for 10:00 may land at 10:07. The armed moment is what says which rules
        // fired (the first call's answer, kept), and re-arming walks on from that moment, not from
        // the clock, so nothing is dropped or fired twice.
        val rules = listOf(rule(1, "10:00", setOf(FRIDAY)), rule(2, "19:00", setOf(FRIDAY), ReminderAction.OFF))
        val first = SharingReminders.nextDue(rules, ms("2026-11-27T09:00:00"), manila)!!
        assertEquals(listOf(1), first.rules.map { it.id })
        val second = SharingReminders.nextDue(rules, first.atMs, manila)!!
        assertEquals(ms("2026-11-27T19:00:00"), second.atMs)
        assertEquals(listOf(2), second.rules.map { it.id })
    }

    @Test fun sundayNightRollsToMonday() {
        val due = SharingReminders.nextDue(listOf(rule(1, "07:00", setOf(MONDAY))), ms("2026-11-29T22:00:00"), manila)!!
        assertEquals(ms("2026-11-30T07:00:00"), due.atMs)
    }

    @Test fun twoRulesInTheSameMinuteAreBothReturnedInIdOrder() {
        val rules = listOf(rule(7, "10:00", setOf(FRIDAY), ReminderAction.OFF), rule(3, "10:00", setOf(FRIDAY)))
        val due = SharingReminders.nextDue(rules, ms("2026-11-27T09:00:00"), manila)!!
        assertEquals(listOf(3, 7), due.rules.map { it.id })
    }

    @Test fun onlyTheEarliestMomentsRulesAreReturned() {
        val rules = listOf(rule(1, "19:00", setOf(FRIDAY), ReminderAction.OFF), rule(2, "10:00", setOf(FRIDAY)))
        val due = SharingReminders.nextDue(rules, ms("2026-11-27T09:00:00"), manila)!!
        assertEquals(listOf(2), due.rules.map { it.id })
    }

    @Test fun noRulesOrOnlyRulesWithNoDaysIsNull() {
        assertNull(SharingReminders.nextDue(emptyList(), ms("2026-11-27T09:00:00"), manila))
        assertNull(SharingReminders.nextDue(listOf(rule(1, "10:00", emptySet())), ms("2026-11-27T09:00:00"), manila))
    }

    @Test fun aRuleWithNoDaysDoesNotHideOneThatHas() {
        val rules = listOf(rule(1, "09:30", emptySet()), rule(2, "10:00", setOf(FRIDAY)))
        val due = SharingReminders.nextDue(rules, ms("2026-11-27T09:00:00"), manila)!!
        assertEquals(listOf(2), due.rules.map { it.id })
    }

    @Test fun aTimeThatDoesNotExistOnSpringForwardDayStillFiresThatDay() {
        // New York, 2026-03-08: clocks go 02:00 to 03:00, so 02:30 never happens and the rule fires
        // at 03:30 instead, once, on the day. Not skipped, not twice.
        val ny = ZoneId.of("America/New_York")
        val due = SharingReminders.nextDue(listOf(rule(1, "02:30", every)), ms("2026-03-07T12:00:00", ny), ny)!!
        assertEquals(ms("2026-03-08T03:30:00", ny), due.atMs)
        val next = SharingReminders.nextDue(listOf(rule(1, "02:30", every)), due.atMs, ny)!!
        assertEquals(ms("2026-03-09T02:30:00", ny), next.atMs)
    }

    // ---- missed: what a late wake-up has to catch up on ----

    @Test fun missedFindsEveryMomentPassedOverOldestFirst() {
        // Last ran at 09:00, woke at 10:07: both the 10:00 and the 10:05 rule fell due in between.
        val rules = listOf(rule(2, "10:05", setOf(FRIDAY), ReminderAction.OFF), rule(1, "10:00", setOf(FRIDAY)))
        val m = SharingReminders.missed(rules, ms("2026-11-27T09:00:00"), ms("2026-11-27T10:07:00"), manila)
        assertEquals(listOf(ms("2026-11-27T10:00:00"), ms("2026-11-27T10:05:00")), m.map { it.atMs })
        assertEquals(listOf(listOf(1), listOf(2)), m.map { d -> d.rules.map { it.id } })
    }

    @Test fun missedLeavesOutTheLastRunMomentAndAnythingAfterNow() {
        val rules = listOf(rule(1, "10:00", setOf(FRIDAY)))
        // Already handled at 10:00 on the last run: not handled twice.
        assertEquals(0, SharingReminders.missed(rules, ms("2026-11-27T10:00:00"), ms("2026-11-27T10:30:00"), manila).size)
        // Not due yet at 09:59.
        assertEquals(0, SharingReminders.missed(rules, ms("2026-11-27T09:00:00"), ms("2026-11-27T09:59:00"), manila).size)
    }

    @Test fun missedIncludesAMomentExactlyNow() {
        val rules = listOf(rule(1, "10:00", setOf(FRIDAY)))
        assertEquals(1, SharingReminders.missed(rules, ms("2026-11-27T09:00:00"), ms("2026-11-27T10:00:00"), manila).size)
    }

    @Test fun missedDropsMomentsOlderThanTheCatchUpWindow() {
        // A daily 10:00 rule, last run a week ago, woken Friday 11:00: only that morning's 10:00 is
        // within twelve hours; the six before it are stale and dropped.
        val m = SharingReminders.missed(listOf(rule(1, "10:00", every)), ms("2026-11-20T09:00:00"), ms("2026-11-27T11:00:00"), manila)
        assertEquals(listOf(ms("2026-11-27T10:00:00")), m.map { it.atMs })
    }

    @Test fun missedWithNoRulesIsEmpty() {
        assertEquals(0, SharingReminders.missed(emptyList(), ms("2026-11-20T09:00:00"), ms("2026-11-27T11:00:00"), manila).size)
    }

    // ---- the saved form ----

    @Test fun rulesAreSavedAsOneLineOfText() {
        val rules = listOf(rule(1, "10:00", setOf(FRIDAY, TUESDAY)), rule(2, "19:30", setOf(MONDAY), ReminderAction.OFF))
        assertEquals("1:10:0:2,5:ON;2:19:30:1:OFF", SharingReminders.encode(rules))
    }

    @Test fun savedRulesComeBackAsTheyWentIn() {
        val rules = listOf(rule(1, "10:00", setOf(FRIDAY, TUESDAY)), rule(2, "07:05", every, ReminderAction.OFF), rule(3, "00:00", emptySet()))
        assertEquals(rules, SharingReminders.decode(SharingReminders.encode(rules)))
    }

    @Test fun nothingSavedIsNoRules() {
        assertEquals(emptyList<ReminderRule>(), SharingReminders.decode(""))
        assertEquals("", SharingReminders.encode(emptyList()))
    }

    @Test fun anUnreadableEntryIsDroppedAndTheRestKept() {
        // Entry 2 is not a rule, 4's hour is 25, 5's day is 9. Only 1 and 3 survive.
        val saved = "1:10:0:5:ON;garbage;3:7:30:1,2:OFF;4:25:0:5:ON;5:9:0:9:ON"
        assertEquals(listOf(1, 3), SharingReminders.decode(saved).map { it.id })
    }

    @Test fun aNewRuleGetsAnIdNoSavedRuleUses() {
        assertEquals(1, SharingReminders.nextId(emptyList()))
        // Ids 1 and 5 are taken, so the next is 6, not the hole at 2: a deleted rule's id is not
        // handed to a new one while a higher one is still alive.
        assertEquals(6, SharingReminders.nextId(listOf(rule(1, "10:00", every), rule(5, "11:00", every))))
    }

    @Test fun aNonexistentTimeOfDayIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReminderRule(1, 24, 0, every, ReminderAction.ON) }
        assertThrows(IllegalArgumentException::class.java) { ReminderRule(1, 10, 60, every, ReminderAction.ON) }
    }
}

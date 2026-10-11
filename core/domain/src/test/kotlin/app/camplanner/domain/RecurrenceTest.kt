package app.camplanner.domain

import app.camplanner.model.AppSettings
import app.camplanner.model.EventSeries
import app.camplanner.model.Frequency
import app.camplanner.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalTime

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val far = d("2027-12-31")

    @Test
    fun `daily and every other day`() {
        assertEquals(listOf("2026-10-20", "2026-10-21", "2026-10-22").map(::d),
            Recurrence.dates(RepeatRule(Frequency.DAILY, count = 3), d("2026-10-20"), d("2026-10-01"), far))
        assertEquals(listOf("2026-10-20", "2026-10-22", "2026-10-24").map(::d),
            Recurrence.dates(RepeatRule(Frequency.DAILY, interval = 2, until = d("2026-10-25")), d("2026-10-20"), d("2026-10-01"), far))
    }

    @Test
    fun `weekly on chosen days, starting mid-week, until inclusive`() {
        val rule = RepeatRule(Frequency.WEEKLY, days = setOf(TUESDAY, THURSDAY), until = d("2026-11-03"))
        // Starts Wed 21 Oct: the first Tuesday that week is skipped.
        assertEquals(listOf("2026-10-22", "2026-10-27", "2026-10-29", "2026-11-03").map(::d),
            Recurrence.dates(rule, d("2026-10-21"), d("2026-10-01"), far))
    }

    @Test
    fun `fortnightly keeps to the starting week`() {
        val rule = RepeatRule(Frequency.WEEKLY, interval = 2, days = setOf(FRIDAY), count = 3)
        assertEquals(listOf("2026-10-23", "2026-11-06", "2026-11-20").map(::d),
            Recurrence.dates(rule, d("2026-10-23"), d("2026-10-01"), far))
    }

    @Test
    fun `count includes exceptions, and the window clips without changing the count`() {
        val rule = RepeatRule(Frequency.DAILY, count = 4)
        assertEquals(listOf("2026-10-20", "2026-10-22", "2026-10-23").map(::d),
            Recurrence.dates(rule, d("2026-10-20"), d("2026-10-01"), far, exceptions = setOf(d("2026-10-21"))))
        assertEquals(listOf("2026-10-22", "2026-10-23").map(::d),
            Recurrence.dates(rule, d("2026-10-20"), d("2026-10-22"), far))
    }

    @Test
    fun `open-ended rules stop at the window`() {
        val dates = Recurrence.dates(RepeatRule(Frequency.WEEKLY), d("2026-10-20"), d("2026-10-01"), d("2026-11-30"))
        assertEquals(d("2026-11-24"), dates.last())
        assertEquals(6, dates.size)
    }

    @Test
    fun `local time holds across the autumn clock change, overnight end`() {
        val series = EventSeries(
            id = 1, title = "Late lab", subjectId = null, startDate = d("2026-10-23"),
            startTime = LocalTime.of(19, 0), endTime = LocalTime.of(1, 0),
            rule = RepeatRule(Frequency.WEEKLY, count = 2), location = null, note = null, travelMode = null, reminderMinutes = null,
        )
        val occ = Recurrence.occurrences(series, d("2026-10-01"), far, LONDON)
        assertEquals(listOf(london("2026-10-23T19:00"), london("2026-10-30T19:00")), occ.map { it.start })
        assertEquals(london("2026-10-31T01:00"), occ[1].end)
        assertEquals("18:00Z then 19:00Z", "${occ[0].start.toString().substring(11, 16)}Z then ${occ[1].start.toString().substring(11, 16)}Z")
    }

    @Test
    fun `descriptions read naturally`() {
        assertEquals("Every week on Tue and Thu, until Fri 4 Dec",
            Recurrence.describe(RepeatRule(Frequency.WEEKLY, days = setOf(THURSDAY, TUESDAY), until = d("2026-12-04")), d("2026-10-20")))
        assertEquals("Every weekday", Recurrence.describe(RepeatRule(Frequency.WEEKLY, days = Recurrence.WEEKDAYS), d("2026-10-20")))
        assertEquals("Every other week on Fri, 3 times", Recurrence.describe(RepeatRule(Frequency.WEEKLY, interval = 2, count = 3), d("2026-10-23")))
    }

    // --- Planner honours per-event choices -----------------------------------------------------

    @Test
    fun `cancelled occurrences get no alarms and don't count as first or last`() {
        val cancelled = event(london("2026-10-20T08:00")).copy(cancelled = true)
        val real = event(london("2026-10-20T10:00"))
        val alarms = AlarmPlanner.planDay(d("2026-10-20"), listOf(cancelled, real), AppSettings(middayNudgeEnabled = false), LONDON, Places.locate)
        assertEquals(london("2026-10-20T08:30"), alarms.single { it.kind == AlarmKind.MORNING }.triggerAt)
        assertTrue(alarms.none { it.eventId == cancelled.id })
    }

    @Test
    fun `own reminder time wins, zero means none`() {
        val a = event(london("2026-10-20T10:00")).copy(reminderMinutes = 30)
        val b = event(london("2026-10-20T12:00")).copy(reminderMinutes = 0)
        val c = event(london("2026-10-20T14:00"))
        val reminders = AlarmPlanner.planDay(d("2026-10-20"), listOf(a, b, c), AppSettings(middayNudgeEnabled = false), LONDON, Places.locate)
            .filter { it.kind == AlarmKind.REMINDER }
        assertEquals(listOf(london("2026-10-20T09:30"), london("2026-10-20T13:45")), reminders.map { it.triggerAt })
    }

    @Test
    fun `a skipped morning has no alarm but keeps its reminders`() {
        val e = event(london("2026-10-20T09:00"))
        val alarms = AlarmPlanner.planDay(
            d("2026-10-20"), listOf(e), AppSettings(skippedMornings = setOf(d("2026-10-20")), middayNudgeEnabled = false), LONDON, Places.locate,
        )
        assertTrue(alarms.none { it.kind == AlarmKind.MORNING })
        assertTrue(alarms.any { it.kind == AlarmKind.REMINDER })
    }
}

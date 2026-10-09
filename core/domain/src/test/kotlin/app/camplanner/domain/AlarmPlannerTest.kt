package app.camplanner.domain

import app.camplanner.model.AppSettings
import app.camplanner.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class AlarmPlannerTest {

    private val settings = AppSettings(
        home = Places.HOME,
        homeAddress = "Trinity Street",
        middayNudgeEnabled = false,
    )

    private fun planDay(date: String, vararg events: app.camplanner.model.CalendarEvent, s: AppSettings = settings) =
        AlarmPlanner.planDay(LocalDate.parse(date), events.toList(), s, LONDON, Places.locate)

    private fun List<PlannedAlarm>.of(kind: AlarmKind) = filter { it.kind == kind }

    // --- Ordinary day ---------------------------------------------------------------------------

    @Test
    fun `morning alarm is lead minutes before the first event`() {
        val first = event(london("2026-10-20T09:00"))
        val second = event(london("2026-10-20T11:00"))
        val alarms = planDay("2026-10-20", second, first)
        val morning = alarms.of(AlarmKind.MORNING).single()
        assertEquals(london("2026-10-20T07:30"), morning.triggerAt)
        assertEquals(first.id, morning.eventId)
    }

    @Test
    fun `no morning alarm on a day without events, check-in falls back to 19_00`() {
        val alarms = planDay("2026-10-24")
        assertTrue(alarms.of(AlarmKind.MORNING).isEmpty())
        assertEquals(london("2026-10-24T19:00"), alarms.of(AlarmKind.CHECK_IN).single().triggerAt)
    }

    @Test
    fun `all-day events do not trigger a morning alarm`() {
        val alarms = planDay("2026-10-20", event(london("2026-10-20T00:00"), minutes = 1440, allDay = true))
        assertTrue(alarms.of(AlarmKind.MORNING).isEmpty())
    }

    @Test
    fun `check-in is delay after the last event ends, not the last to start`() {
        val long = event(london("2026-10-20T14:00"), minutes = 240) // ends 18:00
        val late = event(london("2026-10-20T16:00"), minutes = 60) // ends 17:00
        val alarms = planDay("2026-10-20", long, late)
        assertEquals(london("2026-10-20T18:30"), alarms.of(AlarmKind.CHECK_IN).single().triggerAt)
    }

    @Test
    fun `reminder per event`() {
        val a = event(london("2026-10-20T09:00"))
        val b = event(london("2026-10-20T14:00"))
        val reminders = planDay("2026-10-20", a, b).of(AlarmKind.REMINDER)
        assertEquals(listOf(london("2026-10-20T08:45"), london("2026-10-20T13:45")), reminders.map { it.triggerAt })
    }

    // --- Time to leave ----------------------------------------------------------------------------

    @Test
    fun `first event leaves from home with travel and buffer`() {
        val e = event(london("2026-10-20T10:00"), location = "Cavendish Laboratory")
        val travel = TravelEstimator.travelTime(Places.HOME, Places.CAVENDISH, TravelMode.WALK)
        val leave = planDay("2026-10-20", e).of(AlarmKind.LEAVE).single()
        assertEquals(london("2026-10-20T10:00") - travel - Duration.ofMinutes(5), leave.triggerAt)
    }

    @Test
    fun `second event leaves from the first event's location, honouring bike override`() {
        val a = event(london("2026-10-20T09:00"), location = "Mill Lane Lecture Rooms")
        val b = event(london("2026-10-20T11:00"), location = "Cavendish Laboratory", mode = TravelMode.BIKE)
        val legs = DayPlanner.legs(
            DayPlanner.timedEventsOn(listOf(a, b), LocalDate.parse("2026-10-20"), LONDON),
            Places.HOME, "", TravelMode.WALK, Duration.ofMinutes(5), Places.locate,
        )
        assertEquals(Place.Coordinates(Places.MILL_LANE), legs[1].origin)
        assertEquals(TravelMode.BIKE, legs[1].mode)
        val bike = TravelEstimator.travelTime(Places.MILL_LANE, Places.CAVENDISH, TravelMode.BIKE)
        assertEquals(london("2026-10-20T11:00") - bike - Duration.ofMinutes(5), legs[1].leaveAt)
    }

    @Test
    fun `back-to-back events in the same place get no leave alert`() {
        val a = event(london("2026-10-20T09:00"), location = "Mill Lane Lecture Rooms")
        val b = event(london("2026-10-20T10:00"), location = "  mill lane lecture rooms ")
        val leaves = planDay("2026-10-20", a, b).of(AlarmKind.LEAVE)
        assertEquals(listOf(a.id), leaves.map { it.eventId })
    }

    @Test
    fun `event with an ungeocoded location gets a reminder but no leave alert`() {
        val e = event(london("2026-10-20T09:00"), location = "Room 3, Somewhere Unknown")
        val alarms = planDay("2026-10-20", e)
        assertTrue(alarms.of(AlarmKind.LEAVE).isEmpty())
        assertEquals(1, alarms.of(AlarmKind.REMINDER).size)
    }

    @Test
    fun `origin skips an earlier event without a location`() {
        val a = event(london("2026-10-20T09:00"), location = "Sidgwick Site")
        val online = event(london("2026-10-20T10:00"), location = null)
        val c = event(london("2026-10-20T12:00"), location = "Mill Lane Lecture Rooms")
        val legs = DayPlanner.legs(
            DayPlanner.timedEventsOn(listOf(a, online, c), LocalDate.parse("2026-10-20"), LONDON),
            Places.HOME, "", TravelMode.WALK, Duration.ofMinutes(5), Places.locate,
        )
        assertNull(legs[1].leaveAt)
        assertEquals(Place.Coordinates(Places.SIDGWICK), legs[2].origin)
    }

    @Test
    fun `without a home location the first event has no leave alert`() {
        val e = event(london("2026-10-20T09:00"))
        val alarms = planDay("2026-10-20", e, s = settings.copy(home = null, homeAddress = ""))
        assertTrue(alarms.of(AlarmKind.LEAVE).isEmpty())
    }

    // --- Settings switches ------------------------------------------------------------------------

    @Test
    fun `disabled alarms are not planned`() {
        val s = settings.copy(
            morningAlarmEnabled = false, leaveAlertEnabled = false, reminderEnabled = false, checkInEnabled = false,
        )
        assertTrue(planDay("2026-10-20", event(london("2026-10-20T09:00")), s = s).isEmpty())
    }

    @Test
    fun `midday nudge is skipped on rest days`() {
        val s = settings.copy(middayNudgeEnabled = true, restDays = setOf(DayOfWeek.SUNDAY))
        assertTrue(planDay("2026-10-25", s = s).of(AlarmKind.MIDDAY_NUDGE).isEmpty()) // Sunday
        assertEquals(london("2026-10-26T13:00"), planDay("2026-10-26", s = s).of(AlarmKind.MIDDAY_NUDGE).single().triggerAt)
    }

    // --- Horizon --------------------------------------------------------------------------------

    @Test
    fun `plan drops past alarms and keeps those inside the horizon`() {
        val e = event(london("2026-10-20T09:00"))
        val now = london("2026-10-20T08:00") // after the 07:30 alarm, before the 08:45 reminder
        val plan = AlarmPlanner.plan(listOf(e), settings, LONDON, now, Duration.ofHours(72), Places.locate)
        assertTrue(plan.none { it.kind == AlarmKind.MORNING })
        assertTrue(plan.any { it.kind == AlarmKind.REMINDER })
        assertTrue(plan.all { it.triggerAt.isAfter(now) && !it.triggerAt.isAfter(now + Duration.ofHours(72)) })
        assertEquals(plan.map { it.key }.distinct(), plan.map { it.key })
    }

    @Test
    fun `late check-in from yesterday that lands after midnight is still planned`() {
        val late = event(london("2026-10-20T22:30"), minutes = 90) // ends 00:00, check-in 00:30 next day
        val now = london("2026-10-21T00:10")
        val plan = AlarmPlanner.plan(listOf(late), settings, LONDON, now, Duration.ofHours(72), Places.locate)
        assertEquals(london("2026-10-21T00:30"), plan.single { it.key == "CHECK_IN:2026-10-20" }.triggerAt)
    }

    // --- Europe/London daylight-saving transitions ------------------------------------------------

    @Test
    fun `autumn change - morning alarm on the Monday after clocks go back`() {
        // Sunday 25 Oct 2026: 02:00 BST -> 01:00 GMT. Monday is GMT (= UTC).
        val alarms = planDay("2026-10-26", event(london("2026-10-26T09:00")))
        assertEquals(utc("2026-10-26T07:30:00Z"), alarms.of(AlarmKind.MORNING).single().triggerAt)
    }

    @Test
    fun `autumn change - lead time is real elapsed minutes across the repeated hour`() {
        // Event at 02:30 GMT on the change day; 90 real minutes earlier is 01:00Z, i.e. the second 01:00.
        val alarms = planDay("2026-10-25", event(utc("2026-10-25T02:30:00Z"), location = null))
        assertEquals(utc("2026-10-25T01:00:00Z"), alarms.of(AlarmKind.MORNING).single().triggerAt)
    }

    @Test
    fun `autumn change - fallback check-in is 19_00 local on the change day`() {
        assertEquals(utc("2026-10-25T19:00:00Z"), planDay("2026-10-25").of(AlarmKind.CHECK_IN).single().triggerAt)
        assertEquals(utc("2026-10-24T18:00:00Z"), planDay("2026-10-24").of(AlarmKind.CHECK_IN).single().triggerAt)
    }

    @Test
    fun `spring change - morning alarm before an 08_00 BST event`() {
        // Sunday 28 Mar 2027: 01:00 GMT -> 02:00 BST.
        val alarms = planDay("2027-03-28", event(london("2027-03-28T08:00")))
        assertEquals(utc("2027-03-28T05:30:00Z"), alarms.of(AlarmKind.MORNING).single().triggerAt) // 06:30 BST
    }

    @Test
    fun `spring change - lead time spans the skipped hour in real minutes`() {
        // 03:00 BST is 02:00Z; 90 minutes earlier is 00:30Z, which is 00:30 GMT on the clock.
        val alarms = planDay("2027-03-28", event(london("2027-03-28T03:00"), location = null))
        assertEquals(utc("2027-03-28T00:30:00Z"), alarms.of(AlarmKind.MORNING).single().triggerAt)
    }

    @Test
    fun `spring change - fixed clock time inside the gap moves forward`() {
        val s = settings.copy(middayNudgeEnabled = true, middayNudgeTime = LocalTime.of(1, 30))
        val nudge = planDay("2027-03-28", s = s).of(AlarmKind.MIDDAY_NUDGE).single()
        assertEquals(utc("2027-03-28T01:30:00Z"), nudge.triggerAt) // 02:30 BST
        assertEquals(utc("2027-03-28T18:00:00Z"), planDay("2027-03-28").of(AlarmKind.CHECK_IN).single().triggerAt)
    }

    @Test
    fun `weekly lecture keeps its local time across the change and alarms follow`() {
        val before = event(london("2026-10-19T09:00")) // BST, 08:00Z
        val after = event(london("2026-10-26T09:00")) // GMT, 09:00Z
        assertEquals(utc("2026-10-19T06:30:00Z"), planDay("2026-10-19", before).of(AlarmKind.MORNING).single().triggerAt)
        assertEquals(utc("2026-10-26T07:30:00Z"), planDay("2026-10-26", after).of(AlarmKind.MORNING).single().triggerAt)
    }

    @Test
    fun `event belongs to the local day it starts on`() {
        // A late-evening event belongs to its own day, not the next morning.
        val e = event(london("2026-10-31T23:30"))
        assertEquals(1, planDay("2026-10-31", e).of(AlarmKind.MORNING).size)
        assertTrue(planDay("2026-11-01", e).of(AlarmKind.MORNING).isEmpty())
    }
}

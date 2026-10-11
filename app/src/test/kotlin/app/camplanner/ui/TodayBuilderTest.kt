package app.camplanner.ui

import app.camplanner.model.AppSettings
import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSource
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.GeoPoint
import app.camplanner.ui.common.When
import app.camplanner.ui.today.SetupStep
import app.camplanner.ui.today.TodayBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TodayBuilderTest {
    private val zone = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 10, 13) // a Tuesday in Michaelmas
    private val home = GeoPoint(52.2070, 0.1170)

    private fun event(id: Long, start: String, end: String, title: String, place: String? = null, date: LocalDate = day, cancelled: Boolean = false) =
        CalendarEvent(
            id = id, key = "k$id", source = EventSource.IMPORTED, title = title, subjectId = null,
            start = date.atTime(LocalTime.parse(start)).atZone(zone).toInstant(),
            end = date.atTime(LocalTime.parse(end)).atZone(zone).toInstant(),
            location = place, cancelled = cancelled,
        )

    private val pushUps = Exercise(1, "pushups", "Push-ups", ExerciseUnit.REPS, ExerciseGroup.UPPER, true, 100, 0)

    private fun build(
        now: String,
        events: List<CalendarEvent>,
        settings: AppSettings = AppSettings(home = home, termStart = LocalDate.of(2026, 10, 8)),
        assignments: List<Assignment> = emptyList(),
        sets: List<ExerciseSet> = emptyList(),
    ) = TodayBuilder.build(
        now = day.atTime(LocalTime.parse(now)), zone = zone, events = events, subjects = emptyList(), settings = settings,
        locate = { if (it == "Cavendish") GeoPoint(52.2094, 0.0919) else null }, assignments = assignments,
        exercises = listOf(pushUps), sets = sets, reviewDone = false, hasTimetable = true, permissionsOk = true, attention = null,
    )

    @Test
    fun heroShowsWhatIsOnNowAndWhatIsNext() {
        val s = build("09:30", listOf(event(1, "09:00", "10:00", "Analysis"), event(2, "11:00", "12:00", "Physics", "Cavendish")))
        assertEquals("Analysis", s.hero.now?.title)
        assertEquals(0.5f, s.hero.nowProgress, 0.01f)
        assertEquals("Physics", s.hero.next?.title)
        assertEquals("in 1 h 30 min", s.hero.nextWhen)
        assertEquals(listOf(When.NOW, When.LATER), s.schedule.map { it.state })
        // Travel from home to the Cavendish is worked out, so there is a leave time.
        assertTrue(s.hero.next?.leaveAt != null)
    }

    @Test
    fun cancelledLecturesStayListedButAreNeverNext() {
        val s = build("08:00", listOf(event(1, "09:00", "10:00", "Analysis", cancelled = true), event(2, "11:00", "12:00", "Physics")))
        assertEquals(2, s.schedule.size)
        assertTrue(s.schedule.first().cancelled)
        assertEquals("Physics", s.hero.next?.title)
    }

    @Test
    fun afterTheLastEventTheHeroLooksAtTomorrow() {
        val tomorrow = day.plusDays(1)
        val s = build("18:00", listOf(event(1, "09:00", "10:00", "Analysis"), event(2, "10:00", "11:00", "Groups", date = tomorrow)))
        assertNull(s.hero.now)
        assertNull(s.hero.next)
        assertEquals("Groups", s.hero.tomorrowFirst?.title)
        // Tomorrow starts at 10:00, so the alarm is 90 minutes earlier.
        assertEquals(LocalTime.of(8, 30), s.alarmTime)
        assertFalse(s.alarmSkipped)
    }

    @Test
    fun aSkippedMorningIsReported() {
        val tomorrow = day.plusDays(1)
        val s = build(
            "18:00", listOf(event(2, "10:00", "11:00", "Groups", date = tomorrow)),
            settings = AppSettings(skippedMornings = setOf(tomorrow)),
        )
        assertTrue(s.alarmSkipped)
    }

    @Test
    fun dueSoonFlagsOverdueWorkAndHidesWorkFarAhead() {
        fun due(id: Long, at: LocalDateTime, done: Boolean = false) =
            Assignment(id, "Sheet $id", null, "", at.atZone(zone).toInstant(), done)
        val s = build(
            "12:00", emptyList(),
            assignments = listOf(due(1, day.minusDays(1).atTime(9, 0)), due(2, day.plusDays(2).atTime(9, 0)), due(3, day.plusDays(10).atTime(9, 0))),
        )
        assertEquals(listOf(1L, 2L), s.dueSoon.map { it.id })
        assertTrue(s.dueSoon[0].overdue)
        assertTrue(s.dueSoon[0].detail.startsWith("Overdue"))
        assertFalse(s.dueSoon[1].overdue)
    }

    @Test
    fun setupListsOnlyWhatIsMissing() {
        val s = build("12:00", emptyList(), settings = AppSettings())
        assertEquals(listOf(SetupStep.HOME, SetupStep.TERM), s.setup)
        assertEquals("Michaelmas · Week 1", TodayBuilder.termLine(day, AppSettings(termStart = LocalDate.of(2026, 10, 8))))
    }

    @Test
    fun pushUpsCountTodayOnly() {
        val now = day.atTime(8, 0).atZone(zone).toInstant()
        val s = build(
            "12:00", emptyList(),
            sets = listOf(ExerciseSet(1, 1, day, 30, now), ExerciseSet(2, 1, day, -5, now), ExerciseSet(3, 1, day.minusDays(1), 50, now)),
        )
        assertEquals(25 to 100, s.pushUps)
    }
}

package app.camplanner.ui

import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSeries
import app.camplanner.model.EventSource
import app.camplanner.model.Frequency
import app.camplanner.model.RepeatRule
import app.camplanner.ui.calendar.EditorForm
import app.camplanner.ui.calendar.EndsChoice
import app.camplanner.ui.calendar.RepeatChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class EditorFormTest {
    private val zone = ZoneId.of("Europe/London")
    private val tue = LocalDate.of(2026, 10, 13)

    @Test
    fun newEventStartsAtTheNextHourToday() {
        val f = EditorForm.new(tue, LocalDateTime.of(tue, LocalTime.of(14, 20)))
        assertEquals(LocalTime.of(15, 0), f.start)
        assertEquals(LocalTime.of(16, 0), f.end)
        assertEquals(LocalTime.of(9, 0), EditorForm.new(tue.plusDays(1), LocalDateTime.of(tue, LocalTime.of(14, 20))).start)
    }

    @Test
    fun weeklyDefaultsToTheDatesWeekdayAndKeepsChosenDays() {
        val f = EditorForm.new(tue, LocalDateTime.of(tue, LocalTime.NOON)).copy(title = "Gym").withRepeat(RepeatChoice.WEEKLY)
        assertEquals(setOf(TUESDAY), f.days)
        val rule = f.copy(days = setOf(TUESDAY, THURSDAY)).rule()!!
        assertEquals(Frequency.WEEKLY, rule.frequency)
        assertEquals(setOf(TUESDAY, THURSDAY), rule.days)
        assertEquals("Every week on Tue and Thu", f.copy(days = setOf(TUESDAY, THURSDAY)).summary())
    }

    @Test
    fun endsMapToUntilOrCount() {
        val base = EditorForm.new(tue, LocalDateTime.of(tue, LocalTime.NOON)).copy(title = "Choir").withRepeat(RepeatChoice.FORTNIGHTLY)
        val until = base.copy(ends = EndsChoice.ON_DATE, until = tue.plusWeeks(8)).rule()!!
        assertEquals(2, until.interval)
        assertEquals(tue.plusWeeks(8), until.until)
        assertNull(until.count)
        val count = base.copy(ends = EndsChoice.AFTER, count = "6").rule()!!
        assertEquals(6, count.count)
        assertNull(count.until)
    }

    @Test
    fun problemsBlockSaving() {
        val f = EditorForm.new(tue, LocalDateTime.of(tue, LocalTime.NOON))
        assertEquals("Add a title", f.problem)
        val repeating = f.copy(title = "Gym").withRepeat(RepeatChoice.DAILY)
        assertNotNull(repeating.copy(ends = EndsChoice.ON_DATE, until = tue.minusDays(1)).problem)
        assertNotNull(repeating.copy(ends = EndsChoice.AFTER, count = "").problem)
        assertNull(repeating.copy(ends = EndsChoice.AFTER, count = "3").problem)
    }

    @Test
    fun movingTheStartKeepsTheLength() {
        val f = EditorForm.new(tue, LocalDateTime.of(tue, LocalTime.NOON)).copy(start = LocalTime.of(9, 0), end = LocalTime.of(10, 30))
        val moved = f.withStart(LocalTime.of(14, 0))
        assertEquals(LocalTime.of(15, 30), moved.end)
        assertFalse(moved.overnight)
        assertTrue(f.copy(end = LocalTime.of(8, 0)).overnight)
    }

    @Test
    fun editingAWholeSeriesLoadsItsRule() {
        val series = EventSeries(
            id = 7, title = "Rowing", subjectId = null, startDate = tue, startTime = LocalTime.of(6, 30), endTime = LocalTime.of(8, 0),
            rule = RepeatRule(Frequency.WEEKLY, days = setOf(TUESDAY, FRIDAY), count = 10), location = "Boathouse", note = null,
            travelMode = null, reminderMinutes = 30,
        )
        val occurrence = CalendarEvent(
            id = 99, key = "series-7@x", source = EventSource.MANUAL, title = "Rowing", subjectId = null,
            start = tue.plusDays(3).atTime(6, 30).atZone(zone).toInstant(), end = tue.plusDays(3).atTime(8, 0).atZone(zone).toInstant(),
            location = "Boathouse", reminderMinutes = 30, seriesId = 7,
        )
        val all = EditorForm.of(occurrence, series, wholeSeries = true, zone = zone)
        assertEquals(tue, all.date)
        assertEquals(RepeatChoice.WEEKLY, all.repeat)
        assertEquals(EndsChoice.AFTER, all.ends)
        assertEquals("10", all.count)
        assertEquals(series.rule, all.toDraft().repeat)

        val one = EditorForm.of(occurrence, series, wholeSeries = false, zone = zone)
        assertEquals(tue.plusDays(3), one.date)
        assertFalse(one.canRepeat)
        assertNull(one.toDraft().repeat)
    }

    @Test
    fun importedLecturesCannotRepeat() {
        val lecture = CalendarEvent(
            id = 1, key = "uid@1", source = EventSource.IMPORTED, title = "Analysis", subjectId = null,
            start = tue.atTime(9, 0).atZone(zone).toInstant(), end = tue.atTime(10, 0).atZone(zone).toInstant(),
        )
        val f = EditorForm.of(lecture, null, wholeSeries = false, zone = zone)
        assertTrue(f.imported)
        assertFalse(f.canRepeat)
    }
}

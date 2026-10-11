package app.camplanner.domain

import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSource
import app.camplanner.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CheckInTest {
    private val zone = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 10, 13)
    private val analysis = Subject(1, "Analysis", 0, isAcademic = true)
    private val physics = Subject(2, "Physics", 1, isAcademic = true)

    private fun lecture(id: Long, subject: Subject, cancelled: Boolean = false) = CalendarEvent(
        id = id, key = "k$id", source = EventSource.IMPORTED, title = subject.name, subjectId = subject.id,
        start = day.atTime(9, 0).atZone(zone).toInstant(), end = day.atTime(10, 0).atZone(zone).toInstant(), cancelled = cancelled,
    )

    private fun due(id: Long, date: LocalDate, done: Boolean = false) =
        Assignment(id, "Sheet $id", null, "", date.atTime(12, 0).atZone(zone).toInstant(), done)

    @Test
    fun cancelledLecturesDoNotAskForReading() {
        val plan = CheckIn.plan(day, zone, listOf(analysis, physics), listOf(lecture(1, analysis), lecture(2, physics, cancelled = true)), emptyList(), emptyList(), emptyList(), emptySet())
        assertEquals(listOf("Analysis"), plan.readingSubjects.map { it.name })
    }

    @Test
    fun overdueWorkStillOpenIsIncluded() {
        val work = listOf(due(1, day.minusDays(2)), due(2, day.minusDays(3), done = true), due(3, day.plusDays(1)), due(4, day.plusDays(5)))
        val plan = CheckIn.plan(day, zone, emptyList(), emptyList(), work, emptyList(), emptyList(), emptySet())
        assertEquals(listOf(1L, 3L), plan.homework.map { it.id })
    }
}

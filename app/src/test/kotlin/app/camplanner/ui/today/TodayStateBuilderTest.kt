package app.camplanner.ui.today

import app.camplanner.designsystem.components.StarState
import app.camplanner.model.AppSettings
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TodayStateBuilderTest {
    private val zone = ZoneId.of("Europe/London")
    private val date = LocalDate.parse("2026-10-20")

    private fun ev(id: Long, start: String, end: String) = CalendarEvent(
        id, "k$id", EventSource.IMPORTED, "E$id", null,
        LocalDateTime.of(date, LocalTime.parse(start)).atZone(zone).toInstant(),
        LocalDateTime.of(date, LocalTime.parse(end)).atZone(zone).toInstant(),
    )

    private fun build(now: String, vararg events: CalendarEvent, settings: AppSettings = AppSettings()) =
        TodayStateBuilder.build(date, LocalDateTime.of(date, LocalTime.parse(now)), events.toList(), emptyList(), settings, { null }, zone, null)

    private fun shape(s: TodayUiState) = s.items.map {
        when (it) {
            is TodayItem.Event -> "${it.title}:${it.state}"
            is TodayItem.Gap -> "gap${it.minutes}"
            is TodayItem.Now -> "now"
        }
    }

    @Test
    fun `now marker sits after the current event`() {
        val s = build("11:20", ev(1, "09:00", "10:00"), ev(2, "11:00", "12:00"), ev(3, "14:00", "15:00"))
        assertEquals(listOf("E1:PAST", "gap60", "E2:CURRENT", "now", "gap120", "E3:FUTURE"), shape(s))
    }

    @Test
    fun `now marker sits in a gap and replaces the gap line`() {
        val s = build("12:30", ev(1, "09:00", "10:00"), ev(2, "11:00", "12:00"), ev(3, "14:00", "15:00"))
        assertEquals(listOf("E1:PAST", "gap60", "E2:PAST", "now", "E3:FUTURE"), shape(s))
    }

    @Test
    fun `no now marker before the first or after the last event`() {
        assertEquals(listOf("E1:FUTURE"), shape(build("07:00", ev(1, "09:00", "10:00"))))
        assertEquals(listOf("E1:PAST"), shape(build("21:00", ev(1, "09:00", "10:00"))))
    }

    @Test
    fun `alarm and check-in times follow settings`() {
        val s = build("07:00", ev(1, "09:00", "10:00"), ev(2, "16:00", "17:00"))
        assertEquals(LocalTime.of(7, 30), s.morningAlarm)
        assertEquals(LocalTime.of(17, 30), s.checkIn)
    }

    @Test
    fun `term label in Roman numerals, vacation outside, none when unset`() {
        val term = AppSettings(termStart = LocalDate.parse("2026-10-06"), termEnd = LocalDate.parse("2026-12-04"))
        assertEquals("Michaelmas Term  ·  Week II", TodayStateBuilder.termLabel(date, term))
        assertEquals("Vacation", TodayStateBuilder.termLabel(LocalDate.parse("2026-12-20"), term))
        assertNull(TodayStateBuilder.termLabel(date, AppSettings()))
    }
}

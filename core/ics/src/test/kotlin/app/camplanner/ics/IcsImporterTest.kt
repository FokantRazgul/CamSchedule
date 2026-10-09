package app.camplanner.ics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class IcsImporterTest {

    private val london = ZoneId.of("Europe/London")
    private val farFuture = Instant.parse("2030-01-01T00:00:00Z")
    private lateinit var originalZone: TimeZone

    @Before
    fun setUp() {
        // The phone's zone; floating and all-day values are read in it.
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(originalZone)

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(london).toInstant()

    private fun import(vararg events: String, withVTimezone: Boolean = true, until: Instant = farFuture): ImportResult =
        IcsImporter().parse(calendar(*events, withVTimezone = withVTimezone), until)

    private fun calendar(vararg events: String, withVTimezone: Boolean) = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//Cam Planner//EN\r\n")
        if (withVTimezone) append(LONDON_VTIMEZONE)
        events.forEach { append(it.trimIndent().replace("\n", "\r\n")).append("\r\n") }
        append("END:VCALENDAR\r\n")
    }

    // --- Single events ----------------------------------------------------------------------------

    @Test
    fun `single event with TZID`() {
        val result = import(
            """
            BEGIN:VEVENT
            UID:single-1
            DTSTAMP:20261001T000000Z
            DTSTART;TZID=Europe/London:20261020T100000
            DTEND;TZID=Europe/London:20261020T110000
            SUMMARY:Vectors & Matrices - Lecture 3
            LOCATION:Mill Lane Lecture Rooms
            DESCRIPTION:Bring notes
            END:VEVENT
            """,
        )
        val e = result.events.single()
        assertEquals(local("2026-10-20T10:00"), e.start)
        assertEquals(Instant.parse("2026-10-20T09:00:00Z"), e.start) // BST
        assertEquals(Duration.ofHours(1), Duration.between(e.start, e.end))
        assertEquals("Vectors & Matrices - Lecture 3", e.summary)
        assertEquals("Mill Lane Lecture Rooms", e.location)
        assertEquals("Bring notes", e.description)
        assertEquals("single-1@${e.start.epochSecond}", e.key)
        assertFalse(e.allDay)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `UTC event`() {
        val e = import(
            """
            BEGIN:VEVENT
            UID:utc-1
            DTSTART:20261110T140000Z
            DTEND:20261110T153000Z
            SUMMARY:Seminar
            END:VEVENT
            """,
        ).events.single()
        assertEquals(Instant.parse("2026-11-10T14:00:00Z"), e.start)
        assertEquals(Instant.parse("2026-11-10T15:30:00Z"), e.end)
        assertNull(e.location)
    }

    @Test
    fun `floating time is read in the phone's zone`() {
        val e = import(
            """
            BEGIN:VEVENT
            UID:float-1
            DTSTART:20261020T090000
            DURATION:PT2H
            SUMMARY:Practical
            END:VEVENT
            """,
        ).events.single()
        assertEquals(local("2026-10-20T09:00"), e.start)
        assertEquals(local("2026-10-20T11:00"), e.end)
    }

    @Test
    fun `all-day event`() {
        val e = import(
            """
            BEGIN:VEVENT
            UID:allday-1
            DTSTART;VALUE=DATE:20261025
            DTEND;VALUE=DATE:20261026
            SUMMARY:Reading week
            END:VEVENT
            """,
        ).events.single()
        assertTrue(e.allDay)
        assertEquals(local("2026-10-25T00:00"), e.start)
        // The change day is 25 hours long; the event still ends at the next local midnight.
        assertEquals(local("2026-10-26T00:00"), e.end)
    }

    @Test
    fun `multi-line location is flattened`() {
        val e = import(
            """
            BEGIN:VEVENT
            UID:loc-1
            DTSTART:20261110T140000Z
            DTEND:20261110T150000Z
            SUMMARY:Supervision
            LOCATION:Trinity College\nGreat Court\, Staircase E
            END:VEVENT
            """,
        ).events.single()
        assertEquals("Trinity College, Great Court, Staircase E", e.location)
    }

    // --- Recurrence and daylight saving -----------------------------------------------------------

    private val weeklyAcrossAutumn = """
        BEGIN:VEVENT
        UID:weekly-autumn
        DTSTART;TZID=Europe/London:20261013T090000
        DTEND;TZID=Europe/London:20261013T100000
        RRULE:FREQ=WEEKLY;COUNT=4
        SUMMARY:Analysis I
        END:VEVENT
    """

    @Test
    fun `weekly lecture stays at 09_00 local across the autumn change`() {
        val starts = import(weeklyAcrossAutumn).events.map { it.start }
        assertEquals(
            listOf(
                Instant.parse("2026-10-13T08:00:00Z"), // BST
                Instant.parse("2026-10-20T08:00:00Z"), // BST
                Instant.parse("2026-10-27T09:00:00Z"), // GMT, clocks went back on the 25th
                Instant.parse("2026-11-03T09:00:00Z"),
            ),
            starts,
        )
        assertTrue(starts.all { it.atZone(london).toLocalTime().hour == 9 })
    }

    @Test
    fun `TZID without a VTIMEZONE block resolves as an Olson zone`() {
        val starts = import(weeklyAcrossAutumn, withVTimezone = false).events.map { it.start }
        assertEquals(Instant.parse("2026-10-20T08:00:00Z"), starts[1])
        assertEquals(Instant.parse("2026-10-27T09:00:00Z"), starts[2])
    }

    @Test
    fun `weekly lecture stays at 09_00 local across the spring change, UNTIL inclusive`() {
        val events = import(
            """
            BEGIN:VEVENT
            UID:weekly-spring
            DTSTART;TZID=Europe/London:20270322T090000
            DTEND;TZID=Europe/London:20270322T100000
            RRULE:FREQ=WEEKLY;UNTIL=20270405T080000Z
            SUMMARY:Linear Algebra
            END:VEVENT
            """,
        ).events
        assertEquals(
            listOf(
                Instant.parse("2027-03-22T09:00:00Z"), // GMT
                Instant.parse("2027-03-29T08:00:00Z"), // BST, clocks went forward on the 28th
                Instant.parse("2027-04-05T08:00:00Z"),
            ),
            events.map { it.start },
        )
        assertTrue(events.all { Duration.between(it.start, it.end) == Duration.ofHours(1) })
    }

    @Test
    fun `daily event spanning the autumn change night keeps local time and length`() {
        val events = import(
            """
            BEGIN:VEVENT
            UID:daily
            DTSTART;TZID=Europe/London:20261024T010000
            DTEND;TZID=Europe/London:20261024T013000
            RRULE:FREQ=DAILY;COUNT=3
            SUMMARY:Observatory session
            END:VEVENT
            """,
        ).events
        assertEquals(listOf(1, 1, 1), events.map { it.start.atZone(london).toLocalTime().hour })
        assertEquals(
            listOf("2026-10-24", "2026-10-25", "2026-10-26"),
            events.map { it.start.atZone(london).toLocalDate().toString() },
        )
        assertTrue(events.all { Duration.between(it.start, it.end) == Duration.ofMinutes(30) })
    }

    @Test
    fun `BYDAY expands Monday Wednesday Friday`() {
        val events = import(
            """
            BEGIN:VEVENT
            UID:mwf
            DTSTART;TZID=Europe/London:20261012T110000
            DTEND;TZID=Europe/London:20261012T120000
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20261023T230000Z
            SUMMARY:Probability
            END:VEVENT
            """,
        ).events
        assertEquals(
            listOf("2026-10-12", "2026-10-14", "2026-10-16", "2026-10-19", "2026-10-21", "2026-10-23"),
            events.map { it.start.atZone(london).toLocalDate().toString() },
        )
    }

    @Test
    fun `EXDATE removes an occurrence`() {
        val events = import(
            """
            BEGIN:VEVENT
            UID:exdate
            DTSTART;TZID=Europe/London:20261013T090000
            DTEND;TZID=Europe/London:20261013T100000
            RRULE:FREQ=WEEKLY;COUNT=4
            EXDATE;TZID=Europe/London:20261027T090000
            SUMMARY:Analysis I
            END:VEVENT
            """,
        ).events
        assertEquals(
            listOf("2026-10-13", "2026-10-20", "2026-11-03"),
            events.map { it.start.atZone(london).toLocalDate().toString() },
        )
    }

    @Test
    fun `EXDATE given in UTC matches the instance after the change`() {
        val events = import(
            """
            BEGIN:VEVENT
            UID:exdate-utc
            DTSTART;TZID=Europe/London:20261013T090000
            DTEND;TZID=Europe/London:20261013T100000
            RRULE:FREQ=WEEKLY;COUNT=4
            EXDATE:20261020T080000Z,20261103T090000Z
            SUMMARY:Analysis I
            END:VEVENT
            """,
        ).events
        assertEquals(
            listOf("2026-10-13", "2026-10-27"),
            events.map { it.start.atZone(london).toLocalDate().toString() },
        )
    }

    @Test
    fun `RECURRENCE-ID moves one occurrence and keeps its key`() {
        val result = import(
            weeklyAcrossAutumn,
            """
            BEGIN:VEVENT
            UID:weekly-autumn
            RECURRENCE-ID;TZID=Europe/London:20261027T090000
            DTSTART;TZID=Europe/London:20261028T140000
            DTEND;TZID=Europe/London:20261028T150000
            SUMMARY:Analysis I (moved)
            LOCATION:Cockcroft Lecture Theatre
            END:VEVENT
            """,
        )
        val events = result.events
        assertEquals(4, events.size)
        val moved = events.single { it.summary == "Analysis I (moved)" }
        assertEquals(local("2026-10-28T14:00"), moved.start)
        assertEquals("weekly-autumn@${local("2026-10-27T09:00").epochSecond}", moved.key)
        assertTrue(events.none { it.start == local("2026-10-27T09:00") })
        assertEquals(events.size, events.map { it.key }.toSet().size)
    }

    @Test
    fun `cancelled occurrence and cancelled series are dropped`() {
        val events = import(
            weeklyAcrossAutumn,
            """
            BEGIN:VEVENT
            UID:weekly-autumn
            RECURRENCE-ID;TZID=Europe/London:20261020T090000
            DTSTART;TZID=Europe/London:20261020T090000
            DTEND;TZID=Europe/London:20261020T100000
            STATUS:CANCELLED
            SUMMARY:Analysis I
            END:VEVENT
            """,
            """
            BEGIN:VEVENT
            UID:gone
            DTSTART;TZID=Europe/London:20261014T090000
            DTEND;TZID=Europe/London:20261014T100000
            RRULE:FREQ=WEEKLY;COUNT=8
            STATUS:CANCELLED
            SUMMARY:Cancelled course
            END:VEVENT
            """,
        ).events
        assertEquals(
            listOf("2026-10-13", "2026-10-27", "2026-11-03"),
            events.map { it.start.atZone(london).toLocalDate().toString() },
        )
    }

    @Test
    fun `open-ended rule stops at the horizon`() {
        val events = import(
            """
            BEGIN:VEVENT
            UID:forever
            DTSTART;TZID=Europe/London:20261013T090000
            DTEND;TZID=Europe/London:20261013T100000
            RRULE:FREQ=WEEKLY
            SUMMARY:Choir
            END:VEVENT
            """,
            until = local("2026-12-01T00:00"),
        ).events
        assertEquals(7, events.size)
        assertEquals(local("2026-11-24T09:00"), events.last().start)
    }

    @Test
    fun `occurrence keys are stable between two imports of the same file`() {
        val a = import(weeklyAcrossAutumn).events.map { it.key }
        val b = import(weeklyAcrossAutumn).events.map { it.key }
        assertEquals(a, b)
    }

    @Test
    fun `missing summary becomes Untitled`() {
        val e = import(
            """
            BEGIN:VEVENT
            UID:nosummary
            DTSTART:20261110T140000Z
            DTEND:20261110T150000Z
            END:VEVENT
            """,
        ).events.single()
        assertEquals("Untitled", e.summary)
    }

    @Test(expected = IcsFormatException::class)
    fun `garbage is rejected`() {
        IcsImporter().parse("this is not a calendar", farFuture)
    }

    private companion object {
        val LONDON_VTIMEZONE = """
            BEGIN:VTIMEZONE
            TZID:Europe/London
            BEGIN:DAYLIGHT
            TZOFFSETFROM:+0000
            TZOFFSETTO:+0100
            TZNAME:BST
            DTSTART:19700329T010000
            RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
            END:DAYLIGHT
            BEGIN:STANDARD
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0000
            TZNAME:GMT
            DTSTART:19701025T020000
            RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
            END:STANDARD
            END:VTIMEZONE
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}

package app.camplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class AlmanacTest {

    private fun localTime(i: Instant?): LocalTime = i!!.atZone(LONDON).toLocalTime()

    private fun assertNear(expected: String, actual: LocalTime, minutes: Long = 4) {
        val diff = Duration.between(LocalTime.parse(expected), actual).abs()
        assertTrue("expected ~$expected, was $actual", diff <= Duration.ofMinutes(minutes))
    }

    @Test
    fun `cambridge sunrise and sunset in Michaelmas`() {
        // Published times for Cambridge, 20 Oct 2026: about 07:29 and 17:53 BST.
        val sun = Almanac.sunTimes(LocalDate.parse("2026-10-20"), Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
        assertNear("07:29", localTime(sun.sunrise))
        assertNear("17:53", localTime(sun.sunset))
    }

    @Test
    fun `cambridge midsummer and midwinter`() {
        val june = Almanac.sunTimes(LocalDate.parse("2026-06-21"), Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
        assertNear("04:41", localTime(june.sunrise))
        assertNear("21:23", localTime(june.sunset)) // later than London: further north
        val december = Almanac.sunTimes(LocalDate.parse("2026-12-21"), Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
        assertNear("08:05", localTime(december.sunrise))
        assertNear("15:52", localTime(december.sunset))
    }

    @Test
    fun `day after the clocks go back starts an hour earlier on the clock`() {
        val sat = Almanac.sunTimes(LocalDate.parse("2026-10-24"), Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
        val sun = Almanac.sunTimes(LocalDate.parse("2026-10-25"), Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
        val shift = Duration.between(localTime(sun.sunrise), localTime(sat.sunrise)).toMinutes()
        assertTrue("clock sunrise should move ~58 min earlier, moved $shift", shift in 55..62)
    }

    @Test
    fun `polar day has no sunset`() {
        val sun = Almanac.sunTimes(LocalDate.parse("2026-06-21"), 78.2, 15.6) // Svalbard
        assertNull(sun.sunrise)
        assertNull(sun.sunset)
    }

    @Test
    fun `moon phase against known lunations`() {
        // Full moon 7 Oct 2025 03:47 UTC; new moon 21 Oct 2025 12:25 UTC.
        val full = Almanac.moon(Instant.parse("2025-10-07T03:47:00Z"))
        assertEquals(Almanac.MoonPhaseName.FULL, full.phase)
        assertTrue(full.illumination > 0.97)
        val new = Almanac.moon(Instant.parse("2025-10-21T12:25:00Z"))
        assertEquals(Almanac.MoonPhaseName.NEW, new.phase)
        assertTrue(new.illumination < 0.03)
        val firstQuarter = Almanac.moon(Instant.parse("2025-10-29T16:21:00Z"))
        assertEquals(Almanac.MoonPhaseName.FIRST_QUARTER, firstQuarter.phase)
        assertTrue(firstQuarter.waxing)
    }

    @Test
    fun `term weeks run Thursday to Wednesday`() {
        val start = LocalDate.parse("2026-10-06") // a Tuesday
        val end = LocalDate.parse("2026-12-04")
        assertEquals(0, TermCalendar.weekOf(LocalDate.parse("2026-10-07"), start, end))
        assertEquals(1, TermCalendar.weekOf(LocalDate.parse("2026-10-08"), start, end))
        assertEquals(1, TermCalendar.weekOf(LocalDate.parse("2026-10-14"), start, end))
        assertEquals(2, TermCalendar.weekOf(LocalDate.parse("2026-10-20"), start, end))
        assertEquals(8, TermCalendar.weekOf(LocalDate.parse("2026-12-02"), start, end))
        assertEquals(9, TermCalendar.weekOf(LocalDate.parse("2026-12-03"), start, end)) // the last Thu-Fri of Full Term
        assertNull(TermCalendar.weekOf(LocalDate.parse("2026-12-05"), start, end))
        assertEquals(TermCalendar.Term.MICHAELMAS, TermCalendar.termOf(start))
        assertEquals(TermCalendar.Term.LENT, TermCalendar.termOf(LocalDate.parse("2027-01-19")))
        assertEquals(TermCalendar.Term.EASTER, TermCalendar.termOf(LocalDate.parse("2027-04-27")))
    }

    @Test
    fun `roman numerals`() {
        assertEquals("MMXXVI", Roman.of(2026))
        assertEquals("XIV", Roman.of(14))
        assertEquals("II", Roman.of(2))
        assertEquals("XLIX", Roman.of(49))
        assertEquals("0", Roman.of(0))
    }
}

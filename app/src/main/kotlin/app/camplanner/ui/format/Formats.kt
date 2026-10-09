package app.camplanner.ui.format

import app.camplanner.model.TravelMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting. English UI, 24-hour clock, British date order. */
object Formats {
    private val locale = Locale.UK
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", locale)
    private val shortDateFmt = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayMonthShortFmt = DateTimeFormatter.ofPattern("d MMM", locale)

    fun time(t: LocalTime): String = timeFmt.format(t)
    fun dayMonth(d: LocalDate): String = dayMonthFmt.format(d)
    fun weekday(d: LocalDate): String = weekdayFmt.format(d)
    fun shortDate(d: LocalDate): String = shortDateFmt.format(d)
    fun dayMonthShort(d: LocalDate): String = dayMonthShortFmt.format(d)

    /** "MMXXVI" */
    fun romanYear(d: LocalDate): String = app.camplanner.domain.Roman.of(d.year)

    fun count(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"

    /** "45 min", "1 h 30 min", "2 h". */
    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun mode(mode: TravelMode): String = when (mode) {
        TravelMode.WALK -> "walk"
        TravelMode.BIKE -> "bike"
    }

    /** "today", "tomorrow", "in 3 days", "2 days ago" relative to [today]. */
    fun relativeDay(date: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days == -1L -> "yesterday"
            days > 1 -> "in $days days"
            else -> "${-days} days ago"
        }
    }

    fun km(meters: Int): String = String.format(locale, "%.1f", meters / 1000.0)
    fun km(km: Double): String = String.format(locale, "%.1f", km)
}

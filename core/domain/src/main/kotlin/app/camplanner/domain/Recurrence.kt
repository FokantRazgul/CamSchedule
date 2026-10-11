package app.camplanner.domain

import app.camplanner.model.EventSeries
import app.camplanner.model.Frequency
import app.camplanner.model.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Pure expansion of user-made repeating events. Times are local wall-clock times, so 19:00 stays 19:00 across the clock changes. */
object Recurrence {

    /** Safety stop for rules without an end. */
    const val MAX_OCCURRENCES = 1_000

    /**
     * The dates [rule] produces from [start] onwards, within [from]..[to] inclusive. [count]
     * counts from the very first occurrence, including ones later made exceptions (as in RFC 5545).
     */
    fun dates(rule: RepeatRule, start: LocalDate, from: LocalDate, to: LocalDate, exceptions: Set<LocalDate> = emptySet()): List<LocalDate> {
        val interval = rule.interval.coerceAtLeast(1)
        val count = rule.count
        val last = listOfNotNull(to, rule.until).min()
        val out = mutableListOf<LocalDate>()
        var produced = 0
        fun accept(d: LocalDate): Boolean {
            if (d.isAfter(last)) return false
            produced++
            if (count != null && produced > count) return false
            if (!d.isBefore(from) && d !in exceptions) out += d
            return produced < MAX_OCCURRENCES
        }
        when (rule.frequency) {
            Frequency.DAILY -> {
                var d = start
                while (accept(d)) d = d.plusDays(interval.toLong())
            }
            Frequency.WEEKLY -> {
                val days = rule.days.ifEmpty { setOf(start.dayOfWeek) }.sorted()
                var weekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                outer@ while (true) {
                    for (dow in days) {
                        val d = weekStart.plusDays((dow.value - 1).toLong())
                        if (d.isBefore(start)) continue
                        if (!accept(d)) break@outer
                    }
                    weekStart = weekStart.plusWeeks(interval.toLong())
                }
            }
        }
        return out
    }

    data class Occurrence(val date: LocalDate, val start: Instant, val end: Instant)

    /** Occurrences of [series] in [from]..[to]; an end time at or before the start runs past midnight. */
    fun occurrences(series: EventSeries, from: LocalDate, to: LocalDate, zone: ZoneId): List<Occurrence> =
        dates(series.rule, series.startDate, from, to, series.exceptions).map { d ->
            val start = d.atTime(series.startTime).atZone(zone).toInstant()
            val endDate = if (series.endTime.isAfter(series.startTime)) d else d.plusDays(1)
            Occurrence(d, start, endDate.atTime(series.endTime).atZone(zone).toInstant())
        }

    /** "Every week on Tue and Thu, until Fri 4 Dec". */
    fun describe(rule: RepeatRule, start: LocalDate): String {
        val dayNames = rule.days.ifEmpty { setOf(start.dayOfWeek) }.sorted()
            .map { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.UK) }
        val list = when (dayNames.size) {
            1 -> dayNames[0]
            else -> dayNames.dropLast(1).joinToString(", ") + " and " + dayNames.last()
        }
        val base = when {
            rule.frequency == Frequency.DAILY && rule.interval == 1 -> "Every day"
            rule.frequency == Frequency.DAILY -> "Every ${rule.interval} days"
            rule.days == WEEKDAYS && rule.interval == 1 -> "Every weekday"
            rule.interval == 1 -> "Every week on $list"
            rule.interval == 2 -> "Every other week on $list"
            else -> "Every ${rule.interval} weeks on $list"
        }
        val end = when {
            rule.until != null -> ", until ${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.UK).format(rule.until)}"
            rule.count != null -> ", ${rule.count} times"
            else -> ""
        }
        return base + end
    }

    val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
}

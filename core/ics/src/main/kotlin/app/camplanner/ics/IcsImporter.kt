package app.camplanner.ics

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.util.ICalDate
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** One concrete occurrence read from an .ics file. */
data class ImportedEvent(
    val uid: String,
    /** Start of this occurrence as the series defines it (RECURRENCE-ID for moved instances). */
    val originalStart: Instant,
    val summary: String,
    val description: String?,
    val location: String?,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val timeZoneId: String?,
) {
    /** Stable across re-imports of the same feed; per-event overrides are keyed on it. */
    val key: String get() = "$uid@${originalStart.epochSecond}"
}

data class ImportResult(
    val events: List<ImportedEvent>,
    val warnings: List<String>,
)

class IcsFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Turns an .ics file into expanded occurrences. Parsing, RRULE/RDATE/EXDATE expansion and VTIMEZONE
 * handling are biweekly's; this class adds RECURRENCE-ID overrides, cancellations and a horizon.
 *
 * Floating times and all-day dates are interpreted in the JVM default time zone (the phone's zone),
 * which is how biweekly parses them.
 */
class IcsImporter(
    private val maxOccurrencesPerSeries: Int = 1500,
) {

    fun parse(text: String, expandUntil: Instant): ImportResult =
        read(expandUntil) { Biweekly.parse(text).all() }

    fun parse(input: InputStream, expandUntil: Instant): ImportResult =
        read(expandUntil) { Biweekly.parse(input).all() }

    private fun read(expandUntil: Instant, parse: () -> List<ICalendar>): ImportResult {
        val calendars = try {
            parse()
        } catch (e: Exception) {
            throw IcsFormatException("This file couldn't be read as an iCalendar (.ics) file.", e)
        }
        if (calendars.isEmpty()) throw IcsFormatException("No calendar found in this file.")

        val warnings = mutableListOf<String>()
        val out = mutableListOf<ImportedEvent>()
        for (ical in calendars) {
            val byUid = ical.events.groupBy { it.uid?.value ?: "no-uid-${System.identityHashCode(it)}" }
            for ((uid, components) in byUid) {
                try {
                    out += expandSeries(ical, uid, components, expandUntil, warnings)
                } catch (e: Exception) {
                    warnings += "Skipped \"${components.firstOrNull()?.summary?.value ?: uid}\": ${e.message}"
                }
            }
        }
        return ImportResult(out.sortedWith(compareBy({ it.start }, { it.summary })), warnings)
    }

    private fun expandSeries(
        ical: ICalendar,
        uid: String,
        components: List<VEvent>,
        expandUntil: Instant,
        warnings: MutableList<String>,
    ): List<ImportedEvent> {
        val master = components.firstOrNull { it.recurrenceId == null }
        val overrides = components.filter { it.recurrenceId != null }
        val overriddenStarts = overrides.mapNotNull { it.recurrenceId?.value?.toInstant() }.toSet()

        val result = mutableListOf<ImportedEvent>()
        if (master != null && !master.isCancelled()) {
            result += expandMaster(ical, uid, master, expandUntil, warnings)
                .filterNot { it.originalStart in overriddenStarts }
        }
        for (o in overrides) {
            if (o.isCancelled()) continue
            val originalStart = o.recurrenceId.value.toInstant()
            single(ical, uid, o, originalStart)?.let { result += it }
        }
        return result
    }

    private fun expandMaster(
        ical: ICalendar,
        uid: String,
        event: VEvent,
        expandUntil: Instant,
        warnings: MutableList<String>,
    ): List<ImportedEvent> {
        val dtStart = event.dateStart?.value ?: return emptyList<ImportedEvent>().also {
            warnings += "Skipped \"${event.summary?.value ?: uid}\": no start time."
        }
        val recurring = event.recurrenceRule != null || event.recurrenceDates.isNotEmpty()
        if (!recurring) return listOfNotNull(single(ical, uid, event, dtStart.toInstant()))

        val zone = iterationZone(ical, event)
        val iterator = event.getDateIterator(zone)
        val length = lengthOf(event, dtStart)
        val out = mutableListOf<ImportedEvent>()
        while (iterator.hasNext()) {
            val occurrence = iterator.next()
            val start = if (dtStart.hasTime()) occurrence.toInstant() else allDayStart(occurrence)
            if (start.isAfter(expandUntil)) break
            if (out.size >= maxOccurrencesPerSeries) {
                warnings += "\"${event.summary?.value ?: uid}\" has more than $maxOccurrencesPerSeries occurrences; the rest were skipped."
                break
            }
            out += build(uid, event, ical, originalStart = start, start = start, length = length, allDay = !dtStart.hasTime())
        }
        return out
    }

    private fun single(ical: ICalendar, uid: String, event: VEvent, originalStart: Instant): ImportedEvent? {
        val dtStart = event.dateStart?.value ?: return null
        val start = if (dtStart.hasTime()) dtStart.toInstant() else allDayStart(dtStart)
        val original = if (dtStart.hasTime()) originalStart else allDayStartOf(originalStart)
        return build(uid, event, ical, original, start, lengthOf(event, dtStart), allDay = !dtStart.hasTime())
    }

    private fun build(
        uid: String,
        event: VEvent,
        ical: ICalendar,
        originalStart: Instant,
        start: Instant,
        length: Length,
        allDay: Boolean,
    ): ImportedEvent {
        val end = when (length) {
            is Length.Exact -> start + length.duration
            is Length.Days -> start.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(length.days)
                .atStartOfDay(ZoneId.systemDefault()).toInstant()
        }
        return ImportedEvent(
            uid = uid,
            originalStart = originalStart,
            summary = event.summary?.value?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
            description = event.description?.value?.trim()?.takeIf { it.isNotEmpty() },
            location = event.location?.value?.trim()?.replace(Regex("\\s*\\n\\s*"), ", ")?.takeIf { it.isNotEmpty() },
            start = start,
            end = if (end.isBefore(start)) start else end,
            allDay = allDay,
            timeZoneId = event.dateStart?.let { ical.timezoneInfo.getTimezone(it)?.timeZone?.id },
        )
    }

    private sealed interface Length {
        data class Exact(val duration: Duration) : Length
        data class Days(val days: Long) : Length
    }

    private fun lengthOf(event: VEvent, dtStart: ICalDate): Length {
        val dtEnd = event.dateEnd?.value
        val duration = event.duration?.value
        if (!dtStart.hasTime()) {
            val days = when {
                dtEnd != null -> java.time.temporal.ChronoUnit.DAYS.between(localDate(dtStart), localDate(dtEnd))
                duration != null -> duration.toMillis() / 86_400_000L
                else -> 1L
            }
            return Length.Days(days.coerceAtLeast(1))
        }
        return Length.Exact(
            when {
                dtEnd != null -> Duration.ofMillis(dtEnd.time - dtStart.time)
                duration != null -> Duration.ofMillis(duration.toMillis())
                else -> Duration.ZERO
            }.let { if (it.isNegative) Duration.ZERO else it },
        )
    }

    /** The zone to expand recurrences in: the DTSTART TZID, or the phone's zone for floating/all-day. */
    private fun iterationZone(ical: ICalendar, event: VEvent): TimeZone {
        val dtStart = event.dateStart
        val tzInfo = ical.timezoneInfo
        tzInfo.getTimezone(dtStart)?.timeZone?.let { return it }
        val utc = dtStart.value.rawComponents?.isUtc == true
        return if (utc) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
    }

    private fun VEvent.isCancelled(): Boolean = status?.isCancelled == true

    private fun localDate(date: ICalDate): LocalDate {
        val raw = date.rawComponents
        return if (raw != null) LocalDate.of(raw.year, raw.month, raw.date)
        else date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun allDayStart(date: java.util.Date): Instant =
        date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun allDayStart(date: ICalDate): Instant = localDate(date).atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun allDayStartOf(instant: Instant): Instant = allDayStart(java.util.Date.from(instant))
}

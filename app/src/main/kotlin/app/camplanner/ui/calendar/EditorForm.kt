package app.camplanner.ui.calendar

import app.camplanner.data.repo.EventDraft
import app.camplanner.domain.Recurrence
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSeries
import app.camplanner.model.EventSource
import app.camplanner.model.Frequency
import app.camplanner.model.RepeatRule
import app.camplanner.model.TravelMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class RepeatChoice(val label: String) {
    NEVER("Never"), DAILY("Every day"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), FORTNIGHTLY("Every 2 weeks"),
}

enum class EndsChoice(val label: String) { NEVER("Never"), ON_DATE("On a date"), AFTER("After a number") }

/** What the event editor shows; pure, so the mapping to and from events is unit-tested. */
data class EditorForm(
    val isNew: Boolean,
    /** Imported lectures: only time, place, travel and reminder can change. */
    val imported: Boolean = false,
    /** False when editing one occurrence of a series or an imported lecture. */
    val canRepeat: Boolean = true,
    val title: String = "",
    val subjectId: Long? = null,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val repeat: RepeatChoice = RepeatChoice.NEVER,
    val days: Set<DayOfWeek> = emptySet(),
    val interval: Int = 1,
    val ends: EndsChoice = EndsChoice.NEVER,
    val until: LocalDate? = null,
    val count: String = "",
    val location: String = "",
    val mode: TravelMode? = null,
    val reminder: Int? = null,
    val note: String = "",
) {
    /** Why Save is not possible yet, or null. */
    val problem: String?
        get() = when {
            title.isBlank() -> "Add a title"
            repeat != RepeatChoice.NEVER && ends == EndsChoice.ON_DATE && (until == null || until.isBefore(date)) ->
                "Choose an end date on or after ${app.camplanner.ui.format.Formats.shortDate(date)}"
            repeat != RepeatChoice.NEVER && ends == EndsChoice.AFTER && (count.toIntOrNull() ?: 0) < 1 -> "Enter how many times it happens"
            else -> null
        }

    /** True when the event runs past midnight (the end is not after the start). */
    val overnight: Boolean get() = !end.isAfter(start)

    fun rule(): RepeatRule? {
        val until = until.takeIf { ends == EndsChoice.ON_DATE }
        val count = count.toIntOrNull()?.takeIf { ends == EndsChoice.AFTER }
        val weekly = days.ifEmpty { setOf(date.dayOfWeek) }
        return when (repeat) {
            RepeatChoice.NEVER -> null
            RepeatChoice.DAILY -> RepeatRule(Frequency.DAILY, interval = interval, until = until, count = count)
            RepeatChoice.WEEKDAYS -> RepeatRule(Frequency.WEEKLY, days = Recurrence.WEEKDAYS, until = until, count = count)
            RepeatChoice.WEEKLY -> RepeatRule(Frequency.WEEKLY, days = weekly, until = until, count = count)
            RepeatChoice.FORTNIGHTLY -> RepeatRule(Frequency.WEEKLY, interval = 2, days = weekly, until = until, count = count)
        }
    }

    /** "Every week on Tue and Thu, until Fri 4 Dec" or null for a one-off. */
    fun summary(): String? = rule()?.let { Recurrence.describe(it, date) }

    fun toDraft() = EventDraft(
        title = title.trim(),
        subjectId = subjectId,
        date = date,
        start = start,
        end = end,
        repeat = if (canRepeat) rule() else null,
        location = location.trim().ifEmpty { null },
        note = note.trim().ifEmpty { null },
        travelMode = mode,
        reminderMinutes = reminder,
    )

    /** Picking a repeat keeps the chosen weekdays sensible. */
    fun withRepeat(choice: RepeatChoice): EditorForm = copy(
        repeat = choice,
        days = if ((choice == RepeatChoice.WEEKLY || choice == RepeatChoice.FORTNIGHTLY) && days.isEmpty()) setOf(date.dayOfWeek) else days,
        interval = if (choice == RepeatChoice.DAILY) interval else 1,
    )

    /** Moving the start keeps the length of the event. */
    fun withStart(t: LocalTime): EditorForm {
        val length = java.time.Duration.between(start, end).let { if (it.isNegative || it.isZero) it.plusDays(1) else it }
        return copy(start = t, end = t.plus(length))
    }

    companion object {
        /** A blank event on [date], starting at the next whole hour if that is today. */
        fun new(date: LocalDate, now: LocalDateTime): EditorForm {
            val start = if (date == now.toLocalDate() && now.hour in 7..21) LocalTime.of(now.hour + 1, 0) else LocalTime.of(9, 0)
            return EditorForm(isNew = true, date = date, start = start, end = start.plusHours(1))
        }

        fun of(event: CalendarEvent, series: EventSeries?, wholeSeries: Boolean, zone: ZoneId): EditorForm {
            val start = event.start.atZone(zone)
            val end = event.end.atZone(zone)
            val base = EditorForm(
                isNew = false,
                imported = event.source == EventSource.IMPORTED,
                canRepeat = event.source != EventSource.IMPORTED && (series == null || wholeSeries),
                title = event.title,
                subjectId = event.subjectId,
                date = start.toLocalDate(),
                start = start.toLocalTime(),
                end = end.toLocalTime(),
                location = event.location.orEmpty(),
                mode = event.travelModeOverride,
                reminder = event.reminderMinutes,
                note = event.note.orEmpty(),
            )
            if (series == null || !wholeSeries) return base
            val r = series.rule
            return base.copy(
                title = series.title,
                subjectId = series.subjectId,
                date = series.startDate,
                start = series.startTime,
                end = series.endTime,
                repeat = when {
                    r.frequency == Frequency.DAILY -> RepeatChoice.DAILY
                    r.interval == 1 && r.days == Recurrence.WEEKDAYS -> RepeatChoice.WEEKDAYS
                    r.interval >= 2 -> RepeatChoice.FORTNIGHTLY
                    else -> RepeatChoice.WEEKLY
                },
                days = r.days.ifEmpty { setOf(series.startDate.dayOfWeek) },
                interval = r.interval,
                ends = when {
                    r.until != null -> EndsChoice.ON_DATE
                    r.count != null -> EndsChoice.AFTER
                    else -> EndsChoice.NEVER
                },
                until = r.until,
                count = r.count?.toString().orEmpty(),
                location = series.location.orEmpty(),
                mode = series.travelMode,
                reminder = series.reminderMinutes,
                note = series.note.orEmpty(),
            )
        }
    }
}

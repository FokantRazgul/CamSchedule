package app.camplanner.ui.today

import app.camplanner.domain.AlarmPlanner
import app.camplanner.domain.Almanac
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.Fitness
import app.camplanner.domain.TermCalendar
import app.camplanner.model.AppSettings
import app.camplanner.model.Assignment
import app.camplanner.model.BuiltInExercises
import app.camplanner.model.CalendarEvent
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseSet
import app.camplanner.model.GeoPoint
import app.camplanner.model.Subject
import app.camplanner.ui.common.ScheduleRow
import app.camplanner.ui.common.When
import app.camplanner.ui.common.scheduleRows
import app.camplanner.ui.format.Formats
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Hero(
    val now: ScheduleRow?,
    val nowProgress: Float,
    val next: ScheduleRow?,
    /** "in 25 min" or "at 14:00" */
    val nextWhen: String?,
    /** When nothing is left today: tomorrow's first event. */
    val tomorrowFirst: ScheduleRow?,
)

data class DueRow(val id: Long, val title: String, val detail: String, val overdue: Boolean, val done: Boolean)

enum class SetupStep(val title: String, val detail: String) {
    IMPORT("Import your timetable", "From an .ics file"),
    HOME("Set your home address", "For routes and leave alerts"),
    TERM("Set the term dates", "For week numbers and statistics"),
    PERMISSIONS("Allow alarms and notifications", "So the alarm can ring"),
}

data class TodayState(
    val date: LocalDate,
    val termLine: String?,
    val hero: Hero,
    val schedule: List<ScheduleRow>,
    /** Tomorrow's morning alarm (null when tomorrow has no events or alarms are off). */
    val alarmTime: LocalTime?,
    val alarmSkipped: Boolean,
    val alarmsEnabled: Boolean,
    val reviewTime: LocalTime?,
    val reviewDone: Boolean,
    val pushUps: Pair<Int, Int>?,
    val dueSoon: List<DueRow>,
    val setup: List<SetupStep>,
    val attention: String?,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val moonName: String,
    val moonIllumination: Float,
    val moonWaxing: Boolean,
)

/** Pure assembly of the Today screen; no Android types, so it is unit-tested. */
object TodayBuilder {

    fun build(
        now: LocalDateTime,
        zone: ZoneId,
        events: List<CalendarEvent>,
        subjects: List<Subject>,
        settings: AppSettings,
        locate: (String) -> GeoPoint?,
        assignments: List<Assignment>,
        exercises: List<Exercise>,
        sets: List<ExerciseSet>,
        reviewDone: Boolean,
        hasTimetable: Boolean,
        permissionsOk: Boolean,
        attention: String?,
    ): TodayState {
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val instant = now.atZone(zone).toInstant()
        val bySubject = subjects.associateBy { it.id }
        val rows = scheduleRows(today, events, bySubject, settings, locate, zone, instant)
        val active = rows.filter { !it.cancelled }

        val current = active.firstOrNull { it.state == When.NOW }
        val next = active.firstOrNull { it.state == When.LATER }
        val nextWhen = next?.let {
            val minutes = Duration.between(now.toLocalTime(), it.start).toMinutes()
            if (minutes < 180) Formats.inMinutes(minutes) else "at ${Formats.time(it.start)}"
        }
        val progress = current?.let {
            val total = Duration.between(it.start, it.end).toMinutes().coerceAtLeast(1)
            (Duration.between(it.start, now.toLocalTime()).toMinutes().toFloat() / total).coerceIn(0f, 1f)
        } ?: 0f
        val tomorrowRows = scheduleRows(tomorrow, events, bySubject, settings, locate, zone, instant).filter { !it.cancelled }

        val tomorrowEvents = DayPlanner.timedEventsOn(events, tomorrow, zone)
        val alarm = tomorrowEvents.firstOrNull()?.takeIf { settings.morningAlarmEnabled }?.let {
            AlarmPlanner.morningAlarmTime(it.start, settings.morningAlarmLeadMinutes).atZone(zone).toLocalTime()
        }
        val todayEvents = DayPlanner.timedEventsOn(events, today, zone)

        val upper = Fitness.progress(exercises, sets, today).firstOrNull { it.exercise.builtInKey == BuiltInExercises.PUSH_UPS }

        val horizon = today.plusDays(3)
        val due = assignments
            .filter {
                val d = it.deadline.atZone(zone).toLocalDate()
                (!it.done && !d.isAfter(horizon)) || (it.done && it.completedAt?.atZone(zone)?.toLocalDate() == today && !d.isAfter(horizon))
            }
            .sortedBy { it.deadline }
            .map { a ->
                val due = a.deadline.atZone(zone)
                val subject = a.subjectId?.let(bySubject::get)?.name
                val overdue = !a.done && a.deadline.isBefore(instant)
                DueRow(
                    a.id, a.title,
                    listOfNotNull(
                        (if (overdue) "Overdue · " else "") + "${Formats.dayLabel(due.toLocalDate(), today)} ${Formats.time(due.toLocalTime())}",
                        subject,
                    ).joinToString(" · "),
                    overdue, a.done,
                )
            }

        val setup = buildList {
            if (!hasTimetable) add(SetupStep.IMPORT)
            if (settings.home == null) add(SetupStep.HOME)
            if (settings.termStart == null) add(SetupStep.TERM)
            if (!permissionsOk) add(SetupStep.PERMISSIONS)
        }

        val sun = Almanac.sunTimes(today, Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
        val moon = Almanac.moon(today.atTime(21, 0).atZone(zone).toInstant())
        return TodayState(
            date = today,
            termLine = termLine(today, settings),
            hero = Hero(current, progress, next, nextWhen, if (current == null && next == null) tomorrowRows.firstOrNull() else null),
            schedule = rows,
            alarmTime = alarm,
            alarmSkipped = tomorrow in settings.skippedMornings,
            alarmsEnabled = settings.morningAlarmEnabled,
            reviewTime = if (settings.checkInEnabled) {
                AlarmPlanner.checkInTime(today, todayEvents, settings.checkInDelayMinutes, settings.checkInFallback, zone).atZone(zone).toLocalTime()
            } else null,
            reviewDone = reviewDone,
            pushUps = upper?.let { it.done to it.target },
            dueSoon = due,
            setup = setup,
            attention = attention,
            sunrise = sun.sunrise?.atZone(zone)?.toLocalTime(),
            sunset = sun.sunset?.atZone(zone)?.toLocalTime(),
            moonName = moon.phase.label,
            moonIllumination = moon.illumination.toFloat(),
            moonWaxing = moon.waxing,
        )
    }

    /** "Michaelmas · Week 2", "Vacation" outside the configured term, null if no term is set. */
    fun termLine(date: LocalDate, settings: AppSettings): String? {
        val start = settings.termStart ?: return null
        val week = TermCalendar.weekOf(date, start, settings.termEnd) ?: return "Vacation"
        return "${TermCalendar.termName(TermCalendar.termOf(start))} · Week $week"
    }
}

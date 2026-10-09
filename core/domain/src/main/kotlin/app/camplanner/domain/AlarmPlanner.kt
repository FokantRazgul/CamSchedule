package app.camplanner.domain

import app.camplanner.model.AppSettings
import app.camplanner.model.CalendarEvent
import app.camplanner.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class AlarmKind { MORNING, LEAVE, REMINDER, CHECK_IN, MIDDAY_NUDGE }

/**
 * An alarm the scheduler should have registered. [key] is stable for the same logical alarm, so a
 * recalculation can diff the new plan against what is already set.
 */
data class PlannedAlarm(
    val key: String,
    val kind: AlarmKind,
    val triggerAt: Instant,
    val date: LocalDate,
    val eventId: Long? = null,
)

/**
 * Pure alarm planning. Times relative to an event ("90 minutes before") are real elapsed time;
 * fixed clock times (check-in fallback, midday nudge) are local wall-clock times in [zone], so
 * both stay right across the Europe/London clock changes.
 */
object AlarmPlanner {

    val DEFAULT_HORIZON: Duration = Duration.ofHours(72)

    fun plan(
        events: List<CalendarEvent>,
        settings: AppSettings,
        zone: ZoneId,
        now: Instant,
        horizon: Duration = DEFAULT_HORIZON,
        locate: (String) -> GeoPoint?,
    ): List<PlannedAlarm> {
        val until = now + horizon
        // Start a day early: yesterday's late lecture can put its check-in after midnight.
        val firstDay = now.atZone(zone).toLocalDate().minusDays(1)
        val lastDay = until.atZone(zone).toLocalDate()
        val result = mutableListOf<PlannedAlarm>()
        var day = firstDay
        while (!day.isAfter(lastDay)) {
            result += planDay(day, events, settings, zone, locate)
            day = day.plusDays(1)
        }
        return result
            .filter { it.triggerAt.isAfter(now) && !it.triggerAt.isAfter(until) }
            .sortedWith(compareBy({ it.triggerAt }, { it.kind }))
    }

    fun planDay(
        date: LocalDate,
        events: List<CalendarEvent>,
        settings: AppSettings,
        zone: ZoneId,
        locate: (String) -> GeoPoint?,
    ): List<PlannedAlarm> {
        val dayEvents = DayPlanner.timedEventsOn(events, date, zone)
        val out = mutableListOf<PlannedAlarm>()

        if (settings.morningAlarmEnabled && dayEvents.isNotEmpty()) {
            out += PlannedAlarm(
                key = "MORNING:$date",
                kind = AlarmKind.MORNING,
                triggerAt = morningAlarmTime(dayEvents.first().start, settings.morningAlarmLeadMinutes),
                date = date,
                eventId = dayEvents.first().id,
            )
        }

        if (settings.leaveAlertEnabled) {
            val legs = DayPlanner.legs(
                dayEvents = dayEvents,
                home = settings.home,
                homeAddress = settings.homeAddress,
                defaultMode = settings.defaultTravelMode,
                buffer = Duration.ofMinutes(settings.bufferMinutes.toLong()),
                locate = locate,
            )
            legs.forEach { leg ->
                val leaveAt = leg.leaveAt ?: return@forEach
                out += PlannedAlarm("LEAVE:${leg.event.key}", AlarmKind.LEAVE, leaveAt, date, leg.event.id)
            }
        }

        if (settings.reminderEnabled) {
            dayEvents.forEach { event ->
                out += PlannedAlarm(
                    key = "REMINDER:${event.key}",
                    kind = AlarmKind.REMINDER,
                    triggerAt = event.start - Duration.ofMinutes(settings.reminderLeadMinutes.toLong()),
                    date = date,
                    eventId = event.id,
                )
            }
        }

        if (settings.checkInEnabled) {
            out += PlannedAlarm(
                key = "CHECK_IN:$date",
                kind = AlarmKind.CHECK_IN,
                triggerAt = checkInTime(date, dayEvents, settings.checkInDelayMinutes, settings.checkInFallback, zone),
                date = date,
            )
        }

        // Whether the nudge is actually shown is decided when it fires, from the logged sets.
        if (settings.middayNudgeEnabled && date.dayOfWeek !in settings.restDays) {
            out += PlannedAlarm(
                key = "MIDDAY_NUDGE:$date",
                kind = AlarmKind.MIDDAY_NUDGE,
                triggerAt = wallClock(date, settings.middayNudgeTime, zone),
                date = date,
            )
        }
        return out
    }

    fun morningAlarmTime(firstEventStart: Instant, leadMinutes: Int): Instant =
        firstEventStart - Duration.ofMinutes(leadMinutes.toLong())

    /** Last event end + delay, or the fallback clock time when the day has no timed events. */
    fun checkInTime(
        date: LocalDate,
        dayEvents: List<CalendarEvent>,
        delayMinutes: Int,
        fallback: LocalTime,
        zone: ZoneId,
    ): Instant {
        val lastEnd = dayEvents.maxOfOrNull { it.end }
        return lastEnd?.plus(Duration.ofMinutes(delayMinutes.toLong())) ?: wallClock(date, fallback, zone)
    }

    /** A local clock time on [date]. In a spring-forward gap it moves forward by the gap length. */
    fun wallClock(date: LocalDate, time: LocalTime, zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()
}

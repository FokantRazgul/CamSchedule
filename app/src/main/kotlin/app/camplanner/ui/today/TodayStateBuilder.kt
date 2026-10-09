package app.camplanner.ui.today

import app.camplanner.designsystem.components.StarState
import app.camplanner.domain.AlarmPlanner
import app.camplanner.domain.Almanac
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.Roman
import app.camplanner.domain.TermCalendar
import app.camplanner.model.AppSettings
import app.camplanner.model.CalendarEvent
import app.camplanner.model.GeoPoint
import app.camplanner.model.Subject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure assembly of the Today/day screen from data; no Android types. */
object TodayStateBuilder {

    /** Free time shorter than this isn't worth a line. */
    private const val MIN_GAP_MINUTES = 30L

    fun build(
        date: LocalDate,
        now: LocalDateTime,
        events: List<CalendarEvent>,
        subjects: List<Subject>,
        settings: AppSettings,
        locate: (String) -> GeoPoint?,
        zone: ZoneId,
        attention: String?,
    ): TodayUiState {
        val dayEvents = DayPlanner.timedEventsOn(events, date, zone)
        val legs = DayPlanner.legs(
            dayEvents, settings.home, settings.homeAddress, settings.defaultTravelMode,
            Duration.ofMinutes(settings.bufferMinutes.toLong()), locate,
        ).associateBy { it.event.id }
        val subjectById = subjects.associateBy { it.id }
        val nowInstant = now.atZone(zone).toInstant()
        val isToday = date == now.toLocalDate()

        val items = mutableListOf<TodayItem>()
        var nowPlaced = !isToday
        var previousEnd: java.time.Instant? = null
        dayEvents.forEach { e ->
            if (!nowPlaced && e.start.isAfter(nowInstant) && (previousEnd == null || !previousEnd!!.isAfter(nowInstant))) {
                if (previousEnd != null) items += TodayItem.Now("now", now.toLocalTime())
                nowPlaced = true
            }
            val gapStart = previousEnd
            if (gapStart != null) {
                val gap = Duration.between(gapStart, e.start).toMinutes()
                if (gap >= MIN_GAP_MINUTES && !(isToday && gapStart.isBefore(nowInstant) && e.start.isAfter(nowInstant))) {
                    items += TodayItem.Gap("gap-${e.key}", gap)
                }
            }
            val state = when {
                !e.end.isAfter(nowInstant) -> StarState.PAST
                !e.start.isAfter(nowInstant) -> StarState.CURRENT
                else -> StarState.FUTURE
            }
            val subject = e.subjectId?.let(subjectById::get)
            val leg = legs[e.id]
            items += TodayItem.Event(
                key = e.key,
                id = e.id,
                title = e.title,
                subject = subject?.name,
                subjectColor = subject?.colorIndex,
                start = e.start.atZone(zone).toLocalTime(),
                end = e.end.atZone(zone).toLocalTime(),
                location = e.location,
                state = state,
                mode = leg?.mode ?: settings.defaultTravelMode,
                travelMinutes = leg?.travel?.toMinutes()?.takeIf { it > 0 },
                leaveAt = leg?.leaveAt?.atZone(zone)?.toLocalTime(),
                note = e.note,
            )
            if (state == StarState.CURRENT && !nowPlaced) {
                items += TodayItem.Now("now", now.toLocalTime())
                nowPlaced = true
            }
            previousEnd = maxOf(previousEnd ?: e.end, e.end)
        }

        val sun = Almanac.sunTimes(date, Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
        val moon = Almanac.moon(date.atTime(21, 0).atZone(zone).toInstant())
        return TodayUiState(
            date = date,
            items = items,
            now = if (isToday) now.toLocalTime() else null,
            morningAlarm = dayEvents.firstOrNull()?.takeIf { settings.morningAlarmEnabled }?.let {
                AlarmPlanner.morningAlarmTime(it.start, settings.morningAlarmLeadMinutes).atZone(zone).toLocalTime()
            },
            checkIn = if (settings.checkInEnabled) {
                AlarmPlanner.checkInTime(date, dayEvents, settings.checkInDelayMinutes, settings.checkInFallback, zone).atZone(zone).toLocalTime()
            } else null,
            termLabel = termLabel(date, settings),
            almanac = AlmanacInfo(
                sunrise = sun.sunrise?.atZone(zone)?.toLocalTime(),
                sunset = sun.sunset?.atZone(zone)?.toLocalTime(),
                moonName = moon.phase.label,
                moonIllumination = moon.illumination.toFloat(),
                moonWaxing = moon.waxing,
            ),
            attention = attention,
        )
    }

    /** "Michaelmas Term · Week II", "Vacation" outside the configured term, null if no term is set. */
    fun termLabel(date: LocalDate, settings: AppSettings): String? {
        val start = settings.termStart ?: return null
        val week = TermCalendar.weekOf(date, start, settings.termEnd) ?: return "Vacation"
        val name = TermCalendar.termName(TermCalendar.termOf(start))
        return "$name Term  ·  Week ${Roman.of(week)}"
    }
}

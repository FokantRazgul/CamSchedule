package app.camplanner.data.repo

import app.camplanner.data.settings.SettingsStore
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.Leg
import app.camplanner.domain.RouteLinks
import java.time.Duration
import java.time.ZoneId

/** Works out how to reach an event from the timetable, settings and cached locations. */
class RouteService(
    private val timetable: TimetableRepository,
    private val locations: LocationRepository,
    private val settings: SettingsStore,
) {
    /** The leg for [eventId] within its day, or null if the event is gone or all-day. */
    suspend fun legFor(eventId: Long, zone: ZoneId = ZoneId.systemDefault()): Leg? {
        val event = timetable.event(eventId) ?: return null
        if (event.allDay) return null
        val date = event.start.atZone(zone).toLocalDate()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEvents = DayPlanner.timedEventsOn(timetable.between(dayStart, dayStart.plus(Duration.ofDays(1))), date, zone)
        val s = settings.current()
        val legs = DayPlanner.legs(
            dayEvents, s.home, s.homeAddress, s.defaultTravelMode, Duration.ofMinutes(s.bufferMinutes.toLong()), locations.locator(),
        )
        return legs.firstOrNull { it.event.id == eventId }
    }

    /** Google Maps directions URL for [eventId], or null if the event has no location. */
    suspend fun directionsUrl(eventId: Long): String? {
        val leg = legFor(eventId) ?: return null
        val destination = leg.destination ?: return null
        return RouteLinks.directionsUrl(leg.origin, destination, leg.mode)
    }
}

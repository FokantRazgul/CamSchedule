package app.camplanner.domain

import app.camplanner.model.CalendarEvent
import app.camplanner.model.GeoPoint
import app.camplanner.model.TravelMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How to get to one event: where from, how, and when to set off. */
data class Leg(
    val event: CalendarEvent,
    /** Where the route starts; null means "wherever the phone is". */
    val origin: Place?,
    val destination: Place?,
    val mode: TravelMode,
    /** Estimated travel time; null when either end has no coordinates. */
    val travel: Duration?,
    /** start − travel − buffer; null when there is nothing to travel (unknown, or same place). */
    val leaveAt: Instant?,
)

object DayPlanner {

    /** Timed (non all-day) events that start on [date] in [zone], earliest first. */
    fun timedEventsOn(events: List<CalendarEvent>, date: LocalDate, zone: ZoneId): List<CalendarEvent> =
        events
            .filter { !it.allDay && it.start.atZone(zone).toLocalDate() == date }
            .sortedWith(compareBy<CalendarEvent>({ it.start }, { it.end }, { it.id }))

    /**
     * Builds one [Leg] per event. The origin is the most recent earlier event of the day that has a
     * location, or home for the first event.
     *
     * @param dayEvents output of [timedEventsOn]
     * @param locate coordinates for a raw LOCATION string, or null if it hasn't been geocoded
     */
    fun legs(
        dayEvents: List<CalendarEvent>,
        home: GeoPoint?,
        homeAddress: String,
        defaultMode: TravelMode,
        buffer: Duration,
        locate: (String) -> GeoPoint?,
    ): List<Leg> {
        val homePlace: Place? = when {
            home != null -> Place.Coordinates(home)
            homeAddress.isNotBlank() -> Place.Address(homeAddress)
            else -> null
        }
        var previousLocation: String? = null
        return dayEvents.map { event ->
            val mode = event.travelModeOverride ?: defaultMode
            val destText = event.location?.takeIf { it.isNotBlank() }
            val destPoint = destText?.let(locate)
            val destination = destText?.let { text -> destPoint?.let { Place.Coordinates(it) } ?: Place.Address(text) }

            val originText = previousLocation
            val originPoint = if (originText != null) locate(originText) else home
            val origin = when {
                originText != null -> originPoint?.let { Place.Coordinates(it) } ?: Place.Address(originText)
                else -> homePlace
            }

            val samePlace = originText != null && destText != null && sameLocation(originText, destText)
            val travel = when {
                destText == null -> null
                samePlace -> Duration.ZERO
                originPoint != null && destPoint != null ->
                    TravelEstimator.travelTime(originPoint, destPoint, mode)
                else -> null
            }
            val leaveAt = if (travel != null && !travel.isZero) event.start - travel - buffer else null

            if (destText != null) previousLocation = destText
            Leg(event, origin, destination, mode, travel, leaveAt)
        }
    }

    fun sameLocation(a: String, b: String): Boolean = normalizeLocation(a) == normalizeLocation(b)

    /** Key used to cache geocoding results: case- and whitespace-insensitive. */
    fun normalizeLocation(text: String): String = text.trim().replace(Regex("\\s+"), " ").lowercase()
}

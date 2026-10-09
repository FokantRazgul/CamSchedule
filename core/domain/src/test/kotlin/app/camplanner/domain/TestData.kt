package app.camplanner.domain

import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSource
import app.camplanner.model.GeoPoint
import app.camplanner.model.TravelMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val LONDON: ZoneId = ZoneId.of("Europe/London")

fun london(text: String): Instant = LocalDateTime.parse(text).atZone(LONDON).toInstant()

fun utc(text: String): Instant = Instant.parse(text)

object Places {
    val HOME = GeoPoint(52.2070, 0.1170)
    val MILL_LANE = GeoPoint(52.2015, 0.1170)
    val CAVENDISH = GeoPoint(52.2097, 0.0921)
    val SIDGWICK = GeoPoint(52.2010, 0.1087)

    private val byName = mapOf(
        "Mill Lane Lecture Rooms" to MILL_LANE,
        "Cavendish Laboratory" to CAVENDISH,
        "Sidgwick Site" to SIDGWICK,
    )

    val locate: (String) -> GeoPoint? = { byName[it] }
}

private var nextId = 1L

fun event(
    start: Instant,
    minutes: Long = 60,
    location: String? = "Mill Lane Lecture Rooms",
    title: String = "Lecture",
    subjectId: Long? = null,
    mode: TravelMode? = null,
    allDay: Boolean = false,
): CalendarEvent {
    val id = nextId++
    return CalendarEvent(
        id = id,
        key = "test-$id",
        source = EventSource.MANUAL,
        title = title,
        subjectId = subjectId,
        start = start,
        end = start.plusSeconds(minutes * 60),
        allDay = allDay,
        location = location,
        travelModeOverride = mode,
    )
}

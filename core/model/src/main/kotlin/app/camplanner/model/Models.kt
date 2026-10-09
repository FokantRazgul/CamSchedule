package app.camplanner.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TravelMode { WALK, BIKE }

enum class EventSource { IMPORTED, MANUAL }

data class GeoPoint(val lat: Double, val lng: Double)

data class Subject(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val isAcademic: Boolean,
)

/**
 * One concrete occurrence of a calendar event. Recurring imported events are stored already expanded.
 *
 * [key] is stable across re-imports (`uid@originalStartEpochSecond` for imported events,
 * `manual-<id>` for manual ones) and is what per-event overrides hang off.
 */
data class CalendarEvent(
    val id: Long,
    val key: String,
    val source: EventSource,
    val title: String,
    val subjectId: Long?,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean = false,
    val location: String? = null,
    val note: String? = null,
    val travelModeOverride: TravelMode? = null,
)

data class Assignment(
    val id: Long,
    val title: String,
    val subjectId: Long?,
    val description: String,
    val deadline: Instant,
    val done: Boolean,
    val completedAt: Instant? = null,
)

enum class ExerciseUnit { REPS, SECONDS }

enum class ExerciseGroup { UPPER, ABS, LEGS, CUSTOM }

data class Exercise(
    val id: Long,
    /** Stable identifier for built-in exercises (e.g. "pushups"); null for custom ones. */
    val builtInKey: String?,
    val name: String,
    val unit: ExerciseUnit,
    val group: ExerciseGroup,
    val active: Boolean,
    val dailyTarget: Int,
    val sortOrder: Int,
)

data class ExerciseSet(
    val id: Long,
    val exerciseId: Long,
    val date: LocalDate,
    val amount: Int,
    val loggedAt: Instant,
)

data class RunLog(
    val id: Long,
    val date: LocalDate,
    val distanceMeters: Int,
    val durationSeconds: Int,
)

data class ReadingLog(
    val date: LocalDate,
    val subjectId: Long,
    val pages: Int,
)

data class AppSettings(
    val homeAddress: String = "",
    val home: GeoPoint? = null,
    val defaultTravelMode: TravelMode = TravelMode.WALK,
    val bufferMinutes: Int = 5,
    val morningAlarmEnabled: Boolean = true,
    val morningAlarmLeadMinutes: Int = 90,
    val snoozeMinutes: Int = 9,
    val leaveAlertEnabled: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderLeadMinutes: Int = 15,
    val checkInEnabled: Boolean = true,
    val checkInDelayMinutes: Int = 30,
    val checkInFallback: LocalTime = LocalTime.of(19, 0),
    val readingMinPages: Int = 20,
    val readingMaxPages: Int = 50,
    val weeklyRunTargetKm: Double = 15.0,
    val restDays: Set<DayOfWeek> = emptySet(),
    val middayNudgeEnabled: Boolean = true,
    val middayNudgeTime: LocalTime = LocalTime.of(13, 0),
    val termStart: LocalDate? = null,
    val termEnd: LocalDate? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
)

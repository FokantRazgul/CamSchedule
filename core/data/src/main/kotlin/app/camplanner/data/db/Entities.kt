package app.camplanner.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Storage conventions: instants are epoch milliseconds (UTC), calendar days are epoch days,
 * enums are stored by name.
 */

@Entity(tableName = "subject")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val isAcademic: Boolean = true,
)

/** Maps a normalised SUMMARY (see SubjectNames.key) to a subject, so renames and merges survive re-imports. */
@Entity(tableName = "subject_alias")
data class SubjectAliasEntity(
    @PrimaryKey val summaryKey: String,
    val subjectId: Long,
)

@Entity(
    tableName = "event",
    indices = [Index("startUtc"), Index("source"), Index(value = ["eventKey"], unique = true)],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventKey: String,
    val source: String,
    val title: String,
    val subjectId: Long?,
    val startUtc: Long,
    val endUtc: Long,
    val allDay: Boolean,
    val location: String?,
    val note: String?,
)

/** Per-event choices that must outlive a re-import, keyed by the stable event key. */
@Entity(tableName = "event_override")
data class EventOverrideEntity(
    @PrimaryKey val eventKey: String,
    val travelMode: String?,
)

data class EventWithOverride(
    @Embedded val event: EventEntity,
    val overrideMode: String?,
)

@Entity(tableName = "location")
data class LocationEntity(
    /** DayPlanner.normalizeLocation(rawText) */
    @PrimaryKey val key: String,
    val rawText: String,
    val lat: Double?,
    val lng: Double?,
    val status: String,
    /** The geocoder's address line, or the query used, for display. */
    val label: String?,
    val updatedAtUtc: Long,
)

enum class LocationStatus { PENDING, GEOCODED, MANUAL, NOT_FOUND }

@Entity(tableName = "assignment", indices = [Index("deadlineUtc")])
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long?,
    val description: String,
    val deadlineUtc: Long,
    val done: Boolean,
    val completedAtUtc: Long?,
)

@Entity(tableName = "reading_log", primaryKeys = ["epochDay", "subjectId"])
data class ReadingLogEntity(
    val epochDay: Long,
    val subjectId: Long,
    val pages: Int,
)

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val builtInKey: String?,
    val name: String,
    val unit: String,
    val exerciseGroup: String,
    val active: Boolean,
    val dailyTarget: Int,
    val sortOrder: Int,
)

@Entity(tableName = "exercise_set", indices = [Index("epochDay"), Index("exerciseId")])
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val epochDay: Long,
    val amount: Int,
    val loggedAtUtc: Long,
)

@Entity(tableName = "run_log", indices = [Index("epochDay")])
data class RunLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val distanceMeters: Int,
    val durationSeconds: Int,
)

@Entity(tableName = "checkin")
data class CheckInEntity(
    @PrimaryKey val epochDay: Long,
    val completedAtUtc: Long,
)

/** What is currently registered with AlarmManager, so a recalculation can diff against it. */
@Entity(tableName = "scheduled_alarm")
data class ScheduledAlarmEntity(
    @PrimaryKey val key: String,
    val kind: String,
    val triggerAtUtc: Long,
    val eventId: Long?,
)

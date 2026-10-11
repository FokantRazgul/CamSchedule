package app.camplanner.data.repo

import app.camplanner.data.db.AssignmentEntity
import app.camplanner.data.db.EventWithOverride
import app.camplanner.data.db.ExerciseEntity
import app.camplanner.data.db.ExerciseSetEntity
import app.camplanner.data.db.ReadingLogEntity
import app.camplanner.data.db.RunLogEntity
import app.camplanner.data.db.SeriesEntity
import app.camplanner.data.db.SubjectEntity
import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSeries
import app.camplanner.model.EventSource
import app.camplanner.model.Frequency
import app.camplanner.model.RepeatRule
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.ReadingLog
import app.camplanner.model.RunLog
import app.camplanner.model.Subject
import app.camplanner.model.TravelMode
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

fun SubjectEntity.toModel() = Subject(id, name, colorIndex, isAcademic)

/** The event as the user sees it: per-occurrence overrides applied on top of the stored row. */
fun EventWithOverride.toModel() = CalendarEvent(
    id = event.id,
    key = event.eventKey,
    source = enumOrNull<EventSource>(event.source) ?: EventSource.MANUAL,
    title = event.title,
    subjectId = event.subjectId,
    start = Instant.ofEpochMilli(overrideStart ?: event.startUtc),
    end = Instant.ofEpochMilli(overrideEnd ?: event.endUtc),
    allDay = event.allDay,
    location = overrideLocation ?: event.location,
    note = event.note,
    travelModeOverride = enumOrNull<TravelMode>(overrideMode) ?: enumOrNull<TravelMode>(event.travelMode),
    reminderMinutes = overrideReminder ?: event.reminderMinutes,
    cancelled = overrideCancelled ?: false,
    seriesId = event.seriesId,
)

fun SeriesEntity.toModel() = EventSeries(
    id = id,
    title = title,
    subjectId = subjectId,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    startTime = LocalTime.ofSecondOfDay(startMinute * 60L),
    endTime = LocalTime.ofSecondOfDay(endMinute * 60L),
    rule = RepeatRule(
        frequency = enumOrNull<Frequency>(frequency) ?: Frequency.WEEKLY,
        interval = interval,
        days = DayOfWeek.entries.filter { daysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
        until = untilEpochDay?.let(LocalDate::ofEpochDay),
        count = count,
    ),
    location = location,
    note = note,
    travelMode = enumOrNull<TravelMode>(travelMode),
    reminderMinutes = reminderMinutes,
    exceptions = exceptions.split(',').mapNotNull { it.trim().toLongOrNull() }.map(LocalDate::ofEpochDay).toSet(),
)

fun EventSeries.toEntity() = SeriesEntity(
    id = id,
    title = title.trim(),
    subjectId = subjectId,
    startEpochDay = startDate.toEpochDay(),
    startMinute = startTime.toSecondOfDay() / 60,
    endMinute = endTime.toSecondOfDay() / 60,
    frequency = rule.frequency.name,
    interval = rule.interval,
    daysMask = rule.days.fold(0) { m, d -> m or (1 shl (d.value - 1)) },
    untilEpochDay = rule.until?.toEpochDay(),
    count = rule.count,
    location = location?.trim()?.takeIf { it.isNotEmpty() },
    note = note?.trim()?.takeIf { it.isNotEmpty() },
    travelMode = travelMode?.name,
    reminderMinutes = reminderMinutes,
    exceptions = exceptions.map { it.toEpochDay() }.sorted().joinToString(","),
)

fun AssignmentEntity.toModel() = Assignment(
    id = id,
    title = title,
    subjectId = subjectId,
    description = description,
    deadline = Instant.ofEpochMilli(deadlineUtc),
    done = done,
    completedAt = completedAtUtc?.let(Instant::ofEpochMilli),
)

fun ReadingLogEntity.toModel() = ReadingLog(LocalDate.ofEpochDay(epochDay), subjectId, pages)

fun ExerciseEntity.toModel() = Exercise(
    id = id,
    builtInKey = builtInKey,
    name = name,
    unit = enumOrNull<ExerciseUnit>(unit) ?: ExerciseUnit.REPS,
    group = enumOrNull<ExerciseGroup>(exerciseGroup) ?: ExerciseGroup.CUSTOM,
    active = active,
    dailyTarget = dailyTarget,
    sortOrder = sortOrder,
)

fun Exercise.toEntity() = ExerciseEntity(id, builtInKey, name, unit.name, group.name, active, dailyTarget, sortOrder)

fun ExerciseSetEntity.toModel() = ExerciseSet(id, exerciseId, LocalDate.ofEpochDay(epochDay), amount, Instant.ofEpochMilli(loggedAtUtc))

fun RunLogEntity.toModel() = RunLog(id, LocalDate.ofEpochDay(epochDay), distanceMeters, durationSeconds)

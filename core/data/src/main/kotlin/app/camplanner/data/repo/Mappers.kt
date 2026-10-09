package app.camplanner.data.repo

import app.camplanner.data.db.AssignmentEntity
import app.camplanner.data.db.EventWithOverride
import app.camplanner.data.db.ExerciseEntity
import app.camplanner.data.db.ExerciseSetEntity
import app.camplanner.data.db.ReadingLogEntity
import app.camplanner.data.db.RunLogEntity
import app.camplanner.data.db.SubjectEntity
import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSource
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.ReadingLog
import app.camplanner.model.RunLog
import app.camplanner.model.Subject
import app.camplanner.model.TravelMode
import java.time.Instant
import java.time.LocalDate

internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

fun SubjectEntity.toModel() = Subject(id, name, colorIndex, isAcademic)

fun EventWithOverride.toModel() = CalendarEvent(
    id = event.id,
    key = event.eventKey,
    source = enumOrNull<EventSource>(event.source) ?: EventSource.MANUAL,
    title = event.title,
    subjectId = event.subjectId,
    start = Instant.ofEpochMilli(event.startUtc),
    end = Instant.ofEpochMilli(event.endUtc),
    allDay = event.allDay,
    location = event.location,
    note = event.note,
    travelModeOverride = enumOrNull<TravelMode>(overrideMode),
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

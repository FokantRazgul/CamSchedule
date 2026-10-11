package app.camplanner.data.repo

import androidx.room.withTransaction
import app.camplanner.data.db.CamPlannerDatabase
import app.camplanner.data.db.EventEntity
import app.camplanner.data.db.EventOverrideEntity
import app.camplanner.data.db.LocationEntity
import app.camplanner.data.db.LocationStatus
import app.camplanner.data.db.ReadingLogEntity
import app.camplanner.data.db.SeriesEntity
import app.camplanner.data.db.SubjectAliasEntity
import app.camplanner.data.db.SubjectEntity
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.Recurrence
import app.camplanner.domain.SubjectNames
import app.camplanner.ics.IcsImporter
import app.camplanner.ics.ImportResult
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSeries
import app.camplanner.model.EventSource
import app.camplanner.model.RepeatRule
import app.camplanner.model.Subject
import app.camplanner.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class ImportSummary(
    val events: Int,
    val newSubjects: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    val warnings: List<String>,
)

/** Which occurrences an edit or delete applies to. */
enum class Scope { THIS, ALL }

/** Everything the event editor can set. [repeat] null means a one-off event. */
data class EventDraft(
    val title: String,
    val subjectId: Long?,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val repeat: RepeatRule?,
    val location: String?,
    val note: String?,
    val travelMode: TravelMode?,
    val reminderMinutes: Int?,
)

/** What a delete removed, so Undo can put it back exactly. */
sealed interface EventUndo {
    data class Single(val event: EventEntity, val override: EventOverrideEntity?) : EventUndo
    data class Occurrence(val seriesId: Long, val date: LocalDate) : EventUndo
    data class Series(val series: SeriesEntity) : EventUndo
    data class Uncancel(val eventKey: String) : EventUndo
}

/**
 * Timetable, user-made events (one-off and repeating), subjects and per-occurrence overrides.
 * Anything that can move an alarm calls [onScheduleChanged].
 */
class TimetableRepository(
    private val db: CamPlannerDatabase,
    private val onScheduleChanged: () -> Unit,
    private val onLocationsAdded: () -> Unit,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val events = db.events()
    private val subjects = db.subjects()
    private val seriesDao = db.series()

    // --- Reading ------------------------------------------------------------------------------

    fun observeSubjects(): Flow<List<Subject>> = subjects.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun subjects(): List<Subject> = subjects.all().map { it.toModel() }

    /** Events overlapping the local days [from]..[to] inclusive, cancelled ones included. */
    fun observeDays(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<CalendarEvent>> =
        events.observeBetween(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        ).map { rows -> rows.map { it.toModel() } }

    suspend fun between(from: Instant, to: Instant): List<CalendarEvent> =
        events.between(from.toEpochMilli(), to.toEpochMilli()).map { it.toModel() }

    fun observeEvent(id: Long): Flow<CalendarEvent?> = events.observe(id).map { it?.toModel() }

    suspend fun event(id: Long): CalendarEvent? = events.get(id)?.toModel()

    suspend fun allTimedEvents(): List<CalendarEvent> = events.allTimed().map { it.toModel() }

    fun observeImportedCount(): Flow<Int> = events.observeImportedCount()

    suspend fun series(id: Long): EventSeries? = seriesDao.get(id)?.toModel()

    // --- Creating and editing user events -------------------------------------------------------

    /** Creates a new one-off or repeating event; returns the id of its first occurrence row, if any. */
    suspend fun create(draft: EventDraft) {
        db.withTransaction {
            if (draft.repeat == null) insertSingle(draft, key = "manual-${UUID.randomUUID()}")
            else {
                val id = seriesDao.upsert(seriesFrom(0, draft, emptySet()))
                regenerate(id)
            }
        }
        afterEdit(draft.location)
    }

    /**
     * Applies [draft] to the event [eventId]. For a repeating event, [Scope.THIS] detaches that
     * one occurrence and [Scope.ALL] rewrites the series. Imported lectures only take per-
     * occurrence changes (time, place, travel, reminder), kept across re-imports.
     */
    suspend fun update(eventId: Long, draft: EventDraft, scope: Scope) {
        val row = events.raw(eventId) ?: return
        db.withTransaction {
            when {
                row.source == EventSource.IMPORTED.name -> overrideImported(row, draft)
                row.seriesId != null && scope == Scope.ALL -> updateSeries(row.seriesId, draft)
                row.seriesId != null -> {
                    addException(row.seriesId, row.startDate())
                    events.update(row.copy(seriesId = null).applyDraft(draft))
                }
                draft.repeat != null -> {
                    // A one-off event that now repeats becomes a series starting on its date.
                    events.delete(row.id)
                    regenerate(seriesDao.upsert(seriesFrom(0, draft, emptySet())))
                }
                else -> events.update(row.applyDraft(draft))
            }
        }
        afterEdit(draft.location)
    }

    private suspend fun updateSeries(seriesId: Long, draft: EventDraft) {
        val old = seriesDao.get(seriesId)?.toModel() ?: return
        if (draft.repeat == null) {
            // "Doesn't repeat" for the whole series: keep a single event on the chosen date.
            events.deleteSeriesOccurrences(seriesId)
            seriesDao.delete(seriesId)
            insertSingle(draft, key = "manual-${UUID.randomUUID()}")
        } else {
            seriesDao.upsert(seriesFrom(seriesId, draft, old.exceptions))
            regenerate(seriesId)
        }
    }

    private suspend fun overrideImported(row: EventEntity, draft: EventDraft) {
        val z = zone()
        val start = draft.date.atTime(draft.start).atZone(z).toInstant().toEpochMilli()
        val endDate = if (draft.end.isAfter(draft.start)) draft.date else draft.date.plusDays(1)
        val end = endDate.atTime(draft.end).atZone(z).toInstant().toEpochMilli()
        val old = events.override(row.eventKey) ?: EventOverrideEntity(row.eventKey, null)
        events.upsertOverride(
            old.copy(
                travelMode = draft.travelMode?.name,
                reminderMinutes = draft.reminderMinutes,
                location = draft.location?.trim()?.takeIf { it.isNotEmpty() && it != row.location },
                startUtc = start.takeIf { it != row.startUtc },
                endUtc = end.takeIf { it != row.endUtc },
            ),
        )
    }

    private suspend fun insertSingle(d: EventDraft, key: String) {
        val z = zone()
        val endDate = if (d.end.isAfter(d.start)) d.date else d.date.plusDays(1)
        events.insert(
            EventEntity(
                eventKey = key, source = EventSource.MANUAL.name, title = d.title.trim(),
                subjectId = d.subjectId ?: subjectFor(d.title),
                startUtc = d.date.atTime(d.start).atZone(z).toInstant().toEpochMilli(),
                endUtc = endDate.atTime(d.end).atZone(z).toInstant().toEpochMilli(),
                allDay = false, location = d.location?.trim()?.takeIf { it.isNotEmpty() },
                note = d.note?.trim()?.takeIf { it.isNotEmpty() },
                reminderMinutes = d.reminderMinutes, travelMode = d.travelMode?.name,
            ),
        )
    }

    private fun EventEntity.startDate(): LocalDate = Instant.ofEpochMilli(startUtc).atZone(zone()).toLocalDate()

    private suspend fun EventEntity.applyDraft(d: EventDraft): EventEntity {
        val z = zone()
        val endDate = if (d.end.isAfter(d.start)) d.date else d.date.plusDays(1)
        return copy(
            title = d.title.trim(),
            subjectId = d.subjectId ?: subjectFor(d.title),
            startUtc = d.date.atTime(d.start).atZone(z).toInstant().toEpochMilli(),
            endUtc = endDate.atTime(d.end).atZone(z).toInstant().toEpochMilli(),
            location = d.location?.trim()?.takeIf { it.isNotEmpty() },
            note = d.note?.trim()?.takeIf { it.isNotEmpty() },
            reminderMinutes = d.reminderMinutes,
            travelMode = d.travelMode?.name,
        )
    }

    private fun seriesFrom(id: Long, d: EventDraft, exceptions: Set<LocalDate>) = EventSeries(
        id = id, title = d.title, subjectId = d.subjectId, startDate = d.date, startTime = d.start, endTime = d.end,
        rule = d.repeat!!, location = d.location, note = d.note, travelMode = d.travelMode,
        reminderMinutes = d.reminderMinutes, exceptions = exceptions,
    ).toEntity()

    /** Rewrites the occurrence rows of one series, about 13 months ahead for open-ended rules. */
    private suspend fun regenerate(seriesId: Long) {
        val series = seriesDao.get(seriesId)?.toModel() ?: return
        events.deleteSeriesOccurrences(seriesId)
        val z = zone()
        val to = maxOf(LocalDate.now(z), series.startDate).plusDays(SERIES_HORIZON_DAYS)
        val subjectId = series.subjectId ?: subjectFor(series.title)
        events.insertAll(
            Recurrence.occurrences(series, series.startDate, to, z).map { o ->
                EventEntity(
                    eventKey = "series-$seriesId@${o.date.toEpochDay()}", source = EventSource.MANUAL.name,
                    title = series.title, subjectId = subjectId, startUtc = o.start.toEpochMilli(), endUtc = o.end.toEpochMilli(),
                    allDay = false, location = series.location, note = series.note, seriesId = seriesId,
                    reminderMinutes = series.reminderMinutes, travelMode = series.travelMode?.name,
                )
            },
        )
    }

    /** Keeps open-ended series filled ahead; cheap enough to run at every start. */
    suspend fun regenerateAllSeries() {
        db.withTransaction { seriesDao.all().forEach { regenerate(it.id) } }
        onScheduleChanged()
    }

    private suspend fun addException(seriesId: Long, date: LocalDate) {
        val s = seriesDao.get(seriesId)?.toModel() ?: return
        seriesDao.upsert(s.copy(exceptions = s.exceptions + date).toEntity())
    }

    private suspend fun afterEdit(location: String?) {
        val clean = location?.trim()?.takeIf { it.isNotEmpty() }
        if (clean != null && addLocations(listOf(clean))) onLocationsAdded()
        onScheduleChanged()
    }

    // --- Deleting, cancelling, undo -----------------------------------------------------------

    /**
     * Deletes a user event (one occurrence or the whole series) or, for an imported lecture,
     * cancels that occurrence. Returns what Undo needs.
     */
    suspend fun delete(eventId: Long, scope: Scope): EventUndo? {
        val row = events.raw(eventId) ?: return null
        val undo = db.withTransaction {
            when {
                row.source == EventSource.IMPORTED.name -> {
                    setCancelled(row.eventKey, true)
                    EventUndo.Uncancel(row.eventKey)
                }
                row.seriesId != null && scope == Scope.ALL -> {
                    val series = seriesDao.get(row.seriesId) ?: return@withTransaction null
                    events.deleteSeriesOccurrences(row.seriesId)
                    seriesDao.delete(row.seriesId)
                    EventUndo.Series(series)
                }
                row.seriesId != null -> {
                    addException(row.seriesId, row.startDate())
                    events.delete(row.id)
                    EventUndo.Occurrence(row.seriesId, row.startDate())
                }
                else -> {
                    val override = events.override(row.eventKey)
                    events.delete(row.id)
                    EventUndo.Single(row, override)
                }
            }
        }
        onScheduleChanged()
        return undo
    }

    suspend fun undo(token: EventUndo) {
        db.withTransaction {
            when (token) {
                is EventUndo.Single -> {
                    events.insert(token.event)
                    token.override?.let { events.upsertOverride(it) }
                }
                is EventUndo.Occurrence -> {
                    val s = seriesDao.get(token.seriesId)?.toModel() ?: return@withTransaction
                    seriesDao.upsert(s.copy(exceptions = s.exceptions - token.date).toEntity())
                    regenerate(token.seriesId)
                }
                is EventUndo.Series -> {
                    seriesDao.upsert(token.series)
                    regenerate(token.series.id)
                }
                is EventUndo.Uncancel -> setCancelled(token.eventKey, false)
            }
        }
        onScheduleChanged()
    }

    /** Cancel or restore a single occurrence (imported lectures). */
    suspend fun setCancelled(eventKey: String, cancelled: Boolean) {
        val old = events.override(eventKey) ?: EventOverrideEntity(eventKey, null)
        events.upsertOverride(old.copy(cancelled = cancelled))
        onScheduleChanged()
    }

    suspend fun setTravelMode(eventKey: String, mode: TravelMode?) {
        val old = events.override(eventKey) ?: EventOverrideEntity(eventKey, null)
        events.upsertOverride(old.copy(travelMode = mode?.name))
        onScheduleChanged()
    }

    /** Drops every per-occurrence change to an imported lecture. */
    suspend fun resetOverride(eventKey: String) {
        events.deleteOverride(eventKey)
        onScheduleChanged()
    }

    /** A user event gets the subject its title would get on import, if one exists. */
    private suspend fun subjectFor(title: String): Long? {
        val key = SubjectNames.key(title)
        return subjects.aliases().firstOrNull { it.summaryKey == key }?.subjectId
    }

    // --- Import -------------------------------------------------------------------------------

    /** Parses without touching the database, for the preview. */
    suspend fun parse(input: InputStream, horizon: Instant): ImportResult = withContext(Dispatchers.IO) {
        input.use { IcsImporter().parse(it, horizon) }
    }

    /** Replaces every imported event with [result]; user events, renames, merges and per-lecture changes are kept. */
    suspend fun replaceImported(result: ImportResult, zone: ZoneId): ImportSummary {
        val newSubjectCount = db.withTransaction {
            val aliases = subjects.aliases().associate { it.summaryKey to it.subjectId }.toMutableMap()
            val locationKeys = db.locations().all().map { it.key }.toSet()
            val plan = TimetableImport.plan(result.events, aliases.keys, locationKeys)
            var color = subjects.maxColorIndex() + 1
            plan.newSubjects.forEach { ns ->
                val id = subjects.insert(SubjectEntity(name = ns.name, colorIndex = color++, isAcademic = true))
                subjects.upsertAlias(SubjectAliasEntity(ns.key, id))
                aliases[ns.key] = id
            }
            events.deleteImported()
            events.insertAll(
                plan.events.distinctBy { it.first.key }.map { (e, key) ->
                    EventEntity(
                        eventKey = e.key, source = EventSource.IMPORTED.name, title = e.summary, subjectId = aliases[key],
                        startUtc = e.start.toEpochMilli(), endUtc = e.end.toEpochMilli(), allDay = e.allDay,
                        location = e.location, note = e.description,
                    )
                },
            )
            insertLocations(plan.locations)
            plan.newSubjects.size
        }
        onLocationsAdded()
        onScheduleChanged()
        val days = result.events.filter { !it.allDay }.map { it.start.atZone(zone).toLocalDate() }
        return ImportSummary(result.events.size, newSubjectCount, days.minOrNull(), days.maxOrNull(), result.warnings)
    }

    private suspend fun addLocations(raw: List<String>): Boolean {
        val known = db.locations().all().map { it.key }.toSet()
        val fresh = raw.filter { DayPlanner.normalizeLocation(it) !in known }
        insertLocations(fresh)
        return fresh.isNotEmpty()
    }

    private suspend fun insertLocations(raw: List<String>) {
        val now = System.currentTimeMillis()
        raw.forEach {
            db.locations().insertIfAbsent(
                LocationEntity(DayPlanner.normalizeLocation(it), it.trim(), null, null, LocationStatus.PENDING.name, null, now),
            )
        }
    }

    // --- Subjects -----------------------------------------------------------------------------

    suspend fun addSubject(name: String, isAcademic: Boolean): Long =
        subjects.insert(SubjectEntity(name = name.trim(), colorIndex = subjects.maxColorIndex() + 1, isAcademic = isAcademic))

    suspend fun updateSubject(subject: Subject) {
        subjects.update(SubjectEntity(subject.id, subject.name.trim(), subject.colorIndex, subject.isAcademic))
    }

    /** Removes a subject; its events and homework stay, without a subject, and its reading log goes. */
    suspend fun deleteSubject(id: Long) {
        db.withTransaction {
            events.clearSubject(id)
            seriesDao.repointSubject(id, null)
            db.assignments().clearSubject(id)
            db.reading().deleteSubject(id)
            subjects.deleteAliases(id)
            subjects.delete(id)
        }
    }

    /** Moves everything from [from] into [into] (events, aliases, homework, reading) and deletes [from]. */
    suspend fun mergeSubjects(from: Long, into: Long) {
        if (from == into) return
        db.withTransaction {
            subjects.repointAliases(from, into)
            events.repointSubject(from, into)
            seriesDao.repointSubject(from, into)
            db.assignments().repointSubject(from, into)
            val reading = db.reading()
            reading.forSubject(from).forEach { log ->
                val existing = reading.get(log.epochDay, into)
                reading.upsert(ReadingLogEntity(log.epochDay, into, log.pages + (existing?.pages ?: 0)))
                reading.delete(log.epochDay, from)
            }
            subjects.delete(from)
        }
    }

    companion object {
        const val SERIES_HORIZON_DAYS = 400L
    }
}

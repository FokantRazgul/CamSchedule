package app.camplanner.data.repo

import androidx.room.withTransaction
import app.camplanner.data.db.CamPlannerDatabase
import app.camplanner.data.db.EventEntity
import app.camplanner.data.db.EventOverrideEntity
import app.camplanner.data.db.LocationEntity
import app.camplanner.data.db.LocationStatus
import app.camplanner.data.db.ReadingLogEntity
import app.camplanner.data.db.SubjectAliasEntity
import app.camplanner.data.db.SubjectEntity
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.SubjectNames
import app.camplanner.ics.IcsImporter
import app.camplanner.ics.ImportResult
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSource
import app.camplanner.model.Subject
import app.camplanner.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ImportSummary(
    val events: Int,
    val newSubjects: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    val warnings: List<String>,
)

/**
 * Timetable, subjects and per-event overrides. Anything that can move an alarm calls
 * [onScheduleChanged] so the alarm plan is recalculated.
 */
class TimetableRepository(
    private val db: CamPlannerDatabase,
    private val onScheduleChanged: () -> Unit,
    private val onLocationsAdded: () -> Unit,
) {
    private val events = db.events()
    private val subjects = db.subjects()

    fun observeSubjects(): Flow<List<Subject>> = subjects.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun subjects(): List<Subject> = subjects.all().map { it.toModel() }

    /** Events overlapping the local days [from]..[to] inclusive. */
    fun observeDays(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<CalendarEvent>> =
        events.observeBetween(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        ).map { rows -> rows.map { it.toModel() } }

    suspend fun between(from: Instant, to: Instant): List<CalendarEvent> =
        events.between(from.toEpochMilli(), to.toEpochMilli()).map { it.toModel() }

    fun observeEvent(id: Long): Flow<CalendarEvent?> = events.observe(id).map { it?.toModel() }

    suspend fun event(id: Long): CalendarEvent? = events.get(id)?.toModel()

    suspend fun allTimedEvents(): List<CalendarEvent> = events.allTimed().map { e ->
        app.camplanner.data.db.EventWithOverride(e, null).toModel()
    }

    fun observeImportedCount(): Flow<Int> = events.observeImportedCount()

    // --- Manual events ------------------------------------------------------------------------

    suspend fun saveManual(
        id: Long?,
        title: String,
        start: Instant,
        end: Instant,
        location: String?,
        note: String?,
    ): Long {
        val cleanLocation = location?.trim()?.takeIf { it.isNotEmpty() }
        val subjectId = subjectFor(title)
        val newId = db.withTransaction {
            val existing = id?.let { events.get(it)?.event }
            val entity = EventEntity(
                id = existing?.id ?: 0,
                eventKey = existing?.eventKey ?: "manual-${UUID.randomUUID()}",
                source = EventSource.MANUAL.name,
                title = title.trim(),
                subjectId = subjectId,
                startUtc = start.toEpochMilli(),
                endUtc = end.toEpochMilli(),
                allDay = false,
                location = cleanLocation,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            )
            if (existing == null) events.insert(entity) else { events.update(entity); entity.id }
        }
        if (cleanLocation != null && addLocations(listOf(cleanLocation))) onLocationsAdded()
        onScheduleChanged()
        return newId
    }

    suspend fun deleteManual(id: Long) {
        val e = events.get(id)?.event ?: return
        if (e.source != EventSource.MANUAL.name) return
        events.delete(id)
        onScheduleChanged()
    }

    suspend fun setTravelMode(eventKey: String, mode: TravelMode?) {
        events.upsertOverride(EventOverrideEntity(eventKey, mode?.name))
        onScheduleChanged()
    }

    /** A manual event gets the subject its title would get on import, if one exists. */
    private suspend fun subjectFor(title: String): Long? {
        val key = SubjectNames.key(title)
        return subjects.aliases().firstOrNull { it.summaryKey == key }?.subjectId
    }

    // --- Import -------------------------------------------------------------------------------

    /** Parses without touching the database, for the preview. */
    suspend fun parse(input: InputStream, horizon: Instant): ImportResult = withContext(Dispatchers.IO) {
        input.use { IcsImporter().parse(it, horizon) }
    }

    /** Replaces every imported event with [result]; manual events, renames and merges are kept. */
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
                        eventKey = e.key,
                        source = EventSource.IMPORTED.name,
                        title = e.summary,
                        subjectId = aliases[key],
                        startUtc = e.start.toEpochMilli(),
                        endUtc = e.end.toEpochMilli(),
                        allDay = e.allDay,
                        location = e.location,
                        note = e.description,
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

    suspend fun updateSubject(subject: Subject) {
        subjects.update(SubjectEntity(subject.id, subject.name.trim(), subject.colorIndex, subject.isAcademic))
    }

    /** Moves everything from [from] into [into] (events, aliases, homework, reading) and deletes [from]. */
    suspend fun mergeSubjects(from: Long, into: Long) {
        if (from == into) return
        db.withTransaction {
            subjects.repointAliases(from, into)
            events.repointSubject(from, into)
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
}

package app.camplanner.data.repo

import app.camplanner.domain.DayPlanner
import app.camplanner.domain.SubjectNames
import app.camplanner.ics.ImportedEvent

/** A subject the import has to create, identified by its alias key. */
data class NewSubject(val key: String, val name: String)

data class ImportPlan(
    val newSubjects: List<NewSubject>,
    /** Each imported occurrence with the alias key of its subject. */
    val events: List<Pair<ImportedEvent, String>>,
    /** Raw LOCATION strings, one per normalised key, that need a location row. */
    val locations: List<String>,
)

/**
 * Pure planning for a re-import: which subjects are new (everything else maps through the alias
 * table, so renames and merges are kept) and which locations need geocoding.
 */
object TimetableImport {
    fun plan(imported: List<ImportedEvent>, existingAliasKeys: Set<String>, existingLocationKeys: Set<String>): ImportPlan {
        val events = imported.map { it to SubjectNames.key(it.summary) }
        val newSubjects = events
            .filter { (_, key) -> key !in existingAliasKeys }
            .distinctBy { (_, key) -> key }
            .map { (event, key) -> NewSubject(key, SubjectNames.displayName(event.summary)) }
        val locations = imported.mapNotNull { it.location }
            .distinctBy(DayPlanner::normalizeLocation)
            .filter { DayPlanner.normalizeLocation(it) !in existingLocationKeys }
        return ImportPlan(newSubjects, events, locations)
    }
}

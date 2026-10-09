package app.camplanner.data

import app.camplanner.data.repo.NewSubject
import app.camplanner.data.repo.TimetableImport
import app.camplanner.ics.ImportedEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimetableImportTest {

    private fun ev(summary: String, location: String? = null) = ImportedEvent(
        uid = summary, originalStart = Instant.EPOCH, summary = summary, description = null, location = location,
        start = Instant.EPOCH, end = Instant.EPOCH, allDay = false, timeZoneId = null,
    )

    @Test
    fun `new subjects are created once per normalised summary`() {
        val plan = TimetableImport.plan(
            listOf(ev("Analysis I - Lecture 1"), ev("Analysis I - Lecture 2"), ev("Analysis I (Supervision)"), ev("Physics Practical")),
            existingAliasKeys = emptySet(),
            existingLocationKeys = emptySet(),
        )
        assertEquals(listOf(NewSubject("analysis i", "Analysis I"), NewSubject("physics", "Physics")), plan.newSubjects)
        assertEquals(listOf("analysis i", "analysis i", "analysis i", "physics"), plan.events.map { it.second })
    }

    @Test
    fun `existing aliases are reused so renames and merges survive a re-import`() {
        val plan = TimetableImport.plan(
            listOf(ev("Analysis I - Lecture 1"), ev("Probability")),
            existingAliasKeys = setOf("analysis i"),
            existingLocationKeys = emptySet(),
        )
        assertEquals(listOf(NewSubject("probability", "Probability")), plan.newSubjects)
    }

    @Test
    fun `locations are deduplicated case-insensitively and known ones skipped`() {
        val plan = TimetableImport.plan(
            listOf(ev("A", "Mill Lane Lecture Rooms"), ev("B", "mill lane  lecture rooms"), ev("C", "Cavendish Laboratory"), ev("D")),
            existingAliasKeys = emptySet(),
            existingLocationKeys = setOf("cavendish laboratory"),
        )
        assertEquals(listOf("Mill Lane Lecture Rooms"), plan.locations)
    }
}

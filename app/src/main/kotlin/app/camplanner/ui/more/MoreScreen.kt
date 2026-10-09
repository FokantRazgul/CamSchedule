package app.camplanner.ui.more

import androidx.compose.runtime.Composable
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.IndexRow
import app.camplanner.domain.Roman
import java.time.LocalDate

data class MoreDestinations(
    val checkIn: () -> Unit,
    val stats: () -> Unit,
    val subjects: () -> Unit,
    val locations: () -> Unit,
    val import: () -> Unit,
    val exercises: () -> Unit,
    val settings: () -> Unit,
    val permissions: () -> Unit,
)

/** The index of everything not on the bottom bar. */
@Composable
fun MoreScreen(go: MoreDestinations) {
    AtlasPage(
        mastheadLeft = "Cam Planner",
        mastheadRight = Roman.of(LocalDate.now().year),
        title = "Index",
        aside = "Everything else, in order.",
        graticule = true,
    ) {
        val rows = listOf(
            Triple("The Evening Review", "Reading, homework and exercise for today", go.checkIn),
            Triple("The Reckoning", "Statistics for the week and the term", go.stats),
            Triple("Subjects", "Rename, merge, mark academic", go.subjects),
            Triple("The Gazetteer", "Places on the map", go.locations),
            Triple("Import a Timetable", "From an .ics file", go.import),
            Triple("Exercises", "Which ones, and how many", go.exercises),
            Triple("Settings", "Home, alarms, targets, term", go.settings),
            Triple("Permissions", "What the alarms need", go.permissions),
        )
        rows.forEachIndexed { i, (title, detail, action) ->
            IndexRow(Roman.of(i + 1), title, detail, action, showRule = i != rows.lastIndex)
        }
    }
}

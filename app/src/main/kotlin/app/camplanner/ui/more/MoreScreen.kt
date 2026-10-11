package app.camplanner.ui.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.ui.common.TabPage

/** Where each row of the More tab leads. */
data class MoreLinks(
    val stats: () -> Unit,
    val review: () -> Unit,
    val runs: () -> Unit,
    val exercises: () -> Unit,
    val import: () -> Unit,
    val subjects: () -> Unit,
    val places: () -> Unit,
    val settings: () -> Unit,
    val permissions: () -> Unit,
)

@Composable
fun MoreScreen(links: MoreLinks) {
    TabPage(header = { LargeHeader("More") }) {
        Section(
            "You",
            Entry("Statistics", "Streaks, pages, reps and kilometres", Icons.Outlined.BarChart, links.stats),
            Entry("Evening review", "Reading, homework and fitness for today", Icons.Outlined.Bedtime, links.review),
            Entry("Runs", "Every run, to correct or delete", Icons.AutoMirrored.Outlined.DirectionsRun, links.runs),
            Entry("Exercises and targets", "What you do, how much, rest days", Icons.Outlined.Tune, links.exercises),
        )
        Section(
            "Timetable",
            Entry("Import timetable", "From an .ics file", Icons.Outlined.FileDownload, links.import),
            Entry("Subjects", "Rename, colour, merge, academic", Icons.Outlined.Category, links.subjects),
            Entry("Places", "Correct where things are on the map", Icons.Outlined.Place, links.places),
        )
        Section(
            "App",
            Entry("Settings", "Home, alarm, alerts, term, backup", Icons.Outlined.Settings, links.settings),
            Entry("Alarms and notifications", "Check the permissions", Icons.Outlined.Alarm, links.permissions),
        )
    }
}

private class Entry(val title: String, val detail: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun Section(title: String, vararg entries: Entry) {
    GroupHeader(title)
    Group {
        entries.forEachIndexed { i, e ->
            ItemRow(
                e.title, supporting = e.detail,
                leading = { Icon(e.icon, null, tint = Atlas.colors.textSecondary) },
                onClick = e.onClick,
            )
            if (i != entries.lastIndex) GroupDivider(inset = 56.dp)
        }
    }
}

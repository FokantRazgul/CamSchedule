package app.camplanner.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.components.Moon
import app.camplanner.designsystem.kit.EmptyState
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.StatTile
import app.camplanner.designsystem.kit.SubjectBar
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.DayStatus
import app.camplanner.domain.StatsCalculator
import app.camplanner.domain.StatsInput
import app.camplanner.domain.StatsSnapshot
import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.ReadingLog
import app.camplanner.model.Subject
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.fitness.FitnessBuilder
import app.camplanner.ui.format.Formats
import app.camplanner.ui.today.TodayBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class StatsUi(val s: StatsSnapshot, val today: LocalDate, val runTarget: Double, val termLine: String?, val readingMin: Int)

class StatsViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    private data class Core(val events: List<CalendarEvent>, val subjects: List<Subject>, val reading: List<ReadingLog>, val homework: List<Assignment>)

    val state: StateFlow<StatsUi?> = combine(
        combine(flow { emit(graph.timetable.allTimedEvents()) }, graph.timetable.observeSubjects(), graph.reading.observeAll(), graph.homework.observeAll(), ::Core),
        graph.fitness.observeExercises(),
        graph.fitness.observeSets(),
        graph.fitness.observeRuns(),
        graph.settings.settings,
    ) { core, exercises, sets, runs, settings ->
        val today = LocalDate.now(zone)
        val input = StatsInput(today, Instant.now(), zone, settings, core.subjects, core.events, core.reading, core.homework, exercises, sets, runs)
        StatsUi(StatsCalculator.compute(input), today, settings.weeklyRunTargetKm, TodayBuilder.termLine(today, settings), settings.readingMinPages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun StatsRoute(onBack: () -> Unit) {
    val vm = appViewModel { graph, _ -> StatsViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Atlas.colors
    SubPage("Statistics", onBack) {
        val ui = state ?: return@SubPage
        val s = ui.s
        Text(
            listOfNotNull(ui.termLine, "this week ${Formats.dayMonthShort(s.week.start)}–${Formats.dayMonthShort(s.week.endInclusive)}").joinToString(" · "),
            style = Atlas.type.supporting, color = c.textSecondary,
        )

        GroupHeader("Streaks")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Reading", "${s.readingStreak}", Modifier.weight(1f), detail = if (s.readingStreak == 1) "day" else "days", valueColor = if (s.readingStreak > 0) c.gold else c.text)
            StatTile("Fitness", "${s.fitnessStreak}", Modifier.weight(1f), detail = "best ${s.fitnessBestStreak}", valueColor = if (s.fitnessStreak > 0) c.gold else c.text)
            StatTile("Homework", "${s.assignmentsCompleted}", Modifier.weight(1f), detail = if (s.assignmentsOverdue > 0) "${s.assignmentsOverdue} overdue" else "done this term")
        }

        GroupHeader("Last 14 days")
        Group {
            MoonRow("Reading", s.readingRecent, ui.today)
            GroupDivider()
            MoonRow("Fitness", s.fitnessRecent, ui.today)
        }

        GroupHeader("Fitness")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Reps this week", "${s.repsWeek}", Modifier.weight(1f), detail = "${s.repsTerm} this term")
            StatTile(
                "Km this week", Formats.km(s.runKmWeek), Modifier.weight(1f),
                detail = "of ${Formats.km(ui.runTarget)} · ${Formats.km(s.runKmTerm)} this term",
                valueColor = if (s.runKmWeek >= ui.runTarget) c.gold else c.text,
            )
        }

        GroupHeader("Reading by subject") { Text("week · term", style = Atlas.type.supporting, color = c.textSecondary) }
        Group {
            if (s.readingBySubject.isEmpty()) EmptyState("Mark subjects as academic to track reading.")
            s.readingBySubject.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SubjectBar(c.subject(r.subject.colorIndex), height = 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.subject.name, style = Atlas.type.itemTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (r.streak > 0) "${Formats.count(r.streak, "day")} in a row" else "No streak", style = Atlas.type.supporting, color = if (r.streak > 0) c.gold else c.textSecondary)
                    }
                    Text("${r.pagesWeek} · ${r.pagesTerm}", style = Atlas.type.itemTitle.copy(fontFeatureSettings = "tnum"), color = c.text)
                }
                if (i != s.readingBySubject.lastIndex) GroupDivider()
            }
        }
        Text(
            "A reading day counts when every subject that met reached ${ui.readingMin} pages.",
            style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

@Composable
private fun MoonRow(label: String, days: List<Pair<LocalDate, DayStatus>>, today: LocalDate) {
    val c = Atlas.colors
    Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text(label, style = Atlas.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { (d, status) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Moon(FitnessBuilder.moon(status, d == today), size = 14.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK), style = Atlas.type.caption, color = if (d == today) c.text else c.textSecondary)
                }
            }
        }
    }
}

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
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.MoonPhaseRow
import app.camplanner.designsystem.components.OrbitArc
import app.camplanner.designsystem.components.OrbitRing
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.StatsCalculator
import app.camplanner.domain.StatsInput
import app.camplanner.domain.StatsSnapshot
import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.ReadingLog
import app.camplanner.model.Subject
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.fitness.moonFor
import app.camplanner.ui.format.Formats
import app.camplanner.ui.today.TodayStateBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatsUi(val snapshot: StatsSnapshot, val today: LocalDate, val weeklyRunTarget: Double, val termLabel: String?, val readingMin: Int)

class StatsViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    private val events = flow { emit(graph.timetable.allTimedEvents()) }

    private data class Core(
        val events: List<CalendarEvent>,
        val subjects: List<Subject>,
        val reading: List<ReadingLog>,
        val homework: List<Assignment>,
    )

    val state: StateFlow<StatsUi?> = combine(
        combine(events, graph.timetable.observeSubjects(), graph.reading.observeAll(), graph.homework.observeAll(), ::Core),
        graph.fitness.observeExercises(),
        graph.fitness.observeSets(),
        graph.fitness.observeRuns(),
        graph.settings.settings,
    ) { core, exercises, sets, runs, settings ->
        val input = StatsInput(
            today = LocalDate.now(),
            now = Instant.now(),
            zone = zone,
            settings = settings,
            subjects = core.subjects,
            events = core.events,
            reading = core.reading,
            assignments = core.homework,
            exercises = exercises,
            sets = sets,
            runs = runs,
        )
        StatsUi(StatsCalculator.compute(input), input.today, settings.weeklyRunTargetKm, TodayStateBuilder.termLabel(input.today, settings), settings.readingMinPages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun StatsRoute(onBack: () -> Unit) {
    val vm = appViewModel { graph, _ -> StatsViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { StatsScreen(it, onBack) }
}

@Composable
fun StatsScreen(ui: StatsUi, onBack: () -> Unit) {
    val c = Atlas.colors
    val s = ui.snapshot
    AtlasPage(
        mastheadLeft = "Statistics",
        mastheadRight = "${Formats.dayMonthShort(s.term.start)} – ${Formats.dayMonthShort(s.term.endInclusive)}",
        dateline = ui.termLabel,
        title = "The Reckoning",
        aside = "This week and this term, in pages, sheets, repetitions and miles.",
        onBack = onBack,
        graticule = true,
    ) {
        // Headline instruments.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Streak rings close after a week.
            Instrument("Reading streak", s.readingStreak.toString(), "days", s.readingStreak / 7f)
            Instrument("Exercise streak", s.fitnessStreak.toString(), "best ${s.fitnessBestStreak}", s.fitnessStreak / 7f)
            Instrument("Run this week", Formats.km(s.runKmWeek), "of ${Formats.km(ui.weeklyRunTarget)} km", (s.runKmWeek / ui.weeklyRunTarget).toFloat())
        }

        Spacer(Modifier.height(Atlas.space.xxl))
        SectionTitle("I", "Reading") { Text("at least ${ui.readingMin} pp a day", style = Atlas.type.time, color = c.textSecondary) }
        Spacer(Modifier.height(Atlas.space.m))
        MoonPhaseRow(s.readingRecent.map { (d, st) -> moonFor(st, d == ui.today) }, moonSize = 14.dp)
        Spacer(Modifier.height(Atlas.space.s))
        Text("The last fortnight: a full moon when every subject that met had its pages read.", style = Atlas.type.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(Atlas.space.m))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text("WEEK", style = Atlas.type.overline, color = c.textSecondary, modifier = Modifier.width(56.dp))
            Text("TERM", style = Atlas.type.overline, color = c.textSecondary, modifier = Modifier.width(56.dp))
            Text("STREAK", style = Atlas.type.overline, color = c.textSecondary, modifier = Modifier.width(60.dp))
        }
        Hairline()
        if (s.readingBySubject.isEmpty()) Text("No academic subjects yet.", style = Atlas.type.aside, color = c.textSecondary, modifier = Modifier.padding(vertical = Atlas.space.m))
        s.readingBySubject.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = Atlas.space.m), verticalAlignment = Alignment.CenterVertically) {
                SubjectMark(c.subject(r.subject.colorIndex))
                Spacer(Modifier.width(Atlas.space.s))
                Text(r.subject.name, style = Atlas.type.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${r.pagesWeek}", style = Atlas.type.time, color = c.text, modifier = Modifier.width(56.dp))
                Text("${r.pagesTerm}", style = Atlas.type.time, color = c.text, modifier = Modifier.width(56.dp))
                Text("${r.streak}", style = Atlas.type.time, color = if (r.streak > 0) c.gold else c.textSecondary, modifier = Modifier.width(60.dp))
            }
            Hairline()
        }

        Spacer(Modifier.height(Atlas.space.xxl))
        SectionTitle("II", "Homework")
        Row(Modifier.fillMaxWidth().padding(vertical = Atlas.space.l)) {
            Figure("${s.assignmentsCompleted}", "completed", Modifier.weight(1f))
            Figure("${s.assignmentsOverdue}", "overdue", Modifier.weight(1f), warn = s.assignmentsOverdue > 0)
        }

        Spacer(Modifier.height(Atlas.space.xl))
        SectionTitle("III", "Exercise")
        Spacer(Modifier.height(Atlas.space.m))
        MoonPhaseRow(s.fitnessRecent.map { (d, st) -> moonFor(st, d == ui.today) }, moonSize = 14.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = Atlas.space.l)) {
            Figure("${s.repsWeek}", "repetitions this week", Modifier.weight(1f))
            Figure("${s.repsTerm}", "this term", Modifier.weight(1f))
        }
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = Atlas.space.l)) {
            Figure(Formats.km(s.runKmWeek), "km run this week", Modifier.weight(1f))
            Figure(Formats.km(s.runKmTerm), "km this term", Modifier.weight(1f))
        }
        OrbitArc((s.runKmWeek / ui.weeklyRunTarget).toFloat())
    }
}

@Composable
private fun Instrument(label: String, value: String, detail: String, fraction: Float) {
    val c = Atlas.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OrbitRing(
            progress = fraction, diameter = 96.dp, graduations = 24, majorEvery = 6,
            color = c.gold, showBody = fraction in 0.01f..0.99f,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = Atlas.type.numeral, color = c.text)
                Text(detail, style = Atlas.type.overline.copy(letterSpacing = Atlas.type.label.letterSpacing), color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Atlas.space.s))
        Text(label.uppercase(), style = Atlas.type.overline, color = c.textSecondary)
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Column(modifier) {
        Text(value, style = Atlas.type.numeralLarge.copy(fontSize = Atlas.type.headline.fontSize * 1.3f), color = if (warn) Atlas.colors.accentText else Atlas.colors.text)
        Text(label, style = Atlas.type.aside, color = Atlas.colors.textSecondary)
    }
}

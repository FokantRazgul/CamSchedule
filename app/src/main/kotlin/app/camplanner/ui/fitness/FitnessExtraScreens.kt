package app.camplanner.ui.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.ChoiceRow
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.LedgerField
import app.camplanner.designsystem.components.Moon
import app.camplanner.designsystem.components.NumberEntry
import app.camplanner.designsystem.components.OrbitDot
import app.camplanner.designsystem.components.RuledRow
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.Fitness
import app.camplanner.domain.Roman
import app.camplanner.domain.Streaks
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.RunLog
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

// --- History ----------------------------------------------------------------------------------

data class FitnessHistory(
    val today: LocalDate,
    val exercises: List<Exercise>,
    val sets: List<ExerciseSet>,
    val runs: List<RunLog>,
    val restDays: Set<DayOfWeek>,
)

class FitnessHistoryViewModel(private val graph: DataGraph) : ViewModel() {
    val state: StateFlow<FitnessHistory?> = combine(
        graph.fitness.observeExercises(), graph.fitness.observeSets(), graph.fitness.observeRuns(), graph.settings.settings,
    ) { e, s, r, settings -> FitnessHistory(LocalDate.now(), e, s, r, settings.restDays) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteRun(id: Long) = viewModelScope.launch { graph.fitness.deleteRun(id) }
}

@Composable
fun FitnessHistoryRoute(onBack: () -> Unit) {
    val vm = appViewModel { graph, _ -> FitnessHistoryViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { FitnessHistoryScreen(it, onBack, vm::deleteRun) }
}

@Composable
fun FitnessHistoryScreen(state: FitnessHistory, onBack: () -> Unit, onDeleteRun: (Long) -> Unit) {
    val c = Atlas.colors
    val status = fitnessStatus(state.exercises, state.sets, state.restDays)
    val earliest = state.sets.minOfOrNull { it.date } ?: state.today
    val byDay = state.sets.groupBy { it.date }
    val names = state.exercises.associateBy { it.id }
    AtlasPage(
        mastheadLeft = "Exercitia",
        mastheadRight = "best streak ${Streaks.best(earliest, state.today, status)}",
        title = "The Record",
        aside = "The last fortnight, day by day.",
        onBack = onBack,
    ) {
        SectionTitle("I", "Days")
        (0L until 14L).map { state.today.minusDays(it) }.forEach { d ->
            val sets = byDay[d].orEmpty()
            Row(Modifier.fillMaxWidth().padding(vertical = Atlas.space.m), verticalAlignment = Alignment.Top) {
                Moon(moonFor(status(d), d == state.today), Modifier.padding(top = 6.dp))
                Spacer(Modifier.padding(start = Atlas.space.m))
                Column(Modifier.weight(1f)) {
                    Text("${Formats.weekday(d)}, ${Formats.dayMonth(d)}", style = Atlas.type.dateline.copy(fontSize = Atlas.type.title.fontSize), color = c.text)
                    if (d.dayOfWeek in state.restDays) Text("Rest day", style = Atlas.type.aside, color = c.textSecondary)
                    val totals = sets.groupBy { it.exerciseId }.mapNotNull { (id, s) ->
                        val e = names[id] ?: return@mapNotNull null
                        val u = if (e.unit == ExerciseUnit.SECONDS) "s" else ""
                        "${e.name} ${s.sumOf { it.amount }}$u"
                    }
                    Text(
                        if (totals.isEmpty()) "Nothing logged" else totals.joinToString("  ·  "),
                        style = Atlas.type.bodySmall, color = c.textSecondary,
                    )
                }
            }
            Hairline()
        }

        Spacer(Modifier.height(Atlas.space.xl))
        SectionTitle("II", "Runs") { Text("${state.runs.size}", style = Atlas.type.time, color = c.textSecondary) }
        if (state.runs.isEmpty()) Text("No runs logged yet.", style = Atlas.type.aside, color = c.textSecondary, modifier = Modifier.padding(vertical = Atlas.space.m))
        state.runs.take(30).forEachIndexed { i, r ->
            RuledRow(showRule = i != minOf(state.runs.size, 30) - 1) {
                Column(Modifier.weight(1f)) {
                    Text("${Formats.km(r.distanceMeters)} km in ${Fitness.formatDuration(r.durationSeconds)}", style = Atlas.type.body, color = c.text)
                    Text(
                        Formats.shortDate(r.date) + (Fitness.pace(r.distanceMeters, r.durationSeconds)?.let { "  ·  ${Fitness.formatPace(it)} /km" } ?: ""),
                        style = Atlas.type.time, color = c.textSecondary,
                    )
                }
                AtlasButton("Remove", { onDeleteRun(r.id) }, style = ActionStyle.Text)
            }
        }
    }
}

// --- Exercise manager -------------------------------------------------------------------------

class ExercisesViewModel(private val graph: DataGraph) : ViewModel() {
    val exercises: StateFlow<List<Exercise>?> = graph.fitness.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(e: Exercise) = viewModelScope.launch { graph.fitness.saveExercise(e) }
    fun add(name: String, unit: ExerciseUnit, target: Int) = viewModelScope.launch { graph.fitness.addCustom(name, unit, target) }
    fun delete(id: Long) = viewModelScope.launch { graph.fitness.deleteCustom(id) }
}

@Composable
fun ExercisesRoute(onBack: () -> Unit) {
    val vm = appViewModel { graph, _ -> ExercisesViewModel(graph) }
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    exercises?.let { ExercisesScreen(it, onBack, vm::save, vm::add, vm::delete) }
}

@Composable
fun ExercisesScreen(
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onSave: (Exercise) -> Unit,
    onAdd: (String, ExerciseUnit, Int) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val c = Atlas.colors
    AtlasPage(
        mastheadLeft = "Exercitia",
        mastheadRight = "${exercises.count { it.active }} active",
        title = "The Exercises",
        aside = "Quiet ones only: no jumping, nothing to disturb the room below.",
        onBack = onBack,
    ) {
        val groups = listOf(
            ExerciseGroup.UPPER to "Upper Body",
            ExerciseGroup.ABS to "Abs",
            ExerciseGroup.LEGS to "Legs",
            ExerciseGroup.CUSTOM to "Your Own",
        )
        groups.forEachIndexed { gi, (group, title) ->
            val list = exercises.filter { it.group == group }
            if (list.isEmpty() && group != ExerciseGroup.CUSTOM) return@forEachIndexed
            SectionTitle(Roman.of(gi + 1), title)
            list.forEachIndexed { i, e ->
                var target by remember(e.id, e.dailyTarget) { mutableStateOf(e.dailyTarget.toString()) }
                Column(Modifier.fillMaxWidth().padding(vertical = Atlas.space.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OrbitDot(if (e.active) 1f else 0f, color = c.gold)
                        Spacer(Modifier.padding(start = Atlas.space.m))
                        Text(e.name, style = Atlas.type.title, color = if (e.active) c.text else c.textSecondary, modifier = Modifier.weight(1f))
                        NumberEntry(target, {
                            target = it
                            it.toIntOrNull()?.takeIf { n -> n > 0 }?.let { n -> onSave(e.copy(dailyTarget = n)) }
                        }, suffix = if (e.unit == ExerciseUnit.SECONDS) "s" else "reps")
                    }
                    Spacer(Modifier.height(Atlas.space.s))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Atlas.space.s)) {
                        ChoiceRow(listOf(true to "Daily", false to "Off"), e.active, { onSave(e.copy(active = it)) })
                        Spacer(Modifier.weight(1f))
                        if (e.builtInKey == null) AtlasButton("Remove", { onDelete(e.id) }, style = ActionStyle.Text)
                    }
                }
                if (i != list.lastIndex) Hairline()
            }
            Spacer(Modifier.height(Atlas.space.xl))
        }

        var name by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf(ExerciseUnit.REPS) }
        var target by remember { mutableStateOf("") }
        Text("ADD AN EXERCISE", style = Atlas.type.overline, color = c.textSecondary)
        Spacer(Modifier.height(Atlas.space.m))
        LedgerField(name, { name = it }, label = "Name", placeholder = "Side plank")
        Spacer(Modifier.height(Atlas.space.l))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Atlas.space.m)) {
            ChoiceRow(listOf(ExerciseUnit.REPS to "Reps", ExerciseUnit.SECONDS to "Seconds"), unit, { unit = it })
            Spacer(Modifier.weight(1f))
            NumberEntry(target, { target = it }, placeholder = "30")
        }
        Spacer(Modifier.height(Atlas.space.l))
        AtlasButton(
            "Add", {
                val n = target.toIntOrNull()
                if (name.isNotBlank() && n != null && n > 0) {
                    onAdd(name, unit, n); name = ""; target = ""
                }
            },
            style = ActionStyle.Filled, enabled = name.isNotBlank() && (target.toIntOrNull() ?: 0) > 0, modifier = Modifier.fillMaxWidth(),
        )
    }
}

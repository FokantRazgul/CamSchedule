package app.camplanner.ui.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.AppSwitch
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.EmptyState
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SettingRow
import app.camplanner.designsystem.kit.SheetBody
import app.camplanner.designsystem.kit.Stepper
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.Fitness
import app.camplanner.model.AppSettings
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.RunLog
import app.camplanner.ui.common.AppSheet
import app.camplanner.ui.common.Confirm
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.common.updateSettings
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// --- Runs -------------------------------------------------------------------------------------

class RunsViewModel(private val graph: DataGraph) : ViewModel() {
    val runs: StateFlow<List<RunLog>?> = graph.fitness.observeRuns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: Long?, date: LocalDate, meters: Int, seconds: Int) = viewModelScope.launch {
        if (id == null) graph.fitness.logRun(date, meters, seconds) else graph.fitness.updateRun(RunLog(id, date, meters, seconds))
    }

    fun delete(id: Long) = viewModelScope.launch {
        val run = graph.fitness.deleteRun(id) ?: return@launch
        Notices.show("Run deleted") { graph.fitness.restoreRun(run) }
    }
}

@Composable
fun RunsRoute(onBack: () -> Unit) {
    val vm = appViewModel { graph, _ -> RunsViewModel(graph) }
    val runs by vm.runs.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RunLog?>(null) }
    var adding by remember { mutableStateOf(false) }
    val c = Atlas.colors
    SubPage("Runs", onBack, actions = { HeaderAction(Icons.Outlined.Add, "Log a run", { adding = true }) }) {
        val all = runs ?: return@SubPage
        if (all.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Group { EmptyState("No runs yet.", action = "Log a run", onAction = { adding = true }) }
        }
        val months = all.sortedWith(compareByDescending<RunLog> { it.date }.thenByDescending { it.id })
            .groupBy { it.date.withDayOfMonth(1) }
        months.forEach { (month, list) ->
            GroupHeader(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK).format(month)) {
                Text("${Formats.km(list.sumOf { it.distanceMeters })} km", style = Atlas.type.supporting, color = c.textSecondary)
            }
            Group {
                list.forEachIndexed { i, r ->
                    val pace = Fitness.pace(r.distanceMeters, r.durationSeconds)?.let { " · ${Fitness.formatPace(it)} /km" } ?: ""
                    ItemRow(
                        title = "${Formats.km(r.distanceMeters)} km in ${Fitness.formatDuration(r.durationSeconds)}",
                        supporting = Formats.shortDate(r.date) + pace,
                        onClick = { editing = r },
                    )
                    if (i != list.lastIndex) GroupDivider()
                }
            }
        }
    }
    editing?.let { r ->
        RunSheet(r, r.date, onSave = { d, m, s -> vm.save(r.id, d, m, s) }, onDismiss = { editing = null }, onDelete = { vm.delete(r.id) })
    }
    if (adding) RunSheet(null, LocalDate.now(), onSave = { d, m, s -> vm.save(null, d, m, s) }, onDismiss = { adding = false })
}

// --- Exercises and targets --------------------------------------------------------------------

class ExercisesViewModel(private val graph: DataGraph) : ViewModel() {
    val exercises: StateFlow<List<Exercise>?> = graph.fitness.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings?> = graph.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(e: Exercise) = viewModelScope.launch { graph.fitness.saveExercise(e) }

    fun add(name: String, unit: ExerciseUnit, target: Int) = viewModelScope.launch {
        graph.fitness.addCustom(name, unit, target)
        Notices.show("Added $name")
    }

    fun delete(e: Exercise) = viewModelScope.launch {
        graph.fitness.deleteCustom(e.id)
        Notices.show("Deleted ${e.name}")
    }

    fun update(change: (AppSettings) -> AppSettings) = viewModelScope.launch { graph.updateSettings(change) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExercisesRoute(onBack: () -> Unit) {
    val vm = appViewModel { graph, _ -> ExercisesViewModel(graph) }
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    val c = Atlas.colors

    SubPage("Exercises and targets", onBack, actions = { HeaderAction(Icons.Outlined.Add, "Add an exercise", { adding = true }) }) {
        val all = exercises ?: return@SubPage
        val s = settings ?: return@SubPage
        Text(
            "Switch on what you do and set a daily target. Everything here is quiet enough for a college room.",
            style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
        )
        listOf(
            ExerciseGroup.UPPER to "Upper body",
            ExerciseGroup.ABS to "Abs",
            ExerciseGroup.LEGS to "Legs",
            ExerciseGroup.CUSTOM to "Your own",
        ).forEach { (group, title) ->
            val list = all.filter { it.group == group }.sortedBy { it.sortOrder }
            if (list.isEmpty() && group != ExerciseGroup.CUSTOM) return@forEach
            GroupHeader(title)
            Group {
                if (list.isEmpty()) EmptyState("Add your own exercise with any daily target.", action = "Add an exercise", onAction = { adding = true })
                list.forEachIndexed { i, e ->
                    ExerciseRow(e, onChange = vm::save, onDelete = if (e.builtInKey == null) ({ deleting = e }) else null)
                    if (i != list.lastIndex) GroupDivider()
                }
            }
        }

        GroupHeader("Running")
        Group {
            SettingRow("Weekly distance", supporting = "Your target for each week") {
                Stepper(s.weeklyRunTargetKm.toInt(), { km -> vm.update { it.copy(weeklyRunTargetKm = km.toDouble()) } }, min = 1, max = 200, suffix = " km", description = "weekly distance")
            }
        }

        GroupHeader("Rest days")
        Group {
            Text(
                "Nothing is expected on these days, and streaks carry over them.",
                style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { d ->
                    AppChip(d.getDisplayName(TextStyle.SHORT, Locale.UK), d in s.restDays, {
                        vm.update { st -> st.copy(restDays = if (d in st.restDays) st.restDays - d else st.restDays + d) }
                    })
                }
            }
        }
    }

    if (adding) AddExerciseSheet(onAdd = vm::add, onDismiss = { adding = false })
    deleting?.let { e ->
        Confirm("Delete ${e.name}?", "Its logged sets are deleted too.", "Delete", { vm.delete(e) }, { deleting = null })
    }
}

@Composable
private fun ExerciseRow(e: Exercise, onChange: (Exercise) -> Unit, onDelete: (() -> Unit)?) {
    val c = Atlas.colors
    val unit = if (e.unit == ExerciseUnit.SECONDS) " s" else ""
    SettingRow(
        e.name,
        supporting = if (e.active) "Daily target ${e.dailyTarget}$unit" else "Off",
        control = {
            if (onDelete != null) {
                IconButton(onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete ${e.name}", tint = c.textSecondary) }
            }
            AppSwitch(e.active, { onChange(e.copy(active = it)) })
        },
    )
    if (e.active) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Spacer(Modifier.weight(1f))
            val step = if (e.unit == ExerciseUnit.SECONDS) 15 else 5
            Stepper(e.dailyTarget, { onChange(e.copy(dailyTarget = it)) }, step = step, min = step, max = 1000, suffix = unit, description = "${e.name} target")
        }
    }
}

@Composable
private fun AddExerciseSheet(onAdd: (String, ExerciseUnit, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(ExerciseUnit.REPS) }
    var target by remember { mutableStateOf("") }
    val t = target.toIntOrNull()?.takeIf { it > 0 }
    AppSheet(onDismiss) {
        SheetBody("New exercise") {
            AppTextField(name, { name = it }, "Name", placeholder = "Dips, side plank…")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("Reps", unit == ExerciseUnit.REPS, { unit = ExerciseUnit.REPS })
                AppChip("Seconds", unit == ExerciseUnit.SECONDS, { unit = ExerciseUnit.SECONDS })
            }
            Spacer(Modifier.height(14.dp))
            AppTextField(
                target, { target = it.filter(Char::isDigit).take(4) }, "Daily target",
                keyboardType = KeyboardType.Number, suffix = if (unit == ExerciseUnit.SECONDS) "seconds" else "reps",
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                "Add", { if (name.isNotBlank() && t != null) { onAdd(name.trim(), unit, t); onDismiss() } },
                Modifier.fillMaxWidth(), enabled = name.isNotBlank() && t != null,
            )
            TertiaryButton("Cancel", onDismiss, Modifier.fillMaxWidth(), color = Atlas.colors.textSecondary)
        }
    }
}

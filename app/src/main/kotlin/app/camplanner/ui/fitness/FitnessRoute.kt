package app.camplanner.ui.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasDialog
import app.camplanner.designsystem.components.LedgerField
import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.DayStatus
import app.camplanner.domain.Fitness
import app.camplanner.domain.Periods
import app.camplanner.domain.Streaks
import app.camplanner.model.BuiltInExercises
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseSet
import app.camplanner.ui.common.DateField
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.common.minuteTicker
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** Maps a day's fitness status to the moon drawn for it. */
fun moonFor(status: DayStatus, isToday: Boolean): MoonPhase = when {
    status == DayStatus.MET -> MoonPhase.FULL
    status == DayStatus.SKIP -> MoonPhase.HALF
    isToday -> MoonPhase.CRESCENT
    else -> MoonPhase.NEW
}

fun fitnessStatus(exercises: List<Exercise>, sets: List<ExerciseSet>, restDays: Set<DayOfWeek>): (LocalDate) -> DayStatus {
    val byDay = sets.groupBy { it.date }
    return { d -> Fitness.dayStatus(d, restDays, Fitness.progress(exercises, byDay[d].orEmpty(), d)) }
}

class FitnessViewModel(private val graph: DataGraph) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    private val custom = MutableStateFlow("")
    private val today = minuteTicker().map { it.toLocalDate() }.distinctUntilChanged()

    val state: StateFlow<FitnessUiState?> = combine(
        combine(today, graph.fitness.observeExercises(), graph.fitness.observeSets()) { d, e, s -> Triple(d, e, s) },
        graph.fitness.observeRuns(),
        graph.settings.settings,
        selected,
        custom,
    ) { (date, exercises, sets), runs, settings, sel, customText ->
        val progress = Fitness.progress(exercises, sets, date)
        val main = progress.filter { it.exercise.builtInKey == BuiltInExercises.PUSH_UPS || it.exercise.builtInKey == BuiltInExercises.PULL_UPS }
        val others = progress - main.toSet()
        val week = Periods.week(date, settings.weekStart)
        val weekKm = runs.filter { it.date in week }.sumOf { it.distanceMeters } / 1000.0
        val last = runs.firstOrNull()
        val status = fitnessStatus(exercises, sets, settings.restDays)
        val earliest = sets.minOfOrNull { it.date } ?: date
        FitnessUiState(
            date = date,
            isRestDay = date.dayOfWeek in settings.restDays,
            main = main.map { ExerciseLine(it.exercise.id, it.exercise.name, it.exercise.unit, it.done, it.target) },
            selectedMainId = sel ?: main.firstOrNull()?.exercise?.id,
            customAmount = customText,
            others = others.map { ExerciseLine(it.exercise.id, it.exercise.name, it.exercise.unit, it.done, it.target) },
            run = RunSummary(
                weekKm = weekKm,
                targetKm = settings.weeklyRunTargetKm,
                lastRunDate = last?.let { Formats.shortDate(it.date) },
                lastRunStats = last?.let { r ->
                    val pace = Fitness.pace(r.distanceMeters, r.durationSeconds)?.let { "  ·  ${Fitness.formatPace(it)} /km" } ?: ""
                    "${Formats.km(r.distanceMeters)} km  ·  ${Fitness.formatDuration(r.durationSeconds)}$pace"
                },
            ),
            recent = Streaks.recent(date, 14, status).map { (d, s) -> moonFor(s, d == date) },
            streakDays = Streaks.current(date, earliest, status),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: Long) { selected.value = id }
    fun setCustom(text: String) { custom.value = text.filter(Char::isDigit).take(4) }

    fun add(exerciseId: Long, amount: Int) = viewModelScope.launch { graph.fitness.log(exerciseId, amount, LocalDate.now()) }

    fun addCustom() {
        val id = state.value?.selectedMainId ?: return
        val amount = custom.value.toIntOrNull() ?: return
        add(id, amount)
        custom.value = ""
    }

    fun logRun(date: LocalDate, km: Double, seconds: Int) = viewModelScope.launch {
        graph.fitness.logRun(date, (km * 1000).toInt(), seconds)
    }
}

@Composable
fun FitnessRoute(onOpenHistory: () -> Unit, onManageExercises: () -> Unit) {
    val vm = appViewModel { graph, _ -> FitnessViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf(false) }
    state?.let {
        FitnessScreen(
            state = it,
            onSelectMain = vm::select,
            onQuickAdd = { id, n -> vm.add(id, n) },
            onCustomAmountChange = vm::setCustom,
            onAddCustom = vm::addCustom,
            onLogRun = { logging = true },
            onOpenHistory = onOpenHistory,
            onManageExercises = onManageExercises,
        )
    }
    if (logging) RunDialog(onDismiss = { logging = false }, onSave = { d, km, s -> vm.logRun(d, km, s); logging = false })
}

/** Distance and time in; pace shown as you type. */
@Composable
fun RunDialog(onDismiss: () -> Unit, onSave: (LocalDate, Double, Int) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var km by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    val distance = km.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 200 }
    val seconds = Fitness.parseDuration(time)
    val pace = if (distance != null && seconds != null) Fitness.pace((distance * 1000).toInt(), seconds) else null
    AtlasDialog(
        title = "Log a run",
        onDismiss = onDismiss,
        actions = {
            AtlasButton("Cancel", onDismiss, style = ActionStyle.Text)
            AtlasButton("Save", { if (distance != null && seconds != null) onSave(date, distance, seconds) }, style = ActionStyle.Text, enabled = distance != null && seconds != null)
        },
    ) {
        DateField("Date", date, { date = it })
        Spacer(Modifier.height(Atlas.space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Atlas.space.l)) {
            LedgerField(km, { km = it }, Modifier.weight(1f), label = "Distance", placeholder = "5.0", suffix = "km", keyboardType = KeyboardType.Decimal)
            LedgerField(time, { time = it }, Modifier.weight(1f), label = "Time", placeholder = "27:30", keyboardType = KeyboardType.Text)
        }
        Spacer(Modifier.height(Atlas.space.m))
        Text(
            pace?.let { "Pace ${Fitness.formatPace(it)} per km" } ?: "Time as minutes, mm:ss or h:mm:ss",
            style = Atlas.type.time, color = if (pace != null) Atlas.colors.gold else Atlas.colors.textSecondary,
        )
    }
}

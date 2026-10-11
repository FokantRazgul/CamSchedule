package app.camplanner.ui.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.components.Moon
import app.camplanner.designsystem.kit.CountButton
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.kit.ProgressBar
import app.camplanner.designsystem.kit.ProgressRing
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.ExerciseUnit
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.TabPage
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class FitnessViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = minuteTicker().map { it.toLocalDate() }.distinctUntilChanged()

    /** Days back from today being shown; 0 is today. */
    private val back = MutableStateFlow(0L)

    val state: StateFlow<FitnessState?> = combine(
        combine(today, back) { t, b -> t to t.minusDays(b) },
        graph.fitness.observeExercises(),
        graph.fitness.observeSets(),
        graph.fitness.observeRuns(),
        graph.settings.settings,
    ) { (today, date), exercises, sets, runs, settings ->
        FitnessBuilder.build(date, today, exercises, sets, runs, settings, zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showDay(daysBack: Long) {
        back.value = daysBack.coerceAtLeast(0)
    }

    /** Adds (or with a negative amount, takes off) and offers Undo. */
    fun add(c: Counter, amount: Int) = viewModelScope.launch {
        val date = state.value?.date ?: return@launch
        val id = graph.fitness.log(c.id, amount, date) ?: return@launch
        val set = graph.fitness.setsOn(date).firstOrNull { it.id == id } ?: return@launch
        val unit = if (c.unit == ExerciseUnit.SECONDS) " s" else ""
        val text = if (set.amount > 0) "Added ${set.amount}$unit ${c.name.lowercase()}" else "Took ${-set.amount}$unit off ${c.name.lowercase()}"
        Notices.show(text) { graph.fitness.deleteSet(id) }
    }

    fun removeSet(id: Long) = viewModelScope.launch {
        val set = graph.fitness.deleteSet(id) ?: return@launch
        Notices.show("Removed a set") { graph.fitness.restoreSet(set) }
    }

    fun saveRun(id: Long?, date: LocalDate, meters: Int, seconds: Int) = viewModelScope.launch {
        if (id == null) {
            val newId = graph.fitness.logRun(date, meters, seconds)
            Notices.show("Run logged") { graph.fitness.deleteRun(newId) }
        } else {
            graph.fitness.updateRun(app.camplanner.model.RunLog(id, date, meters, seconds))
            Notices.show("Run updated")
        }
    }
}

@Composable
fun FitnessRoute(onHistory: () -> Unit, onExercises: () -> Unit) {
    val vm = appViewModel { graph, _ -> FitnessViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    var amountFor by remember { mutableStateOf<Counter?>(null) }
    var logRun by remember { mutableStateOf(false) }
    state?.let { s ->
        FitnessScreen(
            s,
            onAdd = vm::add,
            onCustom = { amountFor = it },
            onRemoveSet = vm::removeSet,
            onLogRun = { logRun = true },
            onHistory = onHistory,
            onExercises = onExercises,
            onDay = { vm.showDay(it) },
        )
        amountFor?.let { c ->
            // Re-read so the sheet shows the current count while it is open.
            val live = (s.main + s.others).firstOrNull { it.id == c.id } ?: c
            AmountSheet(live, onAdd = { vm.add(live, it) }, onDismiss = { amountFor = null })
        }
        if (logRun) RunSheet(null, s.date, onSave = { d, m, sec -> vm.saveRun(null, d, m, sec) }, onDismiss = { logRun = false })
    }
}

@Composable
fun FitnessScreen(
    s: FitnessState,
    onAdd: (Counter, Int) -> Unit,
    onCustom: (Counter) -> Unit,
    onRemoveSet: (Long) -> Unit,
    onLogRun: () -> Unit,
    onHistory: () -> Unit,
    onExercises: () -> Unit,
    onDay: (Long) -> Unit,
) {
    val c = Atlas.colors
    val back = s.today.toEpochDay() - s.date.toEpochDay()
    TabPage(
        header = {
            LargeHeader(
                title = "Fitness",
                overline = if (s.restDay) "Rest day" else null,
                overlineColor = c.textSecondary,
                actions = {
                    HeaderAction(Icons.Outlined.History, "Runs", onHistory)
                    HeaderAction(Icons.Outlined.Tune, "Exercises and targets", onExercises)
                },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onDay(back + 1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day", tint = c.textSecondary) }
            Text(
                "${Formats.dayLabel(s.date, s.today)}${if (back <= 1) ", " + Formats.dayMonth(s.date) else ""}",
                style = Atlas.type.itemTitle, color = if (back == 0L) c.text else c.accentText,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            if (back > 0) {
                IconButton({ onDay(back - 1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day", tint = c.textSecondary) }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
        if (back > 0) {
            Text(
                "Logging for ${Formats.shortDate(s.date)}.",
                style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        s.main.forEach { m ->
            CounterCard(m, onAdd, onCustom)
            Spacer(Modifier.height(10.dp))
        }

        GroupHeader(if (back == 0L) "Today's sets" else "Sets that day") {
            if (s.log.isNotEmpty()) Text("× removes one", style = Atlas.type.supporting, color = c.textSecondary)
        }
        Group {
            if (s.log.isEmpty()) {
                Text("Sets you log appear here, and you can remove any of them.", style = Atlas.type.reading, color = c.textSecondary, modifier = Modifier.padding(16.dp))
            }
            s.log.forEachIndexed { i, set ->
                val unit = if (set.unit == ExerciseUnit.SECONDS) " s" else ""
                ItemRow(
                    title = "${set.exercise}  ${if (set.amount > 0) "+" else "−"}${kotlin.math.abs(set.amount)}$unit",
                    supporting = Formats.time(set.at),
                    minHeight = 56.dp,
                    trailing = {
                        IconButton({ onRemoveSet(set.id) }) { Icon(Icons.Outlined.Close, "Remove this set", tint = c.textSecondary) }
                    },
                )
                if (i != s.log.lastIndex) GroupDivider()
            }
        }

        if (s.others.isNotEmpty()) {
            GroupHeader("Abs and legs") { TertiaryButton("Choose", onExercises) }
            Group {
                s.others.forEachIndexed { i, o ->
                    ItemRow(
                        title = o.name,
                        supporting = if (o.remaining == 0) "Done · ${o.done}${o.suffix}" else "${o.done} of ${o.target}${o.suffix}",
                        supportingColor = if (o.remaining == 0) c.gold else c.textSecondary,
                        leading = { ProgressRing(o.fraction, size = 36.dp, stroke = 4.dp) },
                        trailing = { CountButton("+${o.step}", { onAdd(o, o.step) }, description = "Add ${o.step} to ${o.name}") },
                        onClick = { onCustom(o) },
                    )
                    if (i != s.others.lastIndex) GroupDivider(inset = 68.dp)
                }
            }
        }

        GroupHeader("Running") { Text("this week", style = Atlas.type.supporting, color = c.textSecondary) }
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Formats.km(s.weekKm), style = Atlas.type.groupTitle.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"), color = if (s.weekKm >= s.targetKm) c.gold else c.text)
                    Spacer(Modifier.width(6.dp))
                    Text("of ${Formats.km(s.targetKm)} km", style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(if (s.targetKm > 0) (s.weekKm / s.targetKm).toFloat() else 1f)
                Spacer(Modifier.height(12.dp))
                Text(s.lastRun?.let { "Last run: $it" } ?: "No runs yet.", style = Atlas.type.supporting, color = c.textSecondary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Log a run", onLogRun, Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.DirectionsRun)
                    TertiaryButton("All runs", onHistory)
                }
            }
        }

        GroupHeader("Last 14 days") {
            Text(if (s.streak > 0) Formats.count(s.streak, "day") + " in a row" else "no streak yet", style = Atlas.type.supporting, color = if (s.streak > 0) c.gold else c.textSecondary)
        }
        Group {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                s.recent.forEach { (day, phase) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Moon(phase, size = 16.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK),
                            style = Atlas.type.caption, color = if (day == s.today) c.text else c.textSecondary,
                        )
                    }
                }
            }
            Text(
                "Full moon: every target met. Half: rest day. Empty: missed.",
                style = Atlas.type.supporting, color = c.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
    }
}

/** Push-ups or pull-ups: ring, count, and quick steps, plus "−5" and any amount. */
@Composable
private fun CounterCard(m: Counter, onAdd: (Counter, Int) -> Unit, onCustom: (Counter) -> Unit) {
    val c = Atlas.colors
    Group {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(m.fraction, size = 92.dp, stroke = 7.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${m.done}", style = Atlas.type.groupTitle.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"), color = if (m.remaining == 0) c.gold else c.text)
                    Text("of ${m.target}", style = Atlas.type.caption, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = Atlas.type.groupTitle, color = c.text)
                Text(
                    if (m.remaining == 0) "Done for the day" else "${m.remaining} to go",
                    style = Atlas.type.supporting, color = if (m.remaining == 0) c.gold else c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CountButton("+5", { onAdd(m, 5) }, description = "Add 5 ${m.name}")
                    CountButton("+10", { onAdd(m, 10) }, description = "Add 10 ${m.name}")
                    CountButton("+20", { onAdd(m, 20) }, description = "Add 20 ${m.name}")
                    CountButton("…", { onCustom(m) }, description = "Another amount of ${m.name}")
                }
            }
        }
    }
}

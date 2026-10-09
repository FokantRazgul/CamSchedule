package app.camplanner.ui.fitness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.ChoiceRow
import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.designsystem.components.MoonPhaseRow
import app.camplanner.designsystem.components.NumberEntry
import app.camplanner.designsystem.components.OrbitArc
import app.camplanner.designsystem.components.OrbitDot
import app.camplanner.designsystem.components.OrbitRing
import app.camplanner.designsystem.components.QuickAddButton
import app.camplanner.designsystem.components.RuledRow
import app.camplanner.designsystem.components.ScreenHeading
import app.camplanner.designsystem.components.SectionHeader
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.ExerciseUnit
import app.camplanner.ui.format.Formats
import java.time.LocalDate

@Immutable
data class FitnessUiState(
    val date: LocalDate,
    val isRestDay: Boolean,
    /** Push-ups and pull-ups, shown as the two large orbits. */
    val main: List<ExerciseLine>,
    val selectedMainId: Long?,
    val customAmount: String,
    /** Active abs and legs exercises plus custom ones. */
    val others: List<ExerciseLine>,
    val run: RunSummary,
    val recent: List<MoonPhase>,
    val streakDays: Int,
)

@Immutable
data class ExerciseLine(
    val id: Long,
    val name: String,
    val unit: ExerciseUnit,
    val done: Int,
    val target: Int,
) {
    val remaining: Int get() = (target - done).coerceAtLeast(0)
    val fraction: Float get() = if (target <= 0) 1f else done / target.toFloat()

    /** One tap adds a typical set: 5 reps or 15 seconds. */
    val step: Int get() = if (unit == ExerciseUnit.SECONDS) 15 else 5
}

@Immutable
data class RunSummary(
    val weekKm: Double,
    val targetKm: Double,
    /** "Sun 18 Oct", or null when nothing is logged yet. */
    val lastRunDate: String?,
    /** "5.0 km  ·  27:00  ·  5:24 /km" */
    val lastRunStats: String?,
)

@Composable
fun FitnessScreen(
    state: FitnessUiState,
    onSelectMain: (Long) -> Unit,
    onQuickAdd: (exerciseId: Long, amount: Int) -> Unit,
    onCustomAmountChange: (String) -> Unit,
    onAddCustom: () -> Unit,
    onLogRun: () -> Unit,
    onOpenHistory: () -> Unit,
    onManageExercises: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = Atlas.space
    val c = Atlas.colors
    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.gutter),
    ) {
        Spacer(Modifier.height(space.xl))
        ScreenHeading(
            title = "Fitness",
            overline = "${Formats.weekday(state.date)} ${Formats.dayMonth(state.date)}" + if (state.isRestDay) "  ·  rest day" else "",
        )
        Spacer(Modifier.height(space.xl))

        // Two large orbits.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            state.main.forEach { line -> MainOrbit(line) }
        }
        Spacer(Modifier.height(space.l))
        val selected = state.selectedMainId
        if (state.main.isNotEmpty() && selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ADD TO", style = Atlas.type.overline, color = c.textSecondary)
                Spacer(Modifier.width(space.m))
                ChoiceRow(state.main.map { it.id to it.name }, selected, onSelectMain)
            }
            Spacer(Modifier.height(space.m))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.s)) {
                listOf(5, 10, 20).forEach { n -> QuickAddButton(n, { onQuickAdd(selected, n) }) }
                Spacer(Modifier.weight(1f))
                NumberEntry(state.customAmount, onCustomAmountChange, placeholder = "–")
                AtlasButton("Add", onAddCustom, style = ActionStyle.Text, enabled = state.customAmount.isNotEmpty())
            }
        }

        Spacer(Modifier.height(space.xxl))
        SectionHeader("Abs & legs") {
            AtlasButton("Edit", onManageExercises, style = ActionStyle.Text, contentPadding = PaddingValues(0.dp))
        }
        state.others.forEachIndexed { i, line ->
            RuledRow(showRule = i != state.others.lastIndex) {
                OrbitDot(line.fraction, color = if (line.remaining == 0) c.gold else c.accent)
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = Atlas.type.body, color = c.text)
                    Text(progressText(line), style = Atlas.type.time, color = c.textSecondary)
                }
                if (line.remaining > 0) {
                    AtlasButton(
                        text = "+${line.step}${unitSuffix(line.unit)}",
                        onClick = { onQuickAdd(line.id, line.step) },
                        textStyle = Atlas.type.time,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(space.xxl))
        SectionHeader("Running") {
            Text("this week", style = Atlas.type.time, color = c.textSecondary)
        }
        Spacer(Modifier.height(space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Formats.km(state.run.weekKm), style = Atlas.type.numeral, color = c.text)
            Spacer(Modifier.width(space.xs))
            Text("of ${Formats.km(state.run.targetKm)} km", style = Atlas.type.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        OrbitArc(progress = (state.run.weekKm / state.run.targetKm).toFloat())
        Spacer(Modifier.height(space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (state.run.lastRunDate != null && state.run.lastRunStats != null) {
                    Text("LAST RUN  ·  ${state.run.lastRunDate.uppercase()}", style = Atlas.type.overline, color = c.textSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(state.run.lastRunStats, style = Atlas.type.time, color = c.text)
                } else {
                    Text("No runs logged yet.", style = Atlas.type.aside, color = c.textSecondary)
                }
            }
            AtlasButton("Log a run", onLogRun)
        }

        Spacer(Modifier.height(space.xxl))
        SectionHeader("Last fortnight") {
            Text("${state.streakDays}-day streak", style = Atlas.type.time, color = c.gold)
        }
        Spacer(Modifier.height(space.s))
        MoonPhaseRow(state.recent, moonSize = 14.dp)
        Spacer(Modifier.height(space.m))
        AtlasButton("History", onOpenHistory, style = ActionStyle.Text, contentPadding = PaddingValues(0.dp))
        Spacer(Modifier.height(space.xxxl))
    }
}

@Composable
private fun MainOrbit(line: ExerciseLine) {
    val c = Atlas.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OrbitRing(progress = line.fraction, diameter = 136.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${line.done}", style = Atlas.type.numeralLarge, color = c.text)
                Text("of ${line.target}", style = Atlas.type.time, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Atlas.space.s))
        Text(line.name, style = Atlas.type.title, color = c.text)
        Text(
            if (line.remaining == 0) "complete" else "${line.remaining} to go",
            style = Atlas.type.time,
            color = if (line.remaining == 0) c.gold else c.textSecondary,
        )
    }
}

private fun unitSuffix(unit: ExerciseUnit) = if (unit == ExerciseUnit.SECONDS) "s" else ""

private fun progressText(line: ExerciseLine): String {
    val u = unitSuffix(line.unit)
    return if (line.remaining == 0) "${line.done}$u  ·  done" else "${line.done}$u of ${line.target}$u  ·  ${line.remaining}$u to go"
}

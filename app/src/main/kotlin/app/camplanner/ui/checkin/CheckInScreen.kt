package app.camplanner.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.ChoiceRow
import app.camplanner.designsystem.components.NumberEntry
import app.camplanner.designsystem.components.OrbitRing
import app.camplanner.designsystem.components.RuledRow
import app.camplanner.designsystem.components.Masthead
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.components.StarFieldBackground
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.ExerciseUnit
import app.camplanner.ui.format.Formats
import java.time.LocalDate

@Immutable
data class CheckInUiState(
    val date: LocalDate,
    val readingMin: Int,
    val readingMax: Int,
    val reading: List<ReadingEntry>,
    val homework: List<HomeworkEntry>,
    val fitness: List<FitnessGap>,
    val isRestDay: Boolean,
    val saved: Boolean = false,
)

@Immutable
data class ReadingEntry(val subjectId: Long, val name: String, val colorIndex: Int, val pagesText: String)

@Immutable
data class HomeworkEntry(
    val id: Long,
    val title: String,
    val subject: String?,
    val subjectColor: Int?,
    val due: LocalDate,
    val done: Boolean,
)

@Immutable
data class FitnessGap(
    val exerciseId: Long,
    val name: String,
    val unit: ExerciseUnit,
    val done: Int,
    val target: Int,
) {
    val remaining: Int get() = (target - done).coerceAtLeast(0)
}

@Composable
fun CheckInScreen(
    state: CheckInUiState,
    onPagesChange: (subjectId: Long, text: String) -> Unit,
    onHomeworkDone: (id: Long, done: Boolean) -> Unit,
    onLogRemaining: (exerciseId: Long) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = Atlas.space
    val c = Atlas.colors
    StarFieldBackground(modifier.fillMaxSize(), starsPer10k = 0.6f) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.gutter),
    ) {
        Spacer(Modifier.height(space.l))
        Masthead(left = "The Evening Ledger", right = Formats.romanYear(state.date))
        Spacer(Modifier.height(space.l))
        Text("${Formats.weekday(state.date)}, ${Formats.dayMonth(state.date)}", style = Atlas.type.dateline, color = c.textSecondary)
        Text("The Day in Review", style = Atlas.type.display, color = c.text)
        Spacer(Modifier.height(space.xs))
        Text("Quid hodie egisti?", style = Atlas.type.aside, color = c.textSecondary)

        Spacer(Modifier.height(space.xl))
        Tally(state)
        Spacer(Modifier.height(space.xl))

        SectionTitle("I", "Reading") {
            Text("target ${state.readingMin}–${state.readingMax} pp", style = Atlas.type.time, color = Atlas.colors.textSecondary)
        }
        if (state.reading.isEmpty()) {
            Quiet("No academic subjects met today.")
        } else {
            state.reading.forEachIndexed { i, entry ->
                ReadingRow(entry, state.readingMin, state.readingMax, last = i == state.reading.lastIndex) {
                    onPagesChange(entry.subjectId, it)
                }
            }
        }

        Spacer(Modifier.height(space.xl))
        SectionTitle("II", "Homework") {
            Text("next 3 days", style = Atlas.type.time, color = Atlas.colors.textSecondary)
        }
        if (state.homework.isEmpty()) {
            Quiet("Nothing due before ${Formats.shortDate(state.date.plusDays(3))}.")
        } else {
            state.homework.forEachIndexed { i, hw ->
                HomeworkRow(hw, state.date, last = i == state.homework.lastIndex) { onHomeworkDone(hw.id, it) }
            }
        }

        Spacer(Modifier.height(space.xl))
        SectionTitle("III", "Exercise")
        when {
            state.isRestDay -> Quiet("Rest day. Nothing is owed.")
            state.fitness.isEmpty() -> Quiet("Every target met today.")
            else -> state.fitness.forEachIndexed { i, gap ->
                FitnessRow(gap, last = i == state.fitness.lastIndex) { onLogRemaining(gap.exerciseId) }
            }
        }

        Spacer(Modifier.height(space.xxl))
        AtlasButton(
            text = if (state.saved) "Saved" else "Close the day",
            onClick = onSave,
            style = ActionStyle.Filled,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(space.xxxl))
    }
    }
}

/** The day's reckoning at a glance: three small instruments, one per section. */
@Composable
private fun Tally(state: CheckInUiState) {
    val c = Atlas.colors
    val readingMet = state.reading.count { (it.pagesText.toIntOrNull() ?: 0) >= state.readingMin }
    val homeworkDone = state.homework.count { it.done }
    val owed = state.fitness.size
    fun ratio(done: Int, total: Int) = if (total == 0) 1f else done / total.toFloat()
    data class Dial(val label: String, val fraction: Float, val text: String, val numeric: Boolean)
    val dials = listOf(
        Dial("Reading", ratio(readingMet, state.reading.size), "$readingMet/${state.reading.size}", true),
        Dial("Homework", ratio(homeworkDone, state.homework.size), "$homeworkDone/${state.homework.size}", true),
        when {
            state.isRestDay -> Dial("Exercise", 1f, "rest", false)
            owed == 0 -> Dial("Exercise", 1f, "done", false)
            else -> Dial("Exercise", 0.5f, "$owed owed", false)
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dials.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val complete = d.fraction >= 1f
                OrbitRing(
                    progress = d.fraction, diameter = 92.dp, graduations = 24, majorEvery = 6,
                    color = if (complete) c.gold else c.accent, showBody = !complete,
                ) {
                    Text(
                        d.text,
                        style = if (d.numeric) Atlas.type.numeral.copy(fontSize = Atlas.type.title.fontSize) else Atlas.type.aside,
                        color = c.text,
                    )
                }
                Spacer(Modifier.height(Atlas.space.s))
                Text(d.label.uppercase(), style = Atlas.type.overline, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = Atlas.type.aside, color = Atlas.colors.textSecondary, modifier = Modifier.padding(vertical = Atlas.space.s))
}

@Composable
private fun ReadingRow(entry: ReadingEntry, min: Int, max: Int, last: Boolean, onChange: (String) -> Unit) {
    val pages = entry.pagesText.toIntOrNull() ?: 0
    val met = pages >= min
    RuledRow(showRule = !last) {
        OrbitRing(
            progress = pages / max.toFloat(),
            diameter = 48.dp,
            markFraction = min / max.toFloat(),
            color = if (met) Atlas.colors.gold else Atlas.colors.accent,
            arcWidth = 1.5.dp,
            showBody = false,
            graduations = 10,
            majorEvery = 10,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubjectMark(Atlas.colors.subject(entry.colorIndex))
                Text(entry.name, style = Atlas.type.title, color = Atlas.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when {
                    pages == 0 -> "not logged"
                    met -> "counts for today"
                    else -> "${min - pages} short of the minimum"
                },
                style = Atlas.type.bodySmall,
                color = if (met) Atlas.colors.textSecondary else Atlas.colors.accentText,
            )
        }
        NumberEntry(entry.pagesText, onChange, suffix = "pp")
    }
}

@Composable
private fun HomeworkRow(hw: HomeworkEntry, today: LocalDate, last: Boolean, onDone: (Boolean) -> Unit) {
    RuledRow(showRule = !last) {
        Column(Modifier.weight(1f)) {
            Text(hw.title, style = Atlas.type.title, color = Atlas.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hw.subjectColor != null) SubjectMark(Atlas.colors.subject(hw.subjectColor))
                if (hw.subject != null) {
                    Text(
                        hw.subject, style = Atlas.type.bodySmall, color = Atlas.colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("·", style = Atlas.type.bodySmall, color = Atlas.colors.textSecondary)
                }
                Text(
                    "due ${Formats.relativeDay(hw.due, today)}",
                    style = Atlas.type.bodySmall,
                    maxLines = 1,
                    color = if (hw.due == today && !hw.done) Atlas.colors.accentText else Atlas.colors.textSecondary,
                )
            }
        }
        ChoiceRow(listOf(true to "Done", false to "Not yet"), selected = hw.done, onSelect = onDone)
    }
}

@Composable
private fun FitnessRow(gap: FitnessGap, last: Boolean, onLog: () -> Unit) {
    val unit = if (gap.unit == ExerciseUnit.SECONDS) "s" else ""
    RuledRow(showRule = !last) {
        OrbitRing(progress = gap.done / gap.target.toFloat(), diameter = 28.dp, showBody = false)
        Column(Modifier.weight(1f)) {
            Text(gap.name, style = Atlas.type.title, color = Atlas.colors.text)
            Text(
                "${gap.done}$unit of ${gap.target}$unit  ·  ${gap.remaining}$unit to go",
                style = Atlas.type.time,
                color = Atlas.colors.textSecondary,
            )
        }
        Spacer(Modifier.width(Atlas.space.xs))
        AtlasButton("Log ${gap.remaining}$unit", onLog, style = ActionStyle.Outlined, textStyle = Atlas.type.time)
    }
}

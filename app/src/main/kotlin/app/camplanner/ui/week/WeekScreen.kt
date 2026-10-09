package app.camplanner.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.Glyphs
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.Periods
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.format.Formats
import app.camplanner.ui.today.TodayStateBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Immutable
data class WeekUiState(
    val start: LocalDate,
    val today: LocalDate,
    val termLabel: String?,
    val days: List<WeekDay>,
)

@Immutable
data class WeekDay(val date: LocalDate, val entries: List<WeekEntry>)

@Immutable
data class WeekEntry(
    val id: Long,
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val location: String?,
    val subjectColor: Int?,
)

class WeekViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val offset = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<WeekUiState?> = combine(offset, graph.settings.settings) { o, s -> o to s }
        .flatMapLatest { (o, settings) ->
            val today = LocalDate.now()
            val start = Periods.week(today, settings.weekStart).start.plusWeeks(o)
            combine(graph.timetable.observeDays(start, start.plusDays(6), zone), graph.timetable.observeSubjects()) { events, subjects ->
                val colors = subjects.associate { it.id to it.colorIndex }
                WeekUiState(
                    start = start,
                    today = today,
                    termLabel = TodayStateBuilder.termLabel(start.plusDays(3), settings),
                    days = (0L..6L).map { i ->
                        val d = start.plusDays(i)
                        WeekDay(d, DayPlanner.timedEventsOn(events, d, zone).map { e ->
                            WeekEntry(
                                e.id, e.start.atZone(zone).toLocalTime(), e.end.atZone(zone).toLocalTime(),
                                e.title, e.location, e.subjectId?.let(colors::get),
                            )
                        })
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shift(weeks: Long) { offset.value += weeks }
    fun reset() { offset.value = 0 }
}

@Composable
fun WeekRoute(onOpenDay: (LocalDate) -> Unit, onOpenEvent: (Long) -> Unit, onAdd: (LocalDate) -> Unit) {
    val vm = appViewModel { graph, _ -> WeekViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { WeekScreen(it, vm::shift, vm::reset, onOpenDay, onOpenEvent, onAdd) }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)

@Composable
fun WeekScreen(
    state: WeekUiState,
    onShift: (Long) -> Unit,
    onThisWeek: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenEvent: (Long) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    val end = state.start.plusDays(6)
    val count = state.days.sumOf { it.entries.size }
    AtlasPage(
        mastheadLeft = state.termLabel ?: "The Week",
        mastheadRight = Formats.count(count, "engagement"),
        dateline = "${dayMonth.format(state.start)} to ${dayMonth.format(end)}",
        title = "The Week",
        graticule = true,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AtlasButton("Earlier", { onShift(-1) }, style = ActionStyle.Outlined, glyph = Glyphs.ChevronLeft)
            Spacer(Modifier.weight(1f))
            val thisWeek = !state.today.isBefore(state.start) && !state.today.isAfter(end)
            if (!thisWeek) AtlasButton("This week", onThisWeek, style = ActionStyle.Text)
            Spacer(Modifier.weight(1f))
            AtlasButton("Later", { onShift(1) }, style = ActionStyle.Outlined)
        }
        Spacer(Modifier.height(Atlas.space.l))
        Hairline()
        state.days.forEach { day ->
            DayBlock(day, isToday = day.date == state.today, onOpenDay, onOpenEvent)
        }
        Spacer(Modifier.height(Atlas.space.xl))
        AtlasButton(
            "Add an engagement", { onAdd(if (state.days.any { it.date == state.today }) state.today else state.start) },
            style = ActionStyle.Filled, modifier = Modifier.fillMaxWidth(), glyph = Glyphs.Plus,
        )
    }
}

@Composable
private fun DayBlock(day: WeekDay, isToday: Boolean, onOpenDay: (LocalDate) -> Unit, onOpenEvent: (Long) -> Unit) {
    val c = Atlas.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenDay(day.date) }
            .padding(vertical = Atlas.space.l),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.widthIn(min = 64.dp)) {
            Text(
                day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK).uppercase(),
                style = Atlas.type.overline,
                color = if (isToday) c.rubric else c.textSecondary,
            )
            Text("${day.date.dayOfMonth}", style = Atlas.type.numeral, color = if (isToday) c.gold else c.text)
        }
        Spacer(Modifier.width(Atlas.space.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Atlas.space.s)) {
            if (day.entries.isEmpty()) {
                Text("No engagements", style = Atlas.type.aside, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
            }
            day.entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenEvent(e.id) },
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(Formats.time(e.start), style = Atlas.type.time, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp).widthIn(min = 48.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (e.subjectColor != null) SubjectMark(c.subject(e.subjectColor))
                            Text(e.title, style = Atlas.type.title.copy(fontSize = Atlas.type.body.fontSize * 1.2f), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (e.location != null) {
                            Text(e.location, style = Atlas.type.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    Hairline()
}

package app.camplanner.ui.calendar

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.FileDownload
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.kit.AddButton
import app.camplanner.designsystem.kit.DayCell
import app.camplanner.designsystem.kit.DayStrip
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.DayPlanner
import app.camplanner.ui.common.DatePick
import app.camplanner.ui.common.ScheduleItem
import app.camplanner.ui.common.ScheduleRow
import app.camplanner.ui.common.TabPage
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.common.minuteTicker
import app.camplanner.ui.common.scheduleRows
import app.camplanner.ui.format.Formats
import app.camplanner.ui.today.TodayBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

data class CalendarState(
    val today: LocalDate,
    val selected: LocalDate,
    val termLine: String?,
    val week: List<DayCell>,
    val rows: List<ScheduleRow>,
)

class CalendarViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val selected = MutableStateFlow(LocalDate.now(zone))

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CalendarState?> = combine(selected, graph.settings.settings) { day, settings -> day to settings }
        .flatMapLatest { (day, settings) ->
            val first = day.with(TemporalAdjusters.previousOrSame(settings.weekStart))
            val last = first.plusDays(6)
            combine(
                graph.timetable.observeDays(first, last, zone),
                graph.timetable.observeSubjects(),
                graph.locations.observeAll(),
                minuteTicker(),
            ) { events, subjects, _, now ->
                val today = now.toLocalDate()
                CalendarState(
                    today = today,
                    selected = day,
                    termLine = TodayBuilder.termLine(day, settings),
                    week = (0L..6L).map { i ->
                        val d = first.plusDays(i)
                        DayCell(d, DayPlanner.timedEventsOn(events, d, zone).size, d == today)
                    },
                    rows = scheduleRows(
                        day, events, subjects.associateBy { it.id }, settings, graph.locations.locator(), zone,
                        now.atZone(zone).toInstant(),
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(date: LocalDate) {
        selected.value = date
    }

    fun shift(days: Long) {
        selected.value = selected.value.plusDays(days)
    }
}

@Composable
fun CalendarRoute(onOpenEvent: (Long) -> Unit, onAdd: (LocalDate) -> Unit, onImport: () -> Unit) {
    val vm = appViewModel { graph, _ -> CalendarViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { CalendarScreen(it, vm::select, vm::shift, onOpenEvent, onAdd, onImport) }
}

@Composable
fun CalendarScreen(
    s: CalendarState,
    onSelect: (LocalDate) -> Unit,
    onShift: (Long) -> Unit,
    onOpenEvent: (Long) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onImport: () -> Unit,
) {
    val c = Atlas.colors
    var picking by remember { mutableStateOf(false) }
    val swipe = with(LocalDensity.current) { 72.dp.toPx() }
    TabPage(
        header = {
            LargeHeader(
                title = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK).format(s.selected),
                overline = s.termLine,
                actions = {
                    if (s.selected != s.today) TertiaryButton("Today", { onSelect(s.today) })
                    HeaderAction(Icons.Outlined.EditCalendar, "Go to a date", { picking = true })
                    HeaderAction(Icons.Outlined.FileDownload, "Import timetable", onImport)
                },
            )
        },
        fab = { AddButton("New event", Icons.Outlined.Add, { onAdd(s.selected) }) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onShift(-7) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous week", tint = c.textSecondary) }
            Text(
                "${Formats.dayMonthShort(s.week.first().date)} – ${Formats.dayMonthShort(s.week.last().date)}",
                style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            IconButton({ onShift(7) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next week", tint = c.textSecondary) }
        }
        DayStrip(s.week, s.selected, onSelect, Modifier.swipe(swipe) { onShift(7L * it) })

        // Swiping the day itself moves one day at a time.
        Column(Modifier.swipe(swipe) { onShift(it.toLong()) }) {
            GroupHeader(dayTitle(s.selected, s.today)) {
                Text(Formats.count(s.rows.count { !it.cancelled }, "event"), style = Atlas.type.supporting, color = c.textSecondary)
            }
            Group {
                if (s.rows.isEmpty()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("A free day.", style = Atlas.type.reading, color = c.textSecondary)
                        TertiaryButton("Add an event", { onAdd(s.selected) }, icon = Icons.Outlined.Add)
                    }
                }
                s.rows.forEachIndexed { i, row ->
                    ScheduleItem(row, onClick = { onOpenEvent(row.id) })
                    if (i != s.rows.lastIndex) GroupDivider(inset = 76.dp)
                }
            }
        }
    }
    if (picking) DatePick(s.selected, { onSelect(it); picking = false }, { picking = false })
}

/** "Today, 11 October" near today, "Thursday 15 October" otherwise. */
private fun dayTitle(date: LocalDate, today: LocalDate): String =
    if (abs(date.toEpochDay() - today.toEpochDay()) <= 1) "${Formats.dayLabel(date, today)}, ${Formats.dayMonth(date)}"
    else "${Formats.weekday(date)} ${Formats.dayMonth(date)}"

/** Calls [onSwipe] with +1 for a swipe to the left (forward) and −1 to the right. */
private fun Modifier.swipe(threshold: Float, onSwipe: (Int) -> Unit) = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (abs(total) > threshold) onSwipe(if (total < 0) 1 else -1) },
        onHorizontalDrag = { change, amount ->
            change.consume()
            total += amount
        },
    )
}

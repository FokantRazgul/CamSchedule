package app.camplanner.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AlmanacLine
import app.camplanner.designsystem.components.Astrolabe
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.ChartPoint
import app.camplanner.designsystem.components.Constellation
import app.camplanner.designsystem.components.DialArc
import app.camplanner.designsystem.components.Glyphs
import app.camplanner.designsystem.components.Masthead
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.components.StarFieldBackground
import app.camplanner.designsystem.components.StarState
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.TravelMode
import app.camplanner.ui.format.Formats
import java.time.LocalDate
import java.time.LocalTime

@Immutable
data class TodayUiState(
    val date: LocalDate,
    val items: List<TodayItem>,
    val now: LocalTime? = null,
    val morningAlarm: LocalTime? = null,
    val checkIn: LocalTime? = null,
    /** "Michaelmas Term · Week II"; null outside term or when no term dates are set. */
    val termLabel: String? = null,
    val almanac: AlmanacInfo? = null,
    /** Set when a permission is missing; shown as a quiet line under the heading. */
    val attention: String? = null,
)

@Immutable
data class AlmanacInfo(
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val moonName: String,
    val moonIllumination: Float,
    val moonWaxing: Boolean,
)

@Immutable
sealed interface TodayItem {
    val key: String

    data class Event(
        override val key: String,
        val id: Long,
        val title: String,
        val subject: String?,
        val subjectColor: Int?,
        val start: LocalTime,
        val end: LocalTime,
        val location: String?,
        val state: StarState,
        val mode: TravelMode,
        val travelMinutes: Long?,
        val leaveAt: LocalTime?,
        val note: String? = null,
    ) : TodayItem

    data class Gap(override val key: String, val minutes: Long) : TodayItem

    data class Now(override val key: String, val time: LocalTime) : TodayItem
}

/** Cambridge, as degrees and minutes for the masthead. */
private const val COORDINATES = "52°12′ N  ·  0°07′ E"

@Composable
fun TodayScreen(
    state: TodayUiState,
    onRoute: (eventId: Long) -> Unit,
    onOpenEvent: (eventId: Long) -> Unit,
    onOpenCheckIn: () -> Unit,
    onFixPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = Atlas.space
    val c = Atlas.colors
    val events = state.items.filterIsInstance<TodayItem.Event>()
    StarFieldBackground(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter),
        ) {
            Spacer(Modifier.height(space.l))
            Masthead(left = "Cam Planner", right = COORDINATES)
            Spacer(Modifier.height(space.l))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    (state.termLabel ?: "Vacation").uppercase(),
                    style = Atlas.type.overline, color = c.rubric, modifier = Modifier.weight(1f),
                )
                Text(Formats.romanYear(state.date), style = Atlas.type.overline, color = c.textSecondary)
            }
            Spacer(Modifier.height(space.m))
            Text(Formats.weekday(state.date), style = Atlas.type.dateline, color = c.textSecondary)
            Text(Formats.dayMonth(state.date), style = Atlas.type.display, color = c.text)

            Spacer(Modifier.height(space.xl))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Astrolabe(
                    arcs = events.map { DialArc(it.start.minuteOfDay, it.end.minuteOfDay, c.subject(it.subjectColor ?: 0), it.state) },
                    nowMinute = state.now?.minuteOfDay,
                    sunriseMinute = state.almanac?.sunrise?.minuteOfDay,
                    sunsetMinute = state.almanac?.sunset?.minuteOfDay,
                    diameter = 320.dp,
                    description = "The day as a dial: ${Formats.count(events.size, "event")}",
                ) { DialCentre(state, events) }
            }
            Spacer(Modifier.height(space.l))
            state.almanac?.let { a ->
                AlmanacLine(
                    sunrise = a.sunrise?.let(Formats::time),
                    sunset = a.sunset?.let(Formats::time),
                    moonName = a.moonName,
                    moonIllumination = a.moonIllumination,
                    moonWaxing = a.moonWaxing,
                )
                Spacer(Modifier.height(space.s))
            }
            Text(summaryLine(state, events), style = Atlas.type.time, color = c.textSecondary)
            if (state.attention != null) {
                Spacer(Modifier.height(space.s))
                AtlasButton(state.attention, onFixPermissions, style = ActionStyle.Text, contentPadding = PaddingValues(0.dp))
            }

            Spacer(Modifier.height(space.xxl))
            SectionTitle("I", "The Order of the Day") {
                Text(Formats.count(events.size, "engagement"), style = Atlas.type.time, color = c.textSecondary)
            }
            Spacer(Modifier.height(space.l))
            if (events.isEmpty()) {
                Text("A clear sky. Nothing in the timetable today.", style = Atlas.type.aside, color = c.textSecondary)
            } else {
                Constellation(
                    items = state.items,
                    point = ::chartPoint,
                    leading = { item -> TimeLabel(item) },
                    rowContent = { item ->
                        when (item) {
                            is TodayItem.Event -> EventRow(item, onRoute = { onRoute(item.id) }, onOpen = { onOpenEvent(item.id) })
                            is TodayItem.Gap -> GapRow(item)
                            is TodayItem.Now -> NowRow()
                        }
                    },
                )
            }

            if (state.checkIn != null) {
                Spacer(Modifier.height(space.xl))
                SectionTitle("II", "The Evening Review")
                Row(Modifier.fillMaxWidth().padding(vertical = space.m), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reading, homework and exercise, at", style = Atlas.type.aside, color = c.textSecondary)
                        Text(Formats.time(state.checkIn), style = Atlas.type.numeral, color = c.text)
                    }
                    AtlasButton("Open", onOpenCheckIn)
                }
            }
            Spacer(Modifier.height(space.xxxl))
        }
    }
}

private val LocalTime.minuteOfDay: Int get() = hour * 60 + minute

@Composable
private fun DialCentre(state: TodayUiState, events: List<TodayItem.Event>) {
    val c = Atlas.colors
    val now = state.now
    val current = events.firstOrNull { it.state == StarState.CURRENT }
    val next = events.firstOrNull { it.state == StarState.FUTURE }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (now != null) "NOW" else "FIRST", style = Atlas.type.overline, color = c.textSecondary)
        Text(
            Formats.time(now ?: events.firstOrNull()?.start ?: LocalTime.NOON),
            style = Atlas.type.numeral.copy(fontSize = Atlas.type.headline.fontSize * 1.25f),
            color = c.text,
        )
        val line = when {
            current != null -> "${current.title}\nuntil ${Formats.time(current.end)}"
            next != null -> "${next.title}\nat ${Formats.time(next.start)}"
            else -> "The day's work is done"
        }
        Spacer(Modifier.height(2.dp))
        Text(
            line, style = Atlas.type.aside.copy(fontSize = Atlas.type.bodySmall.fontSize * 1.15f),
            color = if (current != null) c.gold else c.textSecondary,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun summaryLine(state: TodayUiState, events: List<TodayItem.Event>): String = buildList {
    state.morningAlarm?.let { add("Alarm ${Formats.time(it)}") }
    events.firstOrNull()?.let { add("first at ${Formats.time(it.start)}") }
    events.lastOrNull()?.let { add("last ends ${Formats.time(it.end)}") }
}.joinToString("  ·  ")

private fun chartPoint(item: TodayItem): ChartPoint = when (item) {
    is TodayItem.Event -> {
        val minutes = java.time.Duration.between(item.start, item.end).toMinutes()
        ChartPoint.Star(item.state, magnitude = (minutes / 180f).coerceIn(0.15f, 1f), seed = item.key.hashCode())
    }
    is TodayItem.Now -> ChartPoint.Now
    is TodayItem.Gap -> ChartPoint.None
}

@Composable
private fun TimeLabel(item: TodayItem) {
    when (item) {
        is TodayItem.Event -> Column(Modifier.padding(top = 4.dp)) {
            val color = if (item.state == StarState.PAST) Atlas.colors.textSecondary else Atlas.colors.text
            Text(Formats.time(item.start), style = Atlas.type.time, color = color)
            Text(Formats.time(item.end), style = Atlas.type.time, color = Atlas.colors.textSecondary)
        }
        is TodayItem.Now -> Text(
            Formats.time(item.time), style = Atlas.type.time, color = Atlas.colors.accentText,
            modifier = Modifier.padding(top = 4.dp),
        )
        is TodayItem.Gap -> Unit
    }
}

@Composable
private fun EventRow(event: TodayItem.Event, onRoute: () -> Unit, onOpen: () -> Unit) {
    val c = Atlas.colors
    val past = event.state == StarState.PAST
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Atlas.space.xl)
            .clickable(onClick = onOpen),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                event.title,
                style = Atlas.type.title,
                color = when {
                    event.state == StarState.CURRENT -> c.gold
                    past -> c.textSecondary
                    else -> c.text
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (event.location != null && !past) {
                Spacer(Modifier.width(Atlas.space.s))
                AtlasButton(
                    "Route", onRoute, glyph = Glyphs.Route, style = ActionStyle.Outlined,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(Atlas.space.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (event.subjectColor != null) SubjectMark(c.subject(event.subjectColor))
            val line = listOfNotNull(event.subject, event.location).joinToString("  ·  ")
            if (line.isNotEmpty()) {
                Text(line, style = Atlas.type.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!past && event.leaveAt != null && event.travelMinutes != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Leave ${Formats.time(event.leaveAt)}  ·  ${Formats.mode(event.mode)} ${event.travelMinutes} min",
                style = Atlas.type.time,
                color = if (event.state == StarState.CURRENT) c.textSecondary else c.accentText,
            )
        }
        if (event.note != null) {
            Spacer(Modifier.height(2.dp))
            Text(event.note, style = Atlas.type.aside, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GapRow(gap: TodayItem.Gap) {
    Text(
        "${Formats.duration(gap.minutes)} at liberty",
        style = Atlas.type.aside,
        color = Atlas.colors.textSecondary,
        modifier = Modifier.padding(bottom = Atlas.space.xl),
    )
}

@Composable
private fun NowRow() {
    Text(
        "the present hour",
        style = Atlas.type.aside,
        color = Atlas.colors.accentText,
        modifier = Modifier.padding(bottom = Atlas.space.xl),
    )
}

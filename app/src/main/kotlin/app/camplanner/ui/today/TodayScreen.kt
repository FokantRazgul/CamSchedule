package app.camplanner.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import app.camplanner.designsystem.components.ChartPoint
import app.camplanner.designsystem.components.Constellation
import app.camplanner.designsystem.components.Glyphs
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.ScreenHeading
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
    val morningAlarm: LocalTime? = null,
    val checkIn: LocalTime? = null,
    /** Set when a permission is missing; shown as a quiet line under the heading. */
    val attention: String? = null,
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
    StarFieldBackground(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter),
        ) {
            Spacer(Modifier.height(space.xl))
            ScreenHeading(
                title = Formats.dayMonth(state.date),
                overline = Formats.weekday(state.date),
            )
            Spacer(Modifier.height(space.s))
            AlmanacLine(state)
            if (state.attention != null) {
                Spacer(Modifier.height(space.s))
                AtlasButton(state.attention, onFixPermissions, style = ActionStyle.Text, contentPadding = PaddingValues(0.dp))
            }
            Spacer(Modifier.height(space.xl))
            Hairline()
            Spacer(Modifier.height(space.l))

            if (state.items.none { it is TodayItem.Event }) {
                Text("A clear sky. Nothing in the timetable today.", style = Atlas.type.aside, color = Atlas.colors.textSecondary)
            } else {
                Constellation(
                    items = state.items,
                    point = ::chartPoint,
                    leading = { item -> TimeLabel(item) },
                    rowContent = { item ->
                        when (item) {
                            is TodayItem.Event -> EventRow(item, onRoute = { onRoute(item.id) }, onOpen = { onOpenEvent(item.id) })
                            is TodayItem.Gap -> GapRow(item)
                            is TodayItem.Now -> NowRow(item)
                        }
                    },
                )
            }

            if (state.checkIn != null) {
                Spacer(Modifier.height(space.xl))
                Hairline()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("EVENING CHECK-IN", style = Atlas.type.overline, color = Atlas.colors.textSecondary)
                        Text(Formats.time(state.checkIn), style = Atlas.type.time, color = Atlas.colors.text)
                    }
                    AtlasButton("Open", onOpenCheckIn, style = ActionStyle.Text)
                }
            }
            Spacer(Modifier.height(space.xxxl))
        }
    }
}

private fun chartPoint(item: TodayItem): ChartPoint = when (item) {
    is TodayItem.Event -> {
        val minutes = java.time.Duration.between(item.start, item.end).toMinutes()
        ChartPoint.Star(item.state, magnitude = (minutes / 180f).coerceIn(0.15f, 1f), seed = item.key.hashCode())
    }
    is TodayItem.Now -> ChartPoint.Now
    is TodayItem.Gap -> ChartPoint.None
}

/** "Alarm 07:30 · 4 events · first at 09:00" */
@Composable
private fun AlmanacLine(state: TodayUiState) {
    val events = state.items.filterIsInstance<TodayItem.Event>()
    val parts = buildList {
        state.morningAlarm?.let { add("Alarm ${Formats.time(it)}") }
        add(Formats.count(events.size, "event"))
        events.firstOrNull()?.let { add("first at ${Formats.time(it.start)}") }
    }
    Text(parts.joinToString("  ·  "), style = Atlas.type.time, color = Atlas.colors.textSecondary)
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
            .padding(bottom = Atlas.space.l)
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
        "${Formats.duration(gap.minutes)} free",
        style = Atlas.type.aside,
        color = Atlas.colors.textSecondary,
        modifier = Modifier.padding(bottom = Atlas.space.l),
    )
}

@Composable
private fun NowRow(now: TodayItem.Now) {
    Text(
        "now",
        style = Atlas.type.aside,
        color = Atlas.colors.accentText,
        modifier = Modifier.padding(bottom = Atlas.space.l),
    )
}

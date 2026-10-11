package app.camplanner.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.kit.SubjectBar
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.DayPlanner
import app.camplanner.model.AppSettings
import app.camplanner.model.CalendarEvent
import app.camplanner.model.GeoPoint
import app.camplanner.model.Subject
import app.camplanner.ui.format.Formats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class When { PAST, NOW, LATER }

/** One event as a day list shows it. */
@Immutable
data class ScheduleRow(
    val id: Long,
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val place: String?,
    val subjectColor: Int?,
    val state: When,
    val repeats: Boolean = false,
    val cancelled: Boolean = false,
    /** "Leave 13:45 · bike 10 min" */
    val leave: String? = null,
    val leaveAt: LocalTime? = null,
    val travelMinutes: Long? = null,
    val mode: app.camplanner.model.TravelMode = app.camplanner.model.TravelMode.WALK,
)

/** Builds the rows for one day, with travel worked out between consecutive places. */
fun scheduleRows(
    date: LocalDate,
    events: List<CalendarEvent>,
    subjects: Map<Long, Subject>,
    settings: AppSettings,
    locate: (String) -> GeoPoint?,
    zone: ZoneId,
    now: Instant,
): List<ScheduleRow> {
    val all = DayPlanner.timedEventsOn(events, date, zone, includeCancelled = true)
    val legs = DayPlanner.legs(
        all.filter { !it.cancelled }, settings.home, settings.homeAddress, settings.defaultTravelMode,
        Duration.ofMinutes(settings.bufferMinutes.toLong()), locate,
    ).associateBy { it.event.id }
    return all.map { e ->
        val leg = legs[e.id]
        val leaveAt = leg?.leaveAt?.atZone(zone)?.toLocalTime()
        val travel = leg?.travel?.toMinutes()?.takeIf { it > 0 }
        ScheduleRow(
            id = e.id,
            start = e.start.atZone(zone).toLocalTime(),
            end = e.end.atZone(zone).toLocalTime(),
            title = e.title,
            place = e.location,
            subjectColor = e.subjectId?.let(subjects::get)?.colorIndex,
            state = when {
                !e.end.isAfter(now) -> When.PAST
                !e.start.isAfter(now) -> When.NOW
                else -> When.LATER
            },
            repeats = e.seriesId != null,
            cancelled = e.cancelled,
            leave = if (leaveAt != null && travel != null) "Leave ${Formats.time(leaveAt)} · ${Formats.mode(leg.mode)} $travel min" else null,
            leaveAt = leaveAt,
            travelMinutes = travel,
            mode = leg?.mode ?: e.travelModeOverride ?: settings.defaultTravelMode,
        )
    }
}

/** One event in a day list: time column, subject bar, title and place, leave line. */
@Composable
fun ScheduleItem(row: ScheduleRow, onClick: () -> Unit) {
    val c = Atlas.colors
    val dim = row.state == When.PAST || row.cancelled
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 48.dp)) {
            Text(Formats.time(row.start), style = Atlas.type.itemTitle.copy(fontFeatureSettings = "tnum"), color = if (dim) c.textSecondary else c.text)
            Text(Formats.time(row.end), style = Atlas.type.supporting, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        SubjectBar(
            when {
                row.state == When.NOW && !row.cancelled -> c.gold
                row.subjectColor != null -> c.subject(row.subjectColor)
                else -> c.hairline
            }.let { if (dim) it.copy(alpha = 0.4f) else it },
            height = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title, style = Atlas.type.itemTitle, color = if (dim) c.textSecondary else c.text,
                    textDecoration = if (row.cancelled) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (row.repeats) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Repeat, contentDescription = "Repeats", tint = c.textSecondary, modifier = Modifier.size(15.dp))
                }
            }
            when {
                row.cancelled -> Text("Cancelled", style = Atlas.type.supporting, color = c.accentText)
                else -> {
                    row.place?.let { Text(it, style = Atlas.type.supporting, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (row.leave != null && row.state == When.LATER) Text(row.leave, style = Atlas.type.supporting, color = c.accentText)
                }
            }
        }
        if (row.state == When.NOW && !row.cancelled) {
            Text("Now", style = Atlas.type.caption, color = c.gold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

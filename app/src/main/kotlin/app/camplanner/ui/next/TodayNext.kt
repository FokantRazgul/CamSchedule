package app.camplanner.ui.next

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.MoonGlyph
import app.camplanner.designsystem.components.SunGlyph
import app.camplanner.designsystem.kit.AddButton
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.ProgressBar
import app.camplanner.designsystem.kit.StatTile
import app.camplanner.designsystem.kit.SubjectBar
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.TravelMode
import app.camplanner.ui.format.Formats
import java.time.LocalDate
import java.time.LocalTime

enum class When { PAST, NOW, LATER }

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
    /** "Leave 13:45 · bike 10 min" */
    val leave: String? = null,
)

@Immutable
data class Hero(
    val nowTitle: String?,
    val nowUntil: LocalTime?,
    val nowProgress: Float,
    val nowPlace: String?,
    val next: ScheduleRow?,
    val nextIn: String?,
    val nextLeaveAt: LocalTime?,
    val nextTravelMinutes: Long?,
    val nextMode: TravelMode,
)

@Immutable
data class DueRow(val id: Long, val title: String, val detail: String, val overdue: Boolean, val done: Boolean)

@Immutable
data class TodayNextState(
    val date: LocalDate,
    val termLine: String?,
    val hero: Hero?,
    val schedule: List<ScheduleRow>,
    val alarmTomorrow: LocalTime?,
    val alarmSkipped: Boolean,
    val checkIn: LocalTime?,
    val pushUps: Pair<Int, Int>?,
    val dueSoon: List<DueRow>,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val moonName: String,
    val moonIllumination: Float,
    val moonWaxing: Boolean,
)

@Composable
fun TodayNextScreen(
    s: TodayNextState,
    onTab: (Tab) -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onRoute: (Long) -> Unit = {},
    onAdd: () -> Unit = {},
    onToggleAlarm: () -> Unit = {},
    onOpenCheckIn: () -> Unit = {},
    onOpenFitness: () -> Unit = {},
    onToggleDue: (Long, Boolean) -> Unit = { _, _ -> },
    onSettings: () -> Unit = {},
) {
    val c = Atlas.colors
    TabScaffold(Tab.TODAY, onTab, fab = { AddButton("Add", Icons.Outlined.Add, onAdd) }) {
        LargeHeader(
            title = "${Formats.weekday(s.date)}\n${Formats.dayMonth(s.date)}",
            overline = s.termLine,
            actions = { HeaderAction(Icons.Outlined.Settings, "Settings", onSettings) },
        )
        Paged {
            s.hero?.let { HeroCard(it, onRoute) }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "Alarm",
                    if (s.alarmSkipped) "Off" else s.alarmTomorrow?.let(Formats::time) ?: "None",
                    Modifier.weight(1f),
                    detail = if (s.alarmSkipped) "Tap to restore" else "Tap to skip",
                    valueColor = if (s.alarmSkipped) c.textSecondary else c.text,
                    onClick = onToggleAlarm,
                )
                StatTile("Review", s.checkIn?.let(Formats::time) ?: "—", Modifier.weight(1f), detail = "Open now", onClick = onOpenCheckIn)
                s.pushUps?.let { (done, target) ->
                    StatTile("Push-ups", "$done", Modifier.weight(1f), detail = "of $target", valueColor = if (done >= target) c.gold else c.text, onClick = onOpenFitness)
                }
            }

            GroupHeader("Schedule") {
                Text(Formats.count(s.schedule.size, "event"), style = Atlas.type.supporting, color = c.textSecondary)
            }
            Group {
                if (s.schedule.isEmpty()) {
                    Text("Nothing scheduled today.", style = Atlas.type.reading, color = c.textSecondary, modifier = Modifier.padding(16.dp))
                }
                s.schedule.forEachIndexed { i, row ->
                    ScheduleItem(row, onClick = { onOpenEvent(row.id) })
                    if (i != s.schedule.lastIndex) GroupDivider(inset = 80.dp)
                }
            }

            if (s.dueSoon.isNotEmpty()) {
                GroupHeader("Due soon")
                Group {
                    s.dueSoon.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = d.done, onCheckedChange = { onToggleDue(d.id, it) },
                                colors = CheckboxDefaults.colors(checkedColor = c.gold, uncheckedColor = c.textSecondary, checkmarkColor = c.background),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(d.title, style = Atlas.type.itemTitle, color = if (d.done) c.textSecondary else c.text)
                                Text(d.detail, style = Atlas.type.supporting, color = if (d.overdue) c.accentText else c.textSecondary)
                            }
                        }
                        if (i != s.dueSoon.lastIndex) GroupDivider(inset = 56.dp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                SunGlyph()
                Text(
                    listOfNotNull(s.sunrise?.let { "Sunrise ${Formats.time(it)}" }, s.sunset?.let { "sunset ${Formats.time(it)}" }).joinToString(", "),
                    style = Atlas.type.supporting, color = c.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                MoonGlyph(s.moonIllumination, s.moonWaxing)
                Text(s.moonName, style = Atlas.type.supporting, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun HeroCard(h: Hero, onRoute: (Long) -> Unit) {
    val c = Atlas.colors
    Group {
        Column(Modifier.padding(18.dp)) {
            if (h.nowTitle != null) {
                Text("NOW  ·  until ${h.nowUntil?.let(Formats::time)}", style = Atlas.type.caption, color = c.gold)
                Spacer(Modifier.height(4.dp))
                Text(h.nowTitle, style = Atlas.type.heroTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                h.nowPlace?.let { Text(it, style = Atlas.type.supporting, color = c.textSecondary) }
                Spacer(Modifier.height(12.dp))
                ProgressBar(h.nowProgress)
            }
            val next = h.next
            if (next != null) {
                if (h.nowTitle != null) Spacer(Modifier.height(18.dp))
                Text(
                    "NEXT  ·  ${h.nextIn ?: Formats.time(next.start)}",
                    style = Atlas.type.caption, color = if (h.nowTitle == null) c.gold else c.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    next.title,
                    style = if (h.nowTitle == null) Atlas.type.heroTitle else Atlas.type.groupTitle.copy(fontSize = Atlas.type.groupTitle.fontSize * 1.1f),
                    color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull("${Formats.time(next.start)}–${Formats.time(next.end)}", next.place).joinToString("  ·  "),
                    style = Atlas.type.supporting, color = c.textSecondary,
                )
                if (next.place != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (h.nextMode == TravelMode.BIKE) Icons.Outlined.DirectionsBike else Icons.AutoMirrored.Outlined.DirectionsWalk,
                            contentDescription = null, tint = c.accentText, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                h.nextLeaveAt?.let { "Leave at ${Formats.time(it)}" } ?: "Travel time unknown",
                                style = Atlas.type.itemTitle, color = c.text,
                            )
                            h.nextTravelMinutes?.let { Text("$it min ${Formats.mode(h.nextMode)} + buffer", style = Atlas.type.supporting, color = c.textSecondary) }
                        }
                        PrimaryButton("Route", { onRoute(next.id) }, icon = Icons.Outlined.Directions)
                    }
                }
            }
            if (h.nowTitle == null && next == null) {
                Text("Nothing more today.", style = Atlas.type.heroTitle, color = c.textSecondary)
            }
        }
    }
}

/** One event in a day list: time column, subject bar, title and place, leave line. */
@Composable
fun ScheduleItem(row: ScheduleRow, onClick: () -> Unit, onRoute: (() -> Unit)? = null) {
    val c = Atlas.colors
    val dim = row.state == When.PAST
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
                row.state == When.NOW -> c.gold
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
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (row.repeats) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Repeat, contentDescription = "Repeats", tint = c.textSecondary, modifier = Modifier.size(15.dp))
                }
            }
            row.place?.let { Text(it, style = Atlas.type.supporting, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (row.leave != null && !dim) Text(row.leave, style = Atlas.type.supporting, color = c.accentText)
        }
        if (onRoute != null && row.place != null && !dim) {
            IconButton(onClick = onRoute) { Icon(Icons.Outlined.Directions, "Route", tint = c.accentText) }
        }
        if (row.state == When.NOW) {
            Box(Modifier.padding(start = 6.dp)) { Text("NOW", style = Atlas.type.caption, color = c.gold) }
        }
    }
}

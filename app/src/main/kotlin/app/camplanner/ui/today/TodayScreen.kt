package app.camplanner.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.camplanner.designsystem.components.MoonGlyph
import app.camplanner.designsystem.components.SunGlyph
import app.camplanner.designsystem.kit.AddButton
import app.camplanner.designsystem.kit.Banner
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.ProgressBar
import app.camplanner.designsystem.kit.StatTile
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.TravelMode
import app.camplanner.ui.common.ScheduleItem
import app.camplanner.ui.common.ScheduleRow
import app.camplanner.ui.common.TabPage
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.format.Formats
import java.time.LocalDate

/** Where Today's buttons lead. */
data class TodayActions(
    val openEvent: (Long) -> Unit,
    val addEvent: (LocalDate) -> Unit,
    val openReview: () -> Unit,
    val openFitness: () -> Unit,
    val openHomework: (Long) -> Unit,
    val openSettings: () -> Unit,
    val openImport: () -> Unit,
    val openPermissions: () -> Unit,
)

@Composable
fun TodayRoute(actions: TodayActions) {
    val context = LocalContext.current
    val vm = appViewModel { graph, _ -> TodayViewModel(graph) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { s ->
        TodayScreen(
            s, actions,
            onRoute = { vm.route(context, it) },
            onToggleAlarm = vm::toggleAlarm,
            onToggleDue = vm::setDone,
            onHideSetup = vm::hideSetup,
        )
    }
}

@Composable
fun TodayScreen(
    s: TodayState,
    actions: TodayActions,
    onRoute: (Long) -> Unit,
    onToggleAlarm: () -> Unit,
    onToggleDue: (Long, Boolean) -> Unit,
    onHideSetup: () -> Unit,
) {
    val c = Atlas.colors
    TabPage(
        header = {
            LargeHeader(
                title = "${Formats.weekday(s.date)}\n${Formats.dayMonth(s.date)}",
                overline = s.termLine,
                actions = { HeaderAction(Icons.Outlined.Settings, "Settings", actions.openSettings) },
            )
        },
        fab = { AddButton("Add", Icons.Outlined.Add, { actions.addEvent(s.date) }) },
    ) {
        s.attention?.let {
            Banner(it, "Fix", actions.openPermissions, icon = Icons.Outlined.NotificationsOff)
            Spacer(Modifier.height(12.dp))
        }

        HeroCard(s.hero, actions.openEvent, onRoute)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val alarm = s.alarmTime
            StatTile(
                "Alarm",
                when {
                    !s.alarmsEnabled -> "Off"
                    alarm == null -> "None"
                    s.alarmSkipped -> "Skipped"
                    else -> Formats.time(alarm)
                },
                Modifier.weight(1f),
                detail = when {
                    !s.alarmsEnabled -> "In Settings"
                    alarm == null -> "Free morning"
                    s.alarmSkipped -> "Tap to restore"
                    else -> "Tap to skip"
                },
                valueColor = if (alarm == null || s.alarmSkipped || !s.alarmsEnabled) c.textSecondary else c.text,
                onClick = when {
                    !s.alarmsEnabled -> actions.openSettings
                    alarm == null -> null
                    else -> onToggleAlarm
                },
            )
            StatTile(
                "Review",
                if (s.reviewDone) "Done" else s.reviewTime?.let(Formats::time) ?: "Off",
                Modifier.weight(1f),
                detail = if (s.reviewDone) "Reviewed" else "Open now",
                valueColor = if (s.reviewDone) c.gold else c.text,
                onClick = actions.openReview,
            )
            s.pushUps?.let { (done, target) ->
                StatTile(
                    "Push-ups", "$done", Modifier.weight(1f),
                    detail = if (done >= target) "Target met" else "of $target",
                    valueColor = if (done >= target) c.gold else c.text,
                    onClick = actions.openFitness,
                )
            }
        }

        GroupHeader("Schedule") {
            Text(Formats.count(s.schedule.count { !it.cancelled }, "event"), style = Atlas.type.supporting, color = c.textSecondary)
        }
        Group {
            if (s.schedule.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Nothing scheduled today.", style = Atlas.type.reading, color = c.textSecondary)
                    TertiaryButton("Add an event", { actions.addEvent(s.date) }, icon = Icons.Outlined.Add)
                }
            }
            s.schedule.forEachIndexed { i, row ->
                ScheduleItem(row, onClick = { actions.openEvent(row.id) })
                if (i != s.schedule.lastIndex) GroupDivider(inset = 76.dp)
            }
        }

        if (s.dueSoon.isNotEmpty()) {
            GroupHeader("Due soon")
            Group {
                s.dueSoon.forEachIndexed { i, d ->
                    DueItem(d, onToggle = { onToggleDue(d.id, it) }, onClick = { actions.openHomework(d.id) })
                    if (i != s.dueSoon.lastIndex) GroupDivider(inset = 56.dp)
                }
            }
        }

        if (s.setup.isNotEmpty()) {
            GroupHeader("Getting started") { TertiaryButton("Hide", onHideSetup, color = c.textSecondary) }
            Group {
                s.setup.forEachIndexed { i, step ->
                    ItemRow(
                        title = step.title,
                        supporting = step.detail,
                        leading = {
                            Text("${i + 1}", style = Atlas.type.groupTitle, color = c.gold, modifier = Modifier.width(20.dp))
                        },
                        onClick = when (step) {
                            SetupStep.IMPORT -> actions.openImport
                            SetupStep.HOME, SetupStep.TERM -> actions.openSettings
                            SetupStep.PERMISSIONS -> actions.openPermissions
                        },
                    )
                    if (i != s.setup.lastIndex) GroupDivider(inset = 52.dp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Almanac(s)
    }
}

@Composable
private fun HeroCard(h: Hero, onOpen: (Long) -> Unit, onRoute: (Long) -> Unit) {
    val c = Atlas.colors
    val now = h.now
    val next = h.next
    val target = now ?: next ?: h.tomorrowFirst
    Group {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (target != null) Modifier.clickable { onOpen(target.id) } else Modifier)
                .padding(18.dp),
        ) {
            if (now != null) {
                Text("Now · until ${Formats.time(now.end)}", style = Atlas.type.caption, color = c.gold)
                Spacer(Modifier.height(4.dp))
                Text(now.title, style = Atlas.type.heroTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                now.place?.let { Text(it, style = Atlas.type.supporting, color = c.textSecondary) }
                Spacer(Modifier.height(12.dp))
                ProgressBar(h.nowProgress)
            }
            if (next != null) {
                if (now != null) Spacer(Modifier.height(20.dp))
                Text(
                    "Next · ${h.nextWhen ?: Formats.time(next.start)}",
                    style = Atlas.type.caption, color = if (now == null) c.gold else c.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    next.title,
                    style = if (now == null) Atlas.type.heroTitle else Atlas.type.groupTitle,
                    color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull("${Formats.time(next.start)}–${Formats.time(next.end)}", next.place).joinToString(" · "),
                    style = Atlas.type.supporting, color = c.textSecondary,
                )
                if (next.place != null) LeaveLine(next, { onRoute(next.id) })
            }
            if (now == null && next == null) {
                Text("Nothing more today", style = Atlas.type.heroTitle, color = c.text)
                val t = h.tomorrowFirst
                Spacer(Modifier.height(4.dp))
                Text(
                    if (t != null) "Tomorrow starts at ${Formats.time(t.start)} with ${t.title}" else "Tomorrow is clear too.",
                    style = Atlas.type.reading, color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun LeaveLine(next: ScheduleRow, onRoute: () -> Unit) {
    val c = Atlas.colors
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (next.mode == TravelMode.BIKE) Icons.AutoMirrored.Outlined.DirectionsBike else Icons.AutoMirrored.Outlined.DirectionsWalk,
            contentDescription = null, tint = c.accentText, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(next.leaveAt?.let { "Leave at ${Formats.time(it)}" } ?: "Travel time unknown", style = Atlas.type.itemTitle, color = c.text)
            Text(
                next.travelMinutes?.let { "$it min by ${if (next.mode == TravelMode.BIKE) "bike" else "foot"}, plus buffer" }
                    ?: "Set your home address or fix the place",
                style = Atlas.type.supporting, color = c.textSecondary,
            )
        }
        PrimaryButton("Route", onRoute, icon = Icons.Outlined.Directions)
    }
}

@Composable
private fun DueItem(d: DueRow, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val c = Atlas.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = d.done, onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = c.gold, uncheckedColor = c.textSecondary, checkmarkColor = c.background),
        )
        Column(Modifier.weight(1f)) {
            Text(d.title, style = Atlas.type.itemTitle, color = if (d.done) c.textSecondary else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.detail, style = Atlas.type.supporting, color = if (d.overdue) c.accentText else c.textSecondary)
        }
    }
}

@Composable
private fun Almanac(s: TodayState) {
    val c = Atlas.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
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

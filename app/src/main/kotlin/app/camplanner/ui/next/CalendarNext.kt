package app.camplanner.ui.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.kit.AddButton
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.DayCell
import app.camplanner.designsystem.kit.DayStrip
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.kit.PickerBox
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SheetBody
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.TravelMode
import app.camplanner.ui.format.Formats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class CalendarNextState(
    val today: LocalDate,
    val selected: LocalDate,
    val termLine: String?,
    val week: List<DayCell>,
    val dayRows: List<ScheduleRow>,
)

@Composable
fun CalendarNextScreen(
    s: CalendarNextState,
    onTab: (Tab) -> Unit = {},
    onSelect: (LocalDate) -> Unit = {},
    onShiftWeek: (Int) -> Unit = {},
    onToday: () -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onRoute: (Long) -> Unit = {},
    onAdd: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val c = Atlas.colors
    val month = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK).format(s.selected)
    TabScaffold(Tab.CALENDAR, onTab, fab = { AddButton("New event", Icons.Outlined.Add, onAdd) }) {
        LargeHeader(
            title = month,
            overline = s.termLine,
            actions = {
                if (s.selected != s.today) TertiaryButton("Today", onToday)
                HeaderAction(Icons.Outlined.FileDownload, "Import timetable", onImport)
            },
        )
        Paged {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onShiftWeek(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous week", tint = c.textSecondary) }
                Text(
                    "${Formats.dayMonthShort(s.week.first().date)} – ${Formats.dayMonthShort(s.week.last().date)}",
                    style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton({ onShiftWeek(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next week", tint = c.textSecondary) }
            }
            DayStrip(s.week, s.selected, onSelect)

            GroupHeader("${Formats.weekday(s.selected)} ${Formats.dayMonth(s.selected)}") {
                Text(Formats.count(s.dayRows.size, "event"), style = Atlas.type.supporting, color = c.textSecondary)
            }
            Group {
                if (s.dayRows.isEmpty()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("A free day.", style = Atlas.type.reading, color = c.textSecondary)
                        TertiaryButton("Add an event", onAdd, icon = Icons.Outlined.Add)
                    }
                }
                s.dayRows.forEachIndexed { i, row ->
                    ScheduleItem(row, onClick = { onOpenEvent(row.id) }, onRoute = { onRoute(row.id) })
                    if (i != s.dayRows.lastIndex) GroupDivider(inset = 80.dp)
                }
            }
        }
    }
}

// --- Event editor -----------------------------------------------------------------------------

enum class RepeatChoice(val label: String) { NEVER("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), FORTNIGHTLY("Every 2 weeks") }
enum class EndsChoice(val label: String) { NEVER("Never"), ON_DATE("On a date"), AFTER("After a number") }

@Immutable
data class EventEditorState(
    val isNew: Boolean,
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val repeat: RepeatChoice,
    val days: Set<DayOfWeek>,
    val ends: EndsChoice,
    val until: LocalDate?,
    val count: Int?,
    val location: String,
    val mode: TravelMode?,
    val alertMinutes: Int?,
    val note: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditorSheet(
    s: EventEditorState,
    onChange: ((EventEditorState) -> EventEditorState) -> Unit = {},
    onPickDate: () -> Unit = {},
    onPickStart: () -> Unit = {},
    onPickEnd: () -> Unit = {},
    onPickUntil: () -> Unit = {},
    onSave: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val c = Atlas.colors
    SheetBody(if (s.isNew) "New event" else "Edit event") {
        AppTextField(s.title, { v -> onChange { it.copy(title = v) } }, "Title", placeholder = "Supervision, society, gym…")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PickerBox("Date", Formats.shortDate(s.date), onPickDate, Modifier.weight(1.4f))
            PickerBox("Starts", Formats.time(s.start), onPickStart, Modifier.weight(1f))
            PickerBox("Ends", Formats.time(s.end), onPickEnd, Modifier.weight(1f))
        }

        Label("Repeats")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatChoice.entries.forEach { r -> AppChip(r.label, s.repeat == r, { onChange { it.copy(repeat = r) } }) }
        }
        if (s.repeat == RepeatChoice.WEEKLY || s.repeat == RepeatChoice.FORTNIGHTLY) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.entries.forEach { d ->
                    val on = d in s.days
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (on) c.accentFill else Color.Transparent)
                            .border(1.dp, if (on) c.accentFill else c.hairline, CircleShape)
                            .clickable(role = Role.Checkbox) { onChange { st -> st.copy(days = if (on) st.days - d else st.days + d) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.getDisplayName(TextStyle.NARROW, Locale.UK), style = Atlas.type.button, color = if (on) c.text else c.textSecondary)
                    }
                }
            }
        }
        if (s.repeat != RepeatChoice.NEVER) {
            Label("Ends")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EndsChoice.entries.forEach { e -> AppChip(e.label, s.ends == e, { onChange { it.copy(ends = e) } }) }
            }
            when (s.ends) {
                EndsChoice.ON_DATE -> {
                    Spacer(Modifier.height(10.dp))
                    PickerBox("Until", s.until?.let(Formats::shortDate) ?: "Choose", onPickUntil, Modifier.fillMaxWidth())
                }
                EndsChoice.AFTER -> {
                    Spacer(Modifier.height(10.dp))
                    AppTextField(s.count?.toString().orEmpty(), { v -> onChange { it.copy(count = v.toIntOrNull()) } }, "Times", suffix = "times")
                }
                EndsChoice.NEVER -> Unit
            }
            Spacer(Modifier.height(8.dp))
            Text(repeatSummary(s), style = Atlas.type.supporting, color = c.textSecondary)
        }

        Spacer(Modifier.height(16.dp))
        AppTextField(s.location, { v -> onChange { it.copy(location = v) } }, "Place", placeholder = "Used for routes and leave alerts")

        Label("Getting there")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("Usual", s.mode == null, { onChange { it.copy(mode = null) } })
            AppChip("Walk", s.mode == TravelMode.WALK, { onChange { it.copy(mode = TravelMode.WALK) } })
            AppChip("Bike", s.mode == TravelMode.BIKE, { onChange { it.copy(mode = TravelMode.BIKE) } })
        }

        Label("Reminder")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Usual", 0 to "None", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour").forEach { (m, label) ->
                AppChip(label, s.alertMinutes == m, { onChange { it.copy(alertMinutes = m) } })
            }
        }

        Spacer(Modifier.height(16.dp))
        AppTextField(s.note, { v -> onChange { it.copy(note = v) } }, "Notes", singleLine = false)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save", onSave, Modifier.fillMaxWidth(), enabled = s.title.isNotBlank())
        TertiaryButton("Cancel", onCancel, Modifier.fillMaxWidth(), color = c.textSecondary)
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text, style = Atlas.type.caption, color = Atlas.colors.textSecondary)
    Spacer(Modifier.height(8.dp))
}

fun repeatSummary(s: EventEditorState): String {
    val days = s.days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.UK) }
    val what = when (s.repeat) {
        RepeatChoice.NEVER -> return "Once"
        RepeatChoice.DAILY -> "Every day"
        RepeatChoice.WEEKDAYS -> "Every weekday"
        RepeatChoice.WEEKLY -> "Every week on ${days.ifEmpty { s.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK) }}"
        RepeatChoice.FORTNIGHTLY -> "Every other week on ${days.ifEmpty { s.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK) }}"
    }
    val ends = when (s.ends) {
        EndsChoice.NEVER -> ""
        EndsChoice.ON_DATE -> s.until?.let { ", until ${Formats.shortDate(it)}" } ?: ""
        EndsChoice.AFTER -> s.count?.let { ", $it times" } ?: ""
    }
    return "$what at ${Formats.time(s.start)}$ends"
}

package app.camplanner.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasDialog
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A value written on a ruled line that opens a picker when tapped: a date or a time. */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(role = Role.Button, onClick = onClick)) {
        if (label.isNotEmpty()) Text(label.uppercase(), style = Atlas.type.overline, color = Atlas.colors.textSecondary)
        Text(value, style = Atlas.type.numeral.copy(fontSize = Atlas.type.title.fontSize), color = Atlas.colors.text, modifier = Modifier.padding(vertical = 4.dp))
        Hairline()
    }
}

@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, Formats.shortDate(date), { open = true }, modifier)
    if (open) DatePick(date, { onChange(it); open = false }, { open = false })
}

@Composable
fun TimeField(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, Formats.time(time), { open = true }, modifier)
    if (open) TimePick(time, { onChange(it); open = false }, { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC midnights.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val c = Atlas.colors
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AtlasButton("Choose", {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }, style = ActionStyle.Text)
        },
        dismissButton = { AtlasButton("Cancel", onDismiss, style = ActionStyle.Text) },
        colors = DatePickerDefaults.colors(containerColor = c.surface),
    ) {
        DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(
                containerColor = c.surface,
                selectedDayContainerColor = c.accentFill,
                selectedDayContentColor = c.text,
                todayDateBorderColor = c.gold,
                todayContentColor = c.gold,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePick(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    val c = Atlas.colors
    AtlasDialog(
        title = "Choose a time",
        onDismiss = onDismiss,
        actions = {
            AtlasButton("Cancel", onDismiss, style = ActionStyle.Text)
            AtlasButton("Choose", { onPick(LocalTime.of(state.hour, state.minute)) }, style = ActionStyle.Text)
        },
    ) {
        TimePicker(
            state = state,
            colors = TimePickerDefaults.colors(
                clockDialColor = c.background,
                selectorColor = c.accentFill,
                timeSelectorSelectedContainerColor = c.accentFill,
                timeSelectorUnselectedContainerColor = c.background,
                timeSelectorSelectedContentColor = c.text,
                timeSelectorUnselectedContentColor = c.textSecondary,
            ),
        )
    }
}

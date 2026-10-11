package app.camplanner.ui.common

import androidx.compose.material3.AlertDialog
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
import app.camplanner.designsystem.kit.PickerBox
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A framed field showing a date; tap to pick another. */
@Composable
fun DateBox(label: String, date: LocalDate?, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Choose") {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, date?.let(Formats::shortDate) ?: placeholder, { open = true }, modifier)
    if (open) DatePick(date ?: LocalDate.now(), { onChange(it); open = false }, { open = false })
}

/** A framed field showing a time; tap to pick another. */
@Composable
fun TimeBox(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerBox(label, Formats.time(time), { open = true }, modifier)
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
            TertiaryButton("OK", { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } })
        },
        dismissButton = { TertiaryButton("Cancel", onDismiss, color = c.textSecondary) },
        colors = DatePickerDefaults.colors(containerColor = c.surfaceHigh),
    ) {
        DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(
                containerColor = c.surfaceHigh,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Choose a time", style = Atlas.type.groupTitle, color = c.text) },
        text = {
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
        },
        confirmButton = { TertiaryButton("OK", { onPick(LocalTime.of(state.hour, state.minute)) }) },
        dismissButton = { TertiaryButton("Cancel", onDismiss, color = c.textSecondary) },
    )
}

/** A simple yes/no question. */
@Composable
fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Atlas.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(title, style = Atlas.type.groupTitle, color = c.text) },
        text = { Text(text, style = Atlas.type.reading, color = c.textSecondary) },
        confirmButton = { TertiaryButton(confirm, { onConfirm(); onDismiss() }) },
        dismissButton = { TertiaryButton("Cancel", onDismiss, color = c.textSecondary) },
    )
}

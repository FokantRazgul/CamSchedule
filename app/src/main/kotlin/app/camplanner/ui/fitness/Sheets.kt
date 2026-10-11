package app.camplanner.ui.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.kit.SheetBody
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.Fitness
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.RunLog
import app.camplanner.ui.common.AppSheet
import app.camplanner.ui.common.DateBox
import java.time.LocalDate

/** Any amount for one exercise, added or taken off. */
@Composable
fun AmountSheet(c: Counter, onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = text.toIntOrNull()?.takeIf { it > 0 }
    val unit = if (c.unit == ExerciseUnit.SECONDS) "seconds" else "reps"
    AppSheet(onDismiss) {
        SheetBody(c.name) {
            Text("${c.done}${c.suffix} of ${c.target}${c.suffix} so far", style = Atlas.type.reading, color = Atlas.colors.textSecondary)
            Spacer(Modifier.height(14.dp))
            AppTextField(text, { text = it.filter(Char::isDigit).take(4) }, "Amount", keyboardType = KeyboardType.Number, suffix = unit)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Take off", { amount?.let { onAdd(-it); onDismiss() } }, Modifier.weight(1f), enabled = amount != null && c.done > 0)
                PrimaryButton("Add", { amount?.let { onAdd(it); onDismiss() } }, Modifier.weight(1f), enabled = amount != null)
            }
        }
    }
}

/** Log a new run or correct one: date, distance and time, with the pace as you type. */
@Composable
fun RunSheet(run: RunLog?, defaultDate: LocalDate, onSave: (LocalDate, Int, Int) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    var date by remember { mutableStateOf(run?.date ?: defaultDate) }
    var km by remember { mutableStateOf(run?.let { "%.2f".format(java.util.Locale.UK, it.distanceMeters / 1000.0).trimEnd('0').trimEnd('.') }.orEmpty()) }
    var time by remember { mutableStateOf(run?.let { Fitness.formatDuration(it.durationSeconds) }.orEmpty()) }
    val distance = km.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 200 }
    val seconds = Fitness.parseDuration(time)?.takeIf { it > 0 }
    val pace = if (distance != null && seconds != null) Fitness.pace((distance * 1000).toInt(), seconds) else null
    val c = Atlas.colors
    AppSheet(onDismiss) {
        SheetBody(if (run == null) "Log a run" else "Edit run") {
            DateBox("Date", date, { date = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(km, { km = it }, "Distance", Modifier.weight(1f), placeholder = "5.0", keyboardType = KeyboardType.Decimal, suffix = "km")
                AppTextField(time, { time = it }, "Time", Modifier.weight(1f), placeholder = "27:30")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                pace?.let { "Pace ${Fitness.formatPace(it)} per km" } ?: "Time as minutes, mm:ss or h:mm:ss",
                style = Atlas.type.supporting, color = if (pace != null) c.gold else c.textSecondary,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                "Save",
                { if (distance != null && seconds != null) { onSave(date, (distance * 1000).toInt(), seconds); onDismiss() } },
                Modifier.fillMaxWidth(),
                enabled = distance != null && seconds != null,
            )
            if (onDelete != null) TertiaryButton("Delete this run", { onDelete(); onDismiss() }, Modifier.fillMaxWidth())
        }
    }
}

package app.camplanner.ui.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.Moon
import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.designsystem.kit.CountButton
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.kit.ProgressBar
import app.camplanner.designsystem.kit.ProgressRing
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.ExerciseUnit
import app.camplanner.ui.format.Formats
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class Counter(val id: Long, val name: String, val unit: ExerciseUnit, val done: Int, val target: Int) {
    val remaining: Int get() = (target - done).coerceAtLeast(0)
    val fraction: Float get() = if (target <= 0) 1f else done / target.toFloat()
    val suffix: String get() = if (unit == ExerciseUnit.SECONDS) " s" else ""
}

@Immutable
data class LoggedSet(val id: Long, val exercise: String, val amount: Int, val unit: ExerciseUnit, val at: LocalTime)

@Immutable
data class FitnessNextState(
    val date: LocalDate,
    val restDay: Boolean,
    val main: List<Counter>,
    val others: List<Counter>,
    val log: List<LoggedSet>,
    val weekKm: Double,
    val targetKm: Double,
    val lastRun: String?,
    val recent: List<MoonPhase>,
    val streak: Int,
)

@Composable
fun FitnessNextScreen(
    s: FitnessNextState,
    onTab: (Tab) -> Unit = {},
    onAdd: (Long, Int) -> Unit = { _, _ -> },
    onCustom: (Long) -> Unit = {},
    onRemoveSet: (Long) -> Unit = {},
    onLogRun: () -> Unit = {},
    onHistory: () -> Unit = {},
    onEditExercises: () -> Unit = {},
    snackbar: String? = null,
) {
    val c = Atlas.colors
    TabScaffold(Tab.FITNESS, onTab, snackbar = snackbar, snackbarAction = if (snackbar != null) "Undo" else null) {
        LargeHeader(
            title = "Fitness",
            overline = "${Formats.weekday(s.date)} ${Formats.dayMonth(s.date)}" + if (s.restDay) " · rest day" else "",
            overlineColor = c.textSecondary,
            actions = {
                HeaderAction(Icons.Outlined.History, "History", onHistory)
                HeaderAction(Icons.Outlined.Tune, "Exercises and targets", onEditExercises)
            },
        )
        Paged {
            s.main.forEach { m ->
                CounterCard(m, onAdd, onCustom)
                Spacer(Modifier.height(10.dp))
            }

            GroupHeader("Today's sets") {
                Text(if (s.log.isEmpty()) "none yet" else "tap × to remove", style = Atlas.type.supporting, color = c.textSecondary)
            }
            Group {
                if (s.log.isEmpty()) {
                    Text("Sets you log appear here.", style = Atlas.type.reading, color = c.textSecondary, modifier = Modifier.padding(16.dp))
                }
                s.log.forEachIndexed { i, set ->
                    ItemRow(
                        title = "${set.exercise}  +${set.amount}${if (set.unit == ExerciseUnit.SECONDS) " s" else ""}",
                        supporting = Formats.time(set.at),
                        minHeight = 56.dp,
                        trailing = {
                            IconButton({ onRemoveSet(set.id) }) { Icon(Icons.Outlined.Close, "Remove this set", tint = c.textSecondary) }
                        },
                    )
                    if (i != s.log.lastIndex) GroupDivider()
                }
            }

            GroupHeader("Abs and legs") { TertiaryButton("Edit", onEditExercises) }
            Group {
                s.others.forEachIndexed { i, o ->
                    ItemRow(
                        title = o.name,
                        supporting = if (o.remaining == 0) "Done · ${o.done}${o.suffix}" else "${o.done} of ${o.target}${o.suffix} · ${o.remaining}${o.suffix} to go",
                        supportingColor = if (o.remaining == 0) c.gold else c.textSecondary,
                        leading = { ProgressRing(o.fraction, size = 36.dp, stroke = 4.dp) },
                        trailing = {
                            val step = if (o.unit == ExerciseUnit.SECONDS) 15 else 5
                            CountButton("+$step", { onAdd(o.id, step) }, description = "Add $step to ${o.name}")
                        },
                        onClick = { onCustom(o.id) },
                    )
                    if (i != s.others.lastIndex) GroupDivider(inset = 64.dp)
                }
            }

            GroupHeader("Running") {
                Text("this week", style = Atlas.type.supporting, color = c.textSecondary)
            }
            Group {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Formats.km(s.weekKm), style = Atlas.type.heroNumber.copy(fontSize = Atlas.type.heroTitle.fontSize), color = c.text)
                        Spacer(Modifier.width(6.dp))
                        Text("of ${Formats.km(s.targetKm)} km", style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressBar((s.weekKm / s.targetKm).toFloat())
                    Spacer(Modifier.height(12.dp))
                    Text(s.lastRun?.let { "Last run: $it" } ?: "No runs yet.", style = Atlas.type.supporting, color = c.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Log a run", onLogRun, Modifier.fillMaxWidth(), icon = Icons.Outlined.DirectionsRun)
                }
            }

            GroupHeader("Last 14 days") {
                Text("${s.streak}-day streak", style = Atlas.type.supporting, color = c.gold)
            }
            Group {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    s.recent.forEachIndexed { i, phase ->
                        val day = s.date.minusDays((s.recent.size - 1 - i).toLong())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Moon(phase, size = 16.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK),
                                style = Atlas.type.caption, color = if (i == s.recent.lastIndex) c.text else c.textSecondary,
                            )
                        }
                    }
                }
                Text(
                    "Full moon: every target met. Half: rest day. Empty: missed.",
                    style = Atlas.type.supporting, color = c.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }
        }
    }
}

/** A big counter for push-ups or pull-ups: ring, count, and steps of 5, 10, 20, plus "−" and a custom amount. */
@Composable
private fun CounterCard(m: Counter, onAdd: (Long, Int) -> Unit, onCustom: (Long) -> Unit) {
    val c = Atlas.colors
    Group {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(m.fraction, size = 96.dp, stroke = 7.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${m.done}", style = Atlas.type.heroNumber.copy(fontSize = Atlas.type.heroNumber.fontSize * 0.8f), color = if (m.remaining == 0) c.gold else c.text)
                    Text("of ${m.target}", style = Atlas.type.caption, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = Atlas.type.groupTitle, color = c.text)
                Text(
                    if (m.remaining == 0) "Done for today" else "${m.remaining} to go",
                    style = Atlas.type.supporting, color = if (m.remaining == 0) c.gold else c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CountButton("−5", { onAdd(m.id, -5) }, description = "Take 5 off ${m.name}")
                    CountButton("+5", { onAdd(m.id, 5) }, description = "Add 5 ${m.name}")
                    CountButton("+10", { onAdd(m.id, 10) }, description = "Add 10 ${m.name}")
                    CountButton("…", { onCustom(m.id) }, description = "Another amount")
                }
            }
        }
    }
}

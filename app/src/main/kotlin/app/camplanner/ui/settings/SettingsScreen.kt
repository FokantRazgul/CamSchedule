package app.camplanner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.geo.GeoResolver
import app.camplanner.data.geo.GeoResult
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.ChoiceRow
import app.camplanner.designsystem.components.IndexRow
import app.camplanner.designsystem.components.LedgerField
import app.camplanner.designsystem.components.NumberEntry
import app.camplanner.designsystem.components.OnOff
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.components.SettingRow
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.AppSettings
import app.camplanner.model.BuiltInExercises
import app.camplanner.model.Exercise
import app.camplanner.model.TravelMode
import app.camplanner.ui.common.DateField
import app.camplanner.ui.common.TimeField
import app.camplanner.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class SettingsViewModel(private val graph: DataGraph) : ViewModel() {
    val settings: StateFlow<AppSettings?> = graph.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val exercises: StateFlow<List<Exercise>> = graph.fitness.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(change: (AppSettings) -> AppSettings) = viewModelScope.launch {
        graph.settings.update(change)
        graph.onScheduleChanged()
    }

    fun setTarget(e: Exercise, target: Int) = viewModelScope.launch { graph.fitness.saveExercise(e.copy(dailyTarget = target)) }

    /** Geocodes the home address; on success stores the coordinates. */
    suspend fun findHome(address: String): String {
        update { it.copy(homeAddress = address.trim()) }
        return when (val r = graph.locations.lookupAddress(address)) {
            is GeoResult.Found -> {
                update { it.copy(homeAddress = address.trim(), home = r.hit.point) }
                "Found: ${r.hit.label ?: "on the map"}"
            }
            GeoResult.NotFound -> "Not found. Try adding the street, or enter coordinates below."
            GeoResult.Unavailable -> "The map service can't be reached; try again when online, or enter coordinates."
        }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onPermissions: () -> Unit,
    onExercises: () -> Unit,
    onSubjects: () -> Unit,
    onLocations: () -> Unit,
    onImport: () -> Unit,
) {
    val vm = appViewModel { graph, _ -> SettingsViewModel(graph) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    settings?.let { SettingsScreen(it, exercises, vm, onBack, onPermissions, onExercises, onSubjects, onLocations, onImport) }
}

@Composable
private fun SettingsScreen(
    s: AppSettings,
    exercises: List<Exercise>,
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onPermissions: () -> Unit,
    onExercises: () -> Unit,
    onSubjects: () -> Unit,
    onLocations: () -> Unit,
    onImport: () -> Unit,
) {
    val c = Atlas.colors
    val update = vm::update
    val scope = rememberCoroutineScope()
    AtlasPage(mastheadLeft = "Settings", mastheadRight = null, title = "The Settings", onBack = onBack) {
        // I. Home
        SectionTitle("I", "Home")
        var address by remember { mutableStateOf(s.homeAddress) }
        var homeMessage by remember { mutableStateOf<String?>(null) }
        Spacer(Modifier.height(Atlas.space.m))
        LedgerField(address, { address = it }, label = "Address", placeholder = "Trinity College, Trinity Street")
        Spacer(Modifier.height(Atlas.space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.home?.let { String.format(Locale.ROOT, "%.5f, %.5f", it.lat, it.lng) } ?: "Not on the map yet",
                style = Atlas.type.time, color = if (s.home == null) c.accentText else c.textSecondary, modifier = Modifier.weight(1f),
            )
            AtlasButton("Find on the map", { scope.launch { homeMessage = vm.findHome(address) } })
        }
        homeMessage?.let { Text(it, style = Atlas.type.bodySmall, color = c.textSecondary) }
        var coords by remember { mutableStateOf("") }
        Spacer(Modifier.height(Atlas.space.m))
        Row(verticalAlignment = Alignment.Bottom) {
            LedgerField(coords, { coords = it }, Modifier.weight(1f), label = "Or coordinates", placeholder = "52.2070, 0.1170")
            AtlasButton("Set", {
                GeoResolver.parseCoordinates(coords)?.let { p -> update { it.copy(home = p, homeAddress = address.trim()) }; coords = "" }
            }, style = ActionStyle.Text)
        }

        Gap()
        SectionTitle("II", "Travel")
        SettingRow("Usually", detail = "Each engagement can override this") {
            ChoiceRow(listOf(TravelMode.WALK to "Walk", TravelMode.BIKE to "Bike"), s.defaultTravelMode, { m -> update { it.copy(defaultTravelMode = m) } })
        }
        Minutes("Buffer", "Added to every journey", s.bufferMinutes, showRule = false) { v -> update { it.copy(bufferMinutes = v) } }

        Gap()
        SectionTitle("III", "Morning Alarm")
        SettingRow("Alarm") { OnOff(s.morningAlarmEnabled) { v -> update { it.copy(morningAlarmEnabled = v) } } }
        Minutes("Before the first engagement", null, s.morningAlarmLeadMinutes) { v -> update { it.copy(morningAlarmLeadMinutes = v) } }
        Minutes("Snooze", null, s.snoozeMinutes, showRule = false) { v -> update { it.copy(snoozeMinutes = v.coerceIn(1, 60)) } }

        Gap()
        SectionTitle("IV", "Alerts")
        SettingRow("Time to leave", detail = "Start, less travel and buffer") { OnOff(s.leaveAlertEnabled) { v -> update { it.copy(leaveAlertEnabled = v) } } }
        SettingRow("Reminder") { OnOff(s.reminderEnabled) { v -> update { it.copy(reminderEnabled = v) } } }
        Minutes("Reminder before", null, s.reminderLeadMinutes, showRule = false) { v -> update { it.copy(reminderLeadMinutes = v) } }

        Gap()
        SectionTitle("V", "Evening Review")
        SettingRow("Check-in") { OnOff(s.checkInEnabled) { v -> update { it.copy(checkInEnabled = v) } } }
        Minutes("After the last engagement", null, s.checkInDelayMinutes) { v -> update { it.copy(checkInDelayMinutes = v) } }
        SettingRow("On free days, at", showRule = false) {
            TimeField("", s.checkInFallback, { t -> update { it.copy(checkInFallback = t) } })
        }

        Gap()
        SectionTitle("VI", "Reading")
        Number("Minimum", "Below this the day doesn't count", s.readingMinPages, "pp") { v -> update { it.copy(readingMinPages = v, readingMaxPages = maxOf(v, it.readingMaxPages)) } }
        Number("Target", "Where the ring closes", s.readingMaxPages, "pp", showRule = false) { v -> update { it.copy(readingMaxPages = maxOf(v, it.readingMinPages)) } }

        Gap()
        SectionTitle("VII", "Exercise")
        exercises.filter { it.builtInKey == BuiltInExercises.PUSH_UPS || it.builtInKey == BuiltInExercises.PULL_UPS }.forEach { e ->
            Number(e.name, "Daily target", e.dailyTarget, "reps") { v -> vm.setTarget(e, v) }
        }
        var km by remember { mutableStateOf(trimKm(s.weeklyRunTargetKm)) }
        SettingRow("Running", detail = "Weekly distance") {
            LedgerField(km, { t ->
                km = t
                t.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { v -> update { it.copy(weeklyRunTargetKm = v) } }
            }, suffix = "km", minWidth = 44.dp, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
        }
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(Atlas.space.m))
            Text("Rest days", style = Atlas.type.body, color = c.text)
            Text("No targets, and streaks aren't broken", style = Atlas.type.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(Atlas.space.s))
            RestDays(s.restDays) { day ->
                update { it.copy(restDays = if (day in it.restDays) it.restDays - day else it.restDays + day) }
            }
            Spacer(Modifier.height(Atlas.space.m))
        }
        SettingRow("Midday nudge", detail = "If push-ups or pull-ups are under half") {
            OnOff(s.middayNudgeEnabled) { v -> update { it.copy(middayNudgeEnabled = v) } }
        }
        SettingRow("Nudge at") { TimeField("", s.middayNudgeTime, { t -> update { it.copy(middayNudgeTime = t) } }) }
        AtlasButton("All exercises and targets", onExercises, style = ActionStyle.Text)

        Gap()
        SectionTitle("VIII", "Term")
        Text("Used for the week number and the term's statistics.", style = Atlas.type.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(Atlas.space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Atlas.space.xl)) {
            DateField("Full Term begins", s.termStart ?: LocalDate.now(), { d -> update { it.copy(termStart = d) } }, Modifier.weight(1f))
            DateField("Ends", s.termEnd ?: (s.termStart ?: LocalDate.now()).plusWeeks(8), { d -> update { it.copy(termEnd = d) } }, Modifier.weight(1f))
        }
        if (s.termStart != null) AtlasButton("Clear the term", { update { it.copy(termStart = null, termEnd = null) } }, style = ActionStyle.Text)
        SettingRow("Weeks begin on", showRule = false) {
            ChoiceRow(listOf(DayOfWeek.MONDAY to "Monday", DayOfWeek.THURSDAY to "Thursday"), s.weekStart, { d -> update { it.copy(weekStart = d) } })
        }

        Gap()
        SectionTitle("IX", "Elsewhere")
        IndexRow(null, "Permissions", "Notifications, exact alarms, full screen, battery", onPermissions)
        IndexRow(null, "Import a timetable", "Replace the imported engagements", onImport)
        IndexRow(null, "Subjects", "Rename, merge, mark academic", onSubjects)
        IndexRow(null, "Locations", "Correct places on the map", onLocations, showRule = false)
    }
}

private fun trimKm(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@Composable
private fun Gap() = Spacer(Modifier.height(Atlas.space.xxl))

@Composable
private fun Minutes(label: String, detail: String?, value: Int, showRule: Boolean = true, onChange: (Int) -> Unit) =
    Number(label, detail, value, "min", showRule, onChange)

@Composable
private fun Number(label: String, detail: String?, value: Int, unit: String, showRule: Boolean = true, onChange: (Int) -> Unit) {
    var text by remember(label) { mutableStateOf(value.toString()) }
    SettingRow(label, detail = detail, showRule = showRule) {
        NumberEntry(text, { t ->
            text = t
            t.toIntOrNull()?.takeIf { it > 0 }?.let(onChange)
        }, suffix = unit)
    }
}

/** Seven small squares, Monday first; burgundy when chosen. */
@Composable
private fun RestDays(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val c = Atlas.colors
    val shape = RoundedCornerShape(Atlas.space.corner)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val on = d in selected
            Box(
                Modifier
                    .size(42.dp)
                    .background(if (on) c.accentFill else Color.Transparent, shape)
                    .border(1.dp, if (on) c.accent else c.hairline, shape)
                    .clickable(role = Role.Checkbox) { onToggle(d) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK).take(2),
                    style = Atlas.type.label, color = if (on) c.text else c.textSecondary,
                )
            }
        }
    }
}

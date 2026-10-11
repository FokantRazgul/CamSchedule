package app.camplanner.ui.more

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.alarms.AlarmScheduler
import app.camplanner.data.DataGraph
import app.camplanner.data.geo.GeoResolver
import app.camplanner.data.geo.GeoResult
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.AppSwitch
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.kit.SettingRow
import app.camplanner.designsystem.kit.Stepper
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.AppSettings
import app.camplanner.model.TravelMode
import app.camplanner.ui.common.Confirm
import app.camplanner.ui.common.DatePick
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.TimePick
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.common.updateSettings
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class SettingsViewModel(private val graph: DataGraph) : ViewModel() {
    val settings: StateFlow<AppSettings?> = graph.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (AppSettings) -> AppSettings) = viewModelScope.launch { graph.updateSettings(change) }

    /** Geocodes the home address; on success stores the coordinates. Returns a line to show. */
    suspend fun findHome(address: String): String {
        graph.updateSettings { it.copy(homeAddress = address.trim()) }
        return when (val r = graph.locations.lookupAddress(address)) {
            is GeoResult.Found -> {
                graph.updateSettings { it.copy(homeAddress = address.trim(), home = r.hit.point) }
                "Found: ${r.hit.label ?: "on the map"}"
            }
            GeoResult.NotFound -> "Not found. Try adding the street or postcode, or enter coordinates."
            GeoResult.Unavailable -> "The map service can't be reached. Try again when online, or enter coordinates."
        }
    }

    fun testAlarm(context: Context) = viewModelScope.launch {
        AlarmScheduler(context).test()
        Notices.show("The alarm will ring in 10 seconds")
    }

    fun backup(context: Context, uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { graph.backup.export(it) } != null }.getOrDefault(false)
        }
        Notices.show(if (ok) "Backup saved" else "The backup couldn't be written")
    }

    fun restore(context: Context, uri: Uri) = viewModelScope.launch {
        val error = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { graph.backup.restore(it) } ?: error("The file couldn't be opened") }
                .exceptionOrNull()
        }
        Notices.show(if (error == null) "Restored from the backup" else "Not restored: ${error.message ?: "the file isn't a Cam Planner backup"}")
    }
}

/** Where the settings page links to. */
data class SettingsLinks(
    val permissions: () -> Unit,
    val exercises: () -> Unit,
    val subjects: () -> Unit,
    val places: () -> Unit,
    val import: () -> Unit,
)

@Composable
fun SettingsRoute(onBack: () -> Unit, links: SettingsLinks) {
    val context = LocalContext.current
    val vm = appViewModel { graph, _ -> SettingsViewModel(graph) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var askRestore by remember { mutableStateOf<Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.backup(context, it) } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> askRestore = uri }

    SubPage("Settings", onBack) {
        val s = settings ?: return@SubPage
        SettingsContent(
            s, vm, links,
            onBackup = { save.launch("cam-planner-${LocalDate.now()}.json") },
            onRestore = { open.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
            onTestAlarm = { vm.testAlarm(context) },
        )
    }
    askRestore?.let { uri ->
        Confirm(
            "Restore this backup?", "Everything in the app now is replaced by what is in the file.", "Restore",
            { vm.restore(context, uri) }, { askRestore = null },
        )
    }
}

@Composable
private fun SettingsContent(
    s: AppSettings,
    vm: SettingsViewModel,
    links: SettingsLinks,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onTestAlarm: () -> Unit,
) {
    val c = Atlas.colors
    val update = vm::update
    val scope = rememberCoroutineScope()
    var pickTime by remember { mutableStateOf<String?>(null) } // "review" or "nudge"
    var pickDate by remember { mutableStateOf<String?>(null) } // "start" or "end"

    GroupHeader("Home")
    Group {
        Column(Modifier.padding(16.dp)) {
            var address by remember { mutableStateOf(s.homeAddress) }
            var message by remember { mutableStateOf<String?>(null) }
            var coords by remember { mutableStateOf("") }
            AppTextField(address, { address = it }, "Address", placeholder = "Trinity College, Trinity Street")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.home?.let { String.format(Locale.ROOT, "On the map at %.4f, %.4f", it.lat, it.lng) } ?: "Not on the map yet",
                    style = Atlas.type.supporting, color = if (s.home == null) c.accentText else c.textSecondary, modifier = Modifier.weight(1f),
                )
                SecondaryButton("Find", { scope.launch { message = vm.findHome(address) } }, icon = Icons.Outlined.Search, enabled = address.isNotBlank())
            }
            message?.let { Text(it, style = Atlas.type.supporting, color = c.textSecondary) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(coords, { coords = it }, "Or coordinates", Modifier.weight(1f), placeholder = "52.2070, 0.1170")
                TertiaryButton("Set", {
                    val p = GeoResolver.parseCoordinates(coords)
                    if (p != null) {
                        update { it.copy(home = p, homeAddress = address.trim()) }
                        coords = ""
                        message = "Home set from coordinates"
                    } else {
                        message = "Enter latitude and longitude, like 52.2070, 0.1170"
                    }
                })
            }
        }
    }

    GroupHeader("Getting around")
    Group {
        SettingRow("Usually", supporting = "Each event can override this") {
            AppChip("Walk", s.defaultTravelMode == TravelMode.WALK, { update { it.copy(defaultTravelMode = TravelMode.WALK) } })
            Spacer(Modifier.width(6.dp))
            AppChip("Bike", s.defaultTravelMode == TravelMode.BIKE, { update { it.copy(defaultTravelMode = TravelMode.BIKE) } })
        }
        GroupDivider()
        SettingRow("Buffer", supporting = "Added to every journey") {
            Stepper(s.bufferMinutes, { v -> update { it.copy(bufferMinutes = v) } }, max = 60, suffix = " min", description = "buffer")
        }
    }

    GroupHeader("Morning alarm")
    Group {
        SettingRow("Alarm", supporting = "Before the first event of the day") {
            AppSwitch(s.morningAlarmEnabled, { v -> update { it.copy(morningAlarmEnabled = v) } })
        }
        if (s.morningAlarmEnabled) {
            GroupDivider()
            SettingRow("How long before") {
                Stepper(s.morningAlarmLeadMinutes, { v -> update { it.copy(morningAlarmLeadMinutes = v) } }, step = 5, min = 15, max = 300, suffix = " min", description = "alarm lead time")
            }
            GroupDivider()
            SettingRow("Snooze") {
                Stepper(s.snoozeMinutes, { v -> update { it.copy(snoozeMinutes = v) } }, min = 1, max = 30, suffix = " min", description = "snooze")
            }
        }
        GroupDivider()
        SettingRow("Test the alarm", supporting = "Rings in 10 seconds, exactly like a real morning", onClick = onTestAlarm) {
            Icon(Icons.Outlined.Alarm, null, tint = c.accentText)
        }
    }

    GroupHeader("Alerts")
    Group {
        SettingRow("Time to leave", supporting = "Start time less travel and buffer") {
            AppSwitch(s.leaveAlertEnabled, { v -> update { it.copy(leaveAlertEnabled = v) } })
        }
        GroupDivider()
        SettingRow("Reminders", supporting = "Before each event, unless the event says otherwise") {
            AppSwitch(s.reminderEnabled, { v -> update { it.copy(reminderEnabled = v) } })
        }
        if (s.reminderEnabled) {
            GroupDivider()
            SettingRow("How long before") {
                Stepper(s.reminderLeadMinutes, { v -> update { it.copy(reminderLeadMinutes = v) } }, step = 5, min = 5, max = 120, suffix = " min", description = "reminder lead time")
            }
        }
    }

    GroupHeader("Evening review")
    Group {
        SettingRow("Review", supporting = "Reading, homework and fitness for the day") {
            AppSwitch(s.checkInEnabled, { v -> update { it.copy(checkInEnabled = v) } })
        }
        if (s.checkInEnabled) {
            GroupDivider()
            SettingRow("After the last event") {
                Stepper(s.checkInDelayMinutes, { v -> update { it.copy(checkInDelayMinutes = v) } }, step = 15, max = 360, suffix = " min", description = "review delay")
            }
            GroupDivider()
            SettingRow("On free days, at", onClick = { pickTime = "review" }) {
                Text(Formats.time(s.checkInFallback), style = Atlas.type.itemTitle, color = c.accentText)
            }
        }
    }

    GroupHeader("Reading")
    Group {
        SettingRow("Minimum", supporting = "Pages per subject for the day to count") {
            Stepper(s.readingMinPages, { v -> update { it.copy(readingMinPages = v, readingMaxPages = maxOf(v, it.readingMaxPages)) } }, step = 5, min = 5, max = 200, description = "minimum pages")
        }
        GroupDivider()
        SettingRow("Goal", supporting = "The top of your range") {
            Stepper(s.readingMaxPages, { v -> update { it.copy(readingMaxPages = maxOf(v, it.readingMinPages)) } }, step = 5, min = 5, max = 300, description = "page goal")
        }
    }

    GroupHeader("Fitness")
    Group {
        SettingRow("Midday nudge", supporting = "If push-ups or pull-ups are under half") {
            AppSwitch(s.middayNudgeEnabled, { v -> update { it.copy(middayNudgeEnabled = v) } })
        }
        if (s.middayNudgeEnabled) {
            GroupDivider()
            SettingRow("Nudge at", onClick = { pickTime = "nudge" }) {
                Text(Formats.time(s.middayNudgeTime), style = Atlas.type.itemTitle, color = c.accentText)
            }
        }
        GroupDivider()
        SettingRow("Exercises, targets and rest days", onClick = links.exercises)
    }

    GroupHeader("Term")
    Group {
        SettingRow("Full Term begins", onClick = { pickDate = "start" }) {
            Text(s.termStart?.let(Formats::shortDate) ?: "Set", style = Atlas.type.itemTitle, color = c.accentText)
        }
        GroupDivider()
        SettingRow("Ends", onClick = { pickDate = "end" }) {
            Text(s.termEnd?.let(Formats::shortDate) ?: "Set", style = Atlas.type.itemTitle, color = c.accentText)
        }
        GroupDivider()
        SettingRow("Weeks begin on", supporting = "Cambridge weeks run Thursday to Wednesday") {
            AppChip("Mon", s.weekStart == DayOfWeek.MONDAY, { update { it.copy(weekStart = DayOfWeek.MONDAY) } })
            Spacer(Modifier.width(6.dp))
            AppChip("Thu", s.weekStart == DayOfWeek.THURSDAY, { update { it.copy(weekStart = DayOfWeek.THURSDAY) } })
        }
        if (s.termStart != null) {
            GroupDivider()
            SettingRow("Clear the term dates", onClick = { update { it.copy(termStart = null, termEnd = null) } })
        }
    }

    GroupHeader("Your data")
    Group {
        SettingRow("Save a backup", supporting = "Everything, as one file you keep", onClick = onBackup)
        GroupDivider()
        SettingRow("Restore a backup", supporting = "Replaces what is in the app now", onClick = onRestore)
    }

    GroupHeader("Elsewhere")
    Group {
        SettingRow("Alarms and notifications", supporting = "Permissions and battery", onClick = links.permissions)
        GroupDivider()
        SettingRow("Import timetable", onClick = links.import)
        GroupDivider()
        SettingRow("Subjects", onClick = links.subjects)
        GroupDivider()
        SettingRow("Places", onClick = links.places)
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Cam Planner works offline and keeps everything on this phone.",
        style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )

    when (pickTime) {
        "review" -> TimePick(s.checkInFallback, { t -> update { it.copy(checkInFallback = t) }; pickTime = null }, { pickTime = null })
        "nudge" -> TimePick(s.middayNudgeTime, { t -> update { it.copy(middayNudgeTime = t) }; pickTime = null }, { pickTime = null })
    }
    when (pickDate) {
        "start" -> DatePick(s.termStart ?: LocalDate.now(), { d ->
            update { it.copy(termStart = d, termEnd = it.termEnd?.takeIf { e -> e.isAfter(d) } ?: d.plusWeeks(8).minusDays(1)) }
            pickDate = null
        }, { pickDate = null })
        "end" -> DatePick(s.termEnd ?: (s.termStart ?: LocalDate.now()).plusWeeks(8), { d ->
            update { it.copy(termEnd = d) }
            pickDate = null
        }, { pickDate = null })
    }
}

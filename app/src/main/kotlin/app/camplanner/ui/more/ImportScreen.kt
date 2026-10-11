package app.camplanner.ui.more

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.repo.ImportSummary
import app.camplanner.data.settings.ImportInfo
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.SubjectNames
import app.camplanner.ics.IcsFormatException
import app.camplanner.ics.ImportResult
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A file another app asked us to open ("Open with Cam Planner"). */
object PendingImport {
    val uri = MutableStateFlow<Uri?>(null)
}

sealed interface ImportStage {
    data object Idle : ImportStage
    data object Reading : ImportStage
    data class Preview(val fileName: String, val result: ImportResult) : ImportStage
    data class Done(val summary: ImportSummary) : ImportStage
    data class Failed(val message: String) : ImportStage
}

class ImportViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    val stage = MutableStateFlow<ImportStage>(ImportStage.Idle)
    val last: StateFlow<ImportInfo?> = graph.settings.importInfo.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun read(context: Context, uri: Uri) {
        stage.value = ImportStage.Reading
        viewModelScope.launch {
            stage.value = try {
                val input = context.contentResolver.openInputStream(uri) ?: error("The file could not be opened.")
                // Open-ended rules are expanded a little over a year ahead.
                val result = graph.timetable.parse(input, Instant.now() + Duration.ofDays(400))
                if (result.events.isEmpty()) ImportStage.Failed("No events were found in this file.")
                else ImportStage.Preview(displayName(context, uri), result)
            } catch (e: IcsFormatException) {
                ImportStage.Failed(e.message ?: "This file couldn't be read.")
            } catch (e: Exception) {
                ImportStage.Failed("The file could not be read: ${e.message}")
            }
        }
    }

    fun confirm() {
        val preview = stage.value as? ImportStage.Preview ?: return
        stage.value = ImportStage.Reading
        viewModelScope.launch {
            val summary = graph.timetable.replaceImported(preview.result, zone)
            graph.settings.recordImport(ImportInfo(preview.fileName, System.currentTimeMillis(), summary.events))
            stage.value = ImportStage.Done(summary)
        }
    }

    fun reset() {
        stage.value = ImportStage.Idle
    }

    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "timetable.ics"
}

@Composable
fun ImportRoute(onBack: () -> Unit, onSubjects: () -> Unit, onPlaces: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { graph, _ -> ImportViewModel(graph) }
    val stage by vm.stage.collectAsStateWithLifecycle()
    val last by vm.last.collectAsStateWithLifecycle()
    val pending by PendingImport.uri.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            vm.read(context, it)
            PendingImport.uri.value = null
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.read(context, uri) }
    val choose = { picker.launch(arrayOf("text/calendar", "application/ics", "text/x-vcalendar", "application/octet-stream", "*/*")) }
    val c = Atlas.colors
    val zone = ZoneId.systemDefault()

    SubPage("Import timetable", onBack) {
        Text(
            "Choose the .ics file of your timetable (from the lecture list, Moodle or your calendar). " +
                "Importing again replaces only what was imported before. Events you added, lectures you changed or cancelled, " +
                "subject names and merges are kept.",
            style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
        )
        last?.let {
            val at = Instant.ofEpochMilli(it.importedAtUtc).atZone(zone)
            Spacer(Modifier.height(10.dp))
            Text(
                "Last import: ${it.fileName}, ${Formats.count(it.eventCount, "event")}, ${Formats.shortDate(at.toLocalDate())} at ${Formats.time(at.toLocalTime())}",
                style = Atlas.type.supporting, color = c.text,
            )
        }
        Spacer(Modifier.height(20.dp))

        when (val s = stage) {
            ImportStage.Idle, is ImportStage.Failed -> {
                if (s is ImportStage.Failed) {
                    Text(s.message, style = Atlas.type.reading, color = c.accentText)
                    Spacer(Modifier.height(14.dp))
                }
                PrimaryButton("Choose an .ics file", choose, Modifier.fillMaxWidth(), icon = Icons.Outlined.FileOpen)
            }
            ImportStage.Reading -> Text("Reading the file…", style = Atlas.type.reading, color = c.textSecondary)
            is ImportStage.Preview -> {
                val r = s.result
                val days = r.events.filter { !it.allDay }.map { it.start.atZone(zone).toLocalDate() }
                val subjects = r.events.map { SubjectNames.displayName(it.summary) }.distinctBy { it.lowercase() }.sorted()
                GroupHeader(s.fileName)
                Group {
                    ItemRow("Events", trailing = { Count("${r.events.size}") }, minHeight = 52.dp)
                    if (days.isNotEmpty()) {
                        GroupDivider()
                        ItemRow("Dates", trailing = { Count("${Formats.dayMonthShort(days.min())} – ${Formats.dayMonthShort(days.max())}") }, minHeight = 52.dp)
                    }
                    GroupDivider()
                    ItemRow("Subjects", supporting = subjects.joinToString(" · "), trailing = { Count("${subjects.size}") })
                }
                if (r.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    r.warnings.take(5).forEach { Text(it, style = Atlas.type.supporting, color = c.accentText) }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Cancel", vm::reset, Modifier.weight(1f))
                    PrimaryButton("Import", vm::confirm, Modifier.weight(1f))
                }
            }
            is ImportStage.Done -> {
                val sum = s.summary
                GroupHeader("Imported")
                Text(
                    Formats.count(sum.events, "event") +
                        (if (sum.firstDay != null && sum.lastDay != null) ", ${Formats.shortDate(sum.firstDay!!)} to ${Formats.shortDate(sum.lastDay!!)}" else "") +
                        ". ${Formats.count(sum.newSubjects, "new subject")}.",
                    style = Atlas.type.reading, color = c.text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Places are being looked up on the map; leave times and alarms follow automatically.",
                    style = Atlas.type.supporting, color = c.textSecondary,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Review subjects", onSubjects, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Check places", onPlaces, Modifier.weight(1f))
                    SecondaryButton("Done", onBack, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Count(text: String) {
    Text(text, style = Atlas.type.itemTitle.copy(fontFeatureSettings = "tnum"), color = Atlas.colors.text)
}

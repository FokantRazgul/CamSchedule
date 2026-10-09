package app.camplanner.ui.importics

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.repo.ImportSummary
import app.camplanner.data.settings.ImportInfo
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.RuledRow
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.SubjectNames
import app.camplanner.ics.IcsFormatException
import app.camplanner.ics.ImportResult
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
    val lastImport: StateFlow<ImportInfo?> = graph.settings.importInfo.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun reset() { stage.value = ImportStage.Idle }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "timetable.ics"
}

@Composable
fun ImportRoute(onBack: () -> Unit, onOpenSubjects: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { graph, _ -> ImportViewModel(graph) }
    val stage by vm.stage.collectAsStateWithLifecycle()
    val last by vm.lastImport.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.read(context, uri)
    }
    ImportScreen(
        stage = stage,
        last = last,
        onBack = onBack,
        onChoose = { picker.launch(arrayOf("text/calendar", "application/ics", "text/x-vcalendar", "application/octet-stream", "*/*")) },
        onConfirm = vm::confirm,
        onCancel = vm::reset,
        onOpenSubjects = onOpenSubjects,
    )
}

@Composable
fun ImportScreen(
    stage: ImportStage,
    last: ImportInfo?,
    onBack: () -> Unit,
    onChoose: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onOpenSubjects: () -> Unit,
) {
    val c = Atlas.colors
    val zone = ZoneId.systemDefault()
    AtlasPage(
        mastheadLeft = "The Timetable",
        mastheadRight = last?.let { "${it.eventCount} imported" },
        title = "Import a Calendar",
        aside = "An .ics file from your timetable or calendar.",
        onBack = onBack,
    ) {
        Text(
            "Importing again replaces the previous import. Engagements you added by hand, subject names you changed and subjects you merged are kept.",
            style = Atlas.type.body, color = c.textSecondary,
        )
        if (last != null) {
            Spacer(Modifier.height(Atlas.space.m))
            val at = Instant.ofEpochMilli(last.importedAtUtc).atZone(zone)
            Text(
                "Last: ${last.fileName}, ${Formats.shortDate(at.toLocalDate())} at ${Formats.time(at.toLocalTime())}",
                style = Atlas.type.time, color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(Atlas.space.xl))

        when (stage) {
            ImportStage.Idle, is ImportStage.Failed -> {
                if (stage is ImportStage.Failed) {
                    Text(stage.message, style = Atlas.type.body, color = c.accentText)
                    Spacer(Modifier.height(Atlas.space.l))
                }
                AtlasButton("Choose an .ics file", onChoose, style = ActionStyle.Filled, modifier = Modifier.fillMaxWidth())
            }
            ImportStage.Reading -> Text("Reading the file…", style = Atlas.type.aside, color = c.textSecondary)
            is ImportStage.Preview -> {
                val r = stage.result
                val days = r.events.filter { !it.allDay }.map { it.start.atZone(zone).toLocalDate() }
                val subjects = r.events.map { SubjectNames.displayName(it.summary) }.distinctBy { it.lowercase() }.sorted()
                SectionTitle("I", "What the File Holds")
                RuledRow { Text("Engagements", style = Atlas.type.body, modifier = Modifier.weight(1f)); Text("${r.events.size}", style = Atlas.type.numeral) }
                if (days.isNotEmpty()) {
                    RuledRow {
                        Text("From", style = Atlas.type.body, modifier = Modifier.weight(1f))
                        Text("${Formats.shortDate(days.min())} to ${Formats.shortDate(days.max())}", style = Atlas.type.time)
                    }
                }
                RuledRow(showRule = false) {
                    Text("Subjects", style = Atlas.type.body, modifier = Modifier.weight(1f)); Text("${subjects.size}", style = Atlas.type.numeral)
                }
                Text(subjects.joinToString("  ·  "), style = Atlas.type.aside, color = c.textSecondary)
                if (r.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(Atlas.space.l))
                    r.warnings.take(5).forEach { Text(it, style = Atlas.type.bodySmall, color = c.accentText) }
                }
                Spacer(Modifier.height(Atlas.space.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(Atlas.space.s)) {
                    AtlasButton("Replace the timetable", onConfirm, style = ActionStyle.Filled, modifier = Modifier.weight(1f))
                    AtlasButton("Cancel", onCancel, style = ActionStyle.Outlined)
                }
            }
            is ImportStage.Done -> {
                val s = stage.summary
                SectionTitle("I", "Imported")
                Spacer(Modifier.height(Atlas.space.m))
                Text(
                    "${Formats.count(s.events, "engagement")}" +
                        (if (s.firstDay != null && s.lastDay != null) ", ${Formats.shortDate(s.firstDay!!)} to ${Formats.shortDate(s.lastDay!!)}" else "") +
                        ". ${Formats.count(s.newSubjects, "new subject")}.",
                    style = Atlas.type.body, color = c.text,
                )
                Spacer(Modifier.height(Atlas.space.s))
                Text("Places are being looked up on the map; alarms will follow.", style = Atlas.type.aside, color = c.textSecondary)
                Spacer(Modifier.height(Atlas.space.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(Atlas.space.s)) {
                    AtlasButton("Review subjects", onOpenSubjects, style = ActionStyle.Filled, modifier = Modifier.weight(1f))
                    AtlasButton("Done", onBack, style = ActionStyle.Outlined)
                }
            }
        }
    }
}

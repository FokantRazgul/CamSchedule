package app.camplanner.ui.subjects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasDialog
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.ChoiceRow
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.LedgerField
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.Subject
import app.camplanner.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubjectsViewModel(private val graph: DataGraph) : ViewModel() {
    val subjects: StateFlow<List<Subject>?> = graph.timetable.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(s: Subject) = viewModelScope.launch { graph.timetable.updateSubject(s) }
    fun merge(from: Long, into: Long) = viewModelScope.launch { graph.timetable.mergeSubjects(from, into) }
}

@Composable
fun SubjectsRoute(onBack: () -> Unit, onImport: () -> Unit) {
    val vm = appViewModel { graph, _ -> SubjectsViewModel(graph) }
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    subjects?.let { SubjectsScreen(it, onBack, onImport, vm::update, vm::merge) }
}

@Composable
fun SubjectsScreen(
    subjects: List<Subject>,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onUpdate: (Subject) -> Unit,
    onMerge: (from: Long, into: Long) -> Unit,
) {
    val c = Atlas.colors
    var editing by remember { mutableStateOf<Subject?>(null) }
    var merging by remember { mutableStateOf<Subject?>(null) }
    AtlasPage(
        mastheadLeft = "Subjects",
        mastheadRight = "${subjects.count { it.isAcademic }} academic",
        title = "The Subjects",
        aside = "Named from your timetable; rename, merge and choose which count for reading.",
        onBack = onBack,
    ) {
        if (subjects.isEmpty()) {
            Text("No subjects yet. They appear when you import a timetable.", style = Atlas.type.aside, color = c.textSecondary)
            Spacer(Modifier.height(Atlas.space.l))
            AtlasButton("Import a timetable", onImport, style = ActionStyle.Filled)
        }
        subjects.forEachIndexed { i, s ->
            Column(Modifier.fillMaxWidth().padding(vertical = Atlas.space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectMark(c.subject(s.colorIndex), Modifier.size(10.dp))
                    Spacer(Modifier.size(Atlas.space.m))
                    Text(
                        s.name, style = Atlas.type.title, color = c.text,
                        modifier = Modifier.weight(1f).clickable(role = Role.Button) { editing = s },
                    )
                }
                Spacer(Modifier.height(Atlas.space.s))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Atlas.space.s)) {
                    ChoiceRow(listOf(true to "Academic", false to "Other"), s.isAcademic, { onUpdate(s.copy(isAcademic = it)) })
                    Spacer(Modifier.weight(1f))
                    AtlasButton("Rename", { editing = s }, style = ActionStyle.Text)
                    if (subjects.size > 1) AtlasButton("Merge", { merging = s }, style = ActionStyle.Text)
                }
            }
            if (i != subjects.lastIndex) Hairline()
        }
    }

    editing?.let { s ->
        var name by remember(s.id) { mutableStateOf(s.name) }
        var color by remember(s.id) { mutableStateOf(s.colorIndex) }
        AtlasDialog(
            title = "Rename",
            onDismiss = { editing = null },
            actions = {
                AtlasButton("Cancel", { editing = null }, style = ActionStyle.Text)
                AtlasButton("Save", {
                    if (name.isNotBlank()) onUpdate(s.copy(name = name, colorIndex = color))
                    editing = null
                }, style = ActionStyle.Text)
            },
        ) {
            LedgerField(name, { name = it }, label = "Name")
            Spacer(Modifier.height(Atlas.space.l))
            Text("INK", style = Atlas.type.overline, color = c.textSecondary)
            Spacer(Modifier.height(Atlas.space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Atlas.space.s), verticalArrangement = Arrangement.spacedBy(Atlas.space.s)) {
                c.subjects.indices.forEach { idx ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable(role = Role.RadioButton) { color = idx },
                        contentAlignment = Alignment.Center,
                    ) {
                        SubjectMark(c.subject(idx), Modifier.size(if (idx == color) 18.dp else 11.dp))
                    }
                }
            }
        }
    }

    merging?.let { from ->
        AtlasDialog(
            title = "Merge “${from.name}” into…",
            onDismiss = { merging = null },
            actions = { AtlasButton("Cancel", { merging = null }, style = ActionStyle.Text) },
        ) {
            Text(
                "Its engagements, homework and reading move to the subject you choose, and future imports follow.",
                style = Atlas.type.bodySmall, color = c.textSecondary,
            )
            Spacer(Modifier.height(Atlas.space.m))
            subjects.filter { it.id != from.id }.forEach { target ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onMerge(from.id, target.id); merging = null }.padding(vertical = Atlas.space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Atlas.space.s),
                ) {
                    SubjectMark(c.subject(target.colorIndex))
                    Text(target.name, style = Atlas.type.body, color = c.text)
                }
                Hairline()
            }
        }
    }
}

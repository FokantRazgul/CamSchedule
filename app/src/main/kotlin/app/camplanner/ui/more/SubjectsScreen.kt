package app.camplanner.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.AppSwitch
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.EmptyState
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SettingRow
import app.camplanner.designsystem.kit.SheetBody
import app.camplanner.designsystem.kit.SubjectBar
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.Subject
import app.camplanner.ui.common.AppSheet
import app.camplanner.ui.common.Confirm
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubjectsViewModel(private val graph: DataGraph) : ViewModel() {
    val subjects: StateFlow<List<Subject>?> = graph.timetable.observeSubjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(name: String, academic: Boolean) = viewModelScope.launch {
        graph.timetable.addSubject(name, academic)
        Notices.show("Added $name")
    }

    fun save(s: Subject) = viewModelScope.launch { graph.timetable.updateSubject(s) }

    fun merge(from: Subject, into: Subject) = viewModelScope.launch {
        graph.timetable.mergeSubjects(from.id, into.id)
        Notices.show("Merged ${from.name} into ${into.name}")
    }

    fun delete(s: Subject) = viewModelScope.launch {
        graph.timetable.deleteSubject(s.id)
        Notices.show("Deleted ${s.name}")
    }
}

@Composable
fun SubjectsRoute(onBack: () -> Unit) {
    val vm = appViewModel { graph, _ -> SubjectsViewModel(graph) }
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Subject?>(null) }
    var adding by remember { mutableStateOf(false) }
    val c = Atlas.colors
    SubPage("Subjects", onBack, actions = { HeaderAction(Icons.Outlined.Add, "Add a subject", { adding = true }) }) {
        val list = subjects ?: return@SubPage
        Text(
            "Subjects come from your timetable's event titles. Academic subjects get a reading target on days they meet.",
            style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Group {
            if (list.isEmpty()) EmptyState("No subjects yet. Import a timetable or add one.", action = "Add a subject", onAction = { adding = true })
            list.forEachIndexed { i, s ->
                ItemRow(
                    title = s.name,
                    supporting = if (s.isAcademic) "Academic · reading tracked" else "Not academic",
                    leading = { SubjectBar(c.subject(s.colorIndex), height = 28.dp) },
                    trailing = { AppSwitch(s.isAcademic, { vm.save(s.copy(isAcademic = it)) }) },
                    onClick = { editing = s },
                )
                if (i != list.lastIndex) GroupDivider(inset = 36.dp)
            }
        }
    }
    editing?.let { s ->
        SubjectSheet(
            s, others = subjects.orEmpty().filter { it.id != s.id },
            onSave = vm::save, onMerge = { into -> vm.merge(s, into) }, onDelete = { vm.delete(s) },
            onDismiss = { editing = null },
        )
    }
    if (adding) NewSubjectSheet(onAdd = vm::add, onDismiss = { adding = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectSheet(
    subject: Subject,
    others: List<Subject>,
    onSave: (Subject) -> Unit,
    onMerge: (Subject) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(subject.name) }
    var color by remember { mutableStateOf(subject.colorIndex) }
    var academic by remember { mutableStateOf(subject.isAcademic) }
    var mergeInto by remember { mutableStateOf<Subject?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    val c = Atlas.colors
    AppSheet(onDismiss) {
        SheetBody("Subject") {
            AppTextField(name, { name = it }, "Name")
            Spacer(Modifier.height(16.dp))
            Text("Colour", style = Atlas.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                c.subjects.indices.forEach { i ->
                    val on = Math.floorMod(color, c.subjects.size) == i
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(c.subjects[i])
                            .border(2.dp, if (on) c.text else Color.Transparent, CircleShape)
                            .clickable(role = Role.RadioButton) { color = i }
                            .semantics { selected = on; contentDescription = "Colour ${i + 1}" },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingRow("Academic", supporting = "Track reading on days it meets") {
                AppSwitch(academic, { academic = it })
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "Save", { onSave(subject.copy(name = name.trim(), colorIndex = color, isAcademic = academic)); onDismiss() },
                Modifier.fillMaxWidth(), enabled = name.isNotBlank(),
            )
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Merge into another subject", style = Atlas.type.caption, color = c.textSecondary)
                Text(
                    "For the same course under two names. Its lectures, homework and reading move across.",
                    style = Atlas.type.supporting, color = c.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { o -> AppChip(o.name, false, { mergeInto = o }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            TertiaryButton("Delete subject", { askDelete = true }, Modifier.fillMaxWidth())
        }
    }
    mergeInto?.let { into ->
        Confirm(
            "Merge into ${into.name}?", "${subject.name} disappears and everything in it moves to ${into.name}.", "Merge",
            { onMerge(into); onDismiss() }, { mergeInto = null },
        )
    }
    if (askDelete) {
        Confirm(
            "Delete ${subject.name}?", "Its events and homework stay, without a subject. Its reading log is deleted.", "Delete",
            { onDelete(); onDismiss() }, { askDelete = false },
        )
    }
}

@Composable
private fun NewSubjectSheet(onAdd: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var academic by remember { mutableStateOf(true) }
    AppSheet(onDismiss) {
        SheetBody("New subject") {
            AppTextField(name, { name = it }, "Name", placeholder = "Linear Algebra")
            Spacer(Modifier.height(12.dp))
            SettingRow("Academic", supporting = "Track reading on days it meets") { AppSwitch(academic, { academic = it }) }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Add", { onAdd(name.trim(), academic); onDismiss() }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        }
    }
}

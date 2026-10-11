package app.camplanner.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.AppSwitch
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.SettingRow
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.Assignment
import app.camplanner.model.Subject
import app.camplanner.ui.common.Confirm
import app.camplanner.ui.common.DateBox
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.TimeBox
import app.camplanner.ui.common.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class HomeworkForm(
    val id: Long = 0,
    val title: String = "",
    val subjectId: Long? = null,
    val date: LocalDate,
    val time: LocalTime = LocalTime.of(9, 0),
    val description: String = "",
    val done: Boolean = false,
    val completedAt: java.time.Instant? = null,
) {
    fun toAssignment(zone: ZoneId) = Assignment(
        id = id, title = title.trim(), subjectId = subjectId, description = description.trim(),
        deadline = date.atTime(time).atZone(zone).toInstant(), done = done, completedAt = completedAt,
    )

    companion object {
        fun of(a: Assignment, zone: ZoneId): HomeworkForm {
            val due = a.deadline.atZone(zone)
            return HomeworkForm(a.id, a.title, a.subjectId, due.toLocalDate(), due.toLocalTime(), a.description, a.done, a.completedAt)
        }
    }
}

class HomeworkEditorViewModel(private val graph: DataGraph, private val id: Long?) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val _form = MutableStateFlow<HomeworkForm?>(null)
    val form: StateFlow<HomeworkForm?> = _form.asStateFlow()
    var initial: HomeworkForm? = null
        private set
    val subjects: StateFlow<List<Subject>> = graph.timetable.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val f = id?.let { graph.homework.get(it) }?.let { HomeworkForm.of(it, zone) }
                ?: HomeworkForm(date = LocalDate.now(zone).plusDays(7))
            initial = f
            _form.value = f
        }
    }

    fun change(f: (HomeworkForm) -> HomeworkForm) {
        _form.value = _form.value?.let(f)
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val f = _form.value ?: return@launch
        if (f.title.isBlank()) {
            Notices.show("Add a title")
            return@launch
        }
        graph.homework.save(f.toAssignment(zone))
        Notices.show(if (f.id == 0L) "Added ${f.title.trim()}" else "Saved")
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val removed = id?.let { graph.homework.delete(it) } ?: return@launch
        Notices.show("Deleted ${removed.title}") { graph.homework.save(removed) }
        onDone()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeworkEditorRoute(id: Long?, onBack: () -> Unit) {
    val vm = appViewModel(key = "homework-$id") { graph, _ -> HomeworkEditorViewModel(graph, id) }
    val form by vm.form.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val dirty = form != null && form != vm.initial
    BackHandler(enabled = dirty) { confirmLeave = true }
    val c = Atlas.colors

    SubPage(
        title = if (id == null) "New homework" else "Homework",
        onBack = { if (dirty) confirmLeave = true else onBack() },
        actions = {
            if (id != null) HeaderAction(Icons.Outlined.DeleteOutline, "Delete", { vm.delete(onBack) })
            TertiaryButton("Save", { vm.save(onBack) })
        },
    ) {
        val f = form ?: return@SubPage
        Spacer(Modifier.height(8.dp))
        AppTextField(f.title, { v -> vm.change { it.copy(title = v) } }, "Title", placeholder = "Example sheet 3, essay…")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateBox("Due", f.date, { d -> vm.change { it.copy(date = d) } }, Modifier.weight(1.4f))
            TimeBox("Time", f.time, { t -> vm.change { it.copy(time = t) } }, Modifier.weight(1f))
        }
        if (subjects.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Subject", style = Atlas.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("None", f.subjectId == null, { vm.change { it.copy(subjectId = null) } })
                subjects.forEach { s -> AppChip(s.name, f.subjectId == s.id, { vm.change { it.copy(subjectId = s.id) } }) }
            }
        }
        Spacer(Modifier.height(18.dp))
        AppTextField(f.description, { v -> vm.change { it.copy(description = v) } }, "Notes", singleLine = false, placeholder = "Questions, pages, supervisor…")
        if (id != null) {
            Spacer(Modifier.height(18.dp))
            Group {
                SettingRow("Done") { AppSwitch(f.done, { d -> vm.change { it.copy(done = d, completedAt = null) } }) }
            }
        }
    }
    if (confirmLeave) Confirm("Discard changes?", "What you changed here won't be saved.", "Discard", onBack, { confirmLeave = false })
}

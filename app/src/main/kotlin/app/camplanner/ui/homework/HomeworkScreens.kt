package app.camplanner.ui.homework

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import app.camplanner.designsystem.components.Glyphs
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.LedgerField
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.components.drawChartStar
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.Assignment
import app.camplanner.model.Subject
import app.camplanner.ui.common.DateField
import app.camplanner.ui.common.TimeField
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.common.minuteTicker
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class HomeworkList(val items: List<Assignment>, val subjects: Map<Long, Subject>, val now: Instant)

class HomeworkViewModel(private val graph: DataGraph) : ViewModel() {
    val state: StateFlow<HomeworkList?> = combine(
        graph.homework.observeAll(), graph.timetable.observeSubjects(), minuteTicker(),
    ) { items, subjects, now ->
        HomeworkList(items, subjects.associateBy { it.id }, now.atZone(ZoneId.systemDefault()).toInstant())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(id: Long, done: Boolean) = viewModelScope.launch { graph.homework.setDone(id, done) }
}

@Composable
fun HomeworkRoute(onOpen: (Long) -> Unit) {
    val vm = appViewModel { graph, _ -> HomeworkViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { HomeworkScreen(it, onOpen, vm::toggle) }
}

@Composable
fun HomeworkScreen(state: HomeworkList, onOpen: (Long) -> Unit, onToggle: (Long, Boolean) -> Unit) {
    val c = Atlas.colors
    val overdue = state.items.filter { !it.done && it.deadline.isBefore(state.now) }
    val upcoming = state.items.filter { !it.done && !it.deadline.isBefore(state.now) }
    val done = state.items.filter { it.done }.sortedByDescending { it.completedAt ?: it.deadline }.take(10)
    AtlasPage(
        mastheadLeft = "Homework",
        mastheadRight = if (overdue.isNotEmpty()) "${overdue.size} overdue" else "${upcoming.size} to do",
        title = "Assignments",
        aside = "By deadline, the nearest first.",
        graticule = true,
    ) {
        AtlasButton("Add an assignment", { onOpen(0) }, style = ActionStyle.Filled, glyph = Glyphs.Plus, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Atlas.space.xl))
        var n = 0
        if (overdue.isNotEmpty()) {
            SectionTitle(roman(++n), "Overdue") { Text("${overdue.size}", style = Atlas.type.time, color = c.accentText) }
            overdue.forEachIndexed { i, a -> AssignmentRow(a, state, overdue = true, last = i == overdue.lastIndex, onOpen, onToggle) }
            Spacer(Modifier.height(Atlas.space.xl))
        }
        SectionTitle(roman(++n), "Due") { Text("${upcoming.size}", style = Atlas.type.time, color = c.textSecondary) }
        if (upcoming.isEmpty()) {
            Text("Nothing outstanding.", style = Atlas.type.aside, color = c.textSecondary, modifier = Modifier.padding(vertical = Atlas.space.m))
        }
        upcoming.forEachIndexed { i, a -> AssignmentRow(a, state, overdue = false, last = i == upcoming.lastIndex, onOpen, onToggle) }
        if (done.isNotEmpty()) {
            Spacer(Modifier.height(Atlas.space.xl))
            SectionTitle(roman(++n), "Done") { Text("latest ${done.size}", style = Atlas.type.time, color = c.textSecondary) }
            done.forEachIndexed { i, a -> AssignmentRow(a, state, overdue = false, last = i == done.lastIndex, onOpen, onToggle) }
        }
    }
}

private fun roman(n: Int) = app.camplanner.domain.Roman.of(n)

@Composable
private fun AssignmentRow(
    a: Assignment,
    state: HomeworkList,
    overdue: Boolean,
    last: Boolean,
    onOpen: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
) {
    val c = Atlas.colors
    val zone = ZoneId.systemDefault()
    val due = a.deadline.atZone(zone)
    val today = state.now.atZone(zone).toLocalDate()
    val subject = a.subjectId?.let(state.subjects::get)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(a.id) }.padding(vertical = Atlas.space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoneMark(a.done, onToggle = { onToggle(a.id, !a.done) })
        Spacer(Modifier.size(Atlas.space.m))
        Column(Modifier.weight(1f)) {
            Text(
                a.title, style = Atlas.type.title,
                color = if (a.done) c.textSecondary else c.text,
                textDecoration = if (a.done) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (subject != null) {
                    SubjectMark(c.subject(subject.colorIndex))
                    Text(subject.name, style = Atlas.type.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("·", style = Atlas.type.bodySmall, color = c.textSecondary)
                }
                Text(
                    "${Formats.shortDate(due.toLocalDate())} ${Formats.time(due.toLocalTime())}" +
                        if (!a.done) "  (${Formats.relativeDay(due.toLocalDate(), today)})" else "",
                    style = Atlas.type.time,
                    color = if (overdue) c.accentText else c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
    if (!last) Hairline()
}

/** A drawn circle that fills gold with a star when the assignment is done. */
@Composable
private fun DoneMark(done: Boolean, onToggle: () -> Unit) {
    val c = Atlas.colors
    Canvas(
        Modifier
            .size(36.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics {
                contentDescription = "Done"
                stateDescription = if (done) "done" else "not done"
            }
            .padding(8.dp),
    ) {
        val r = size.minDimension / 2f
        if (done) {
            drawCircle(c.gold.copy(alpha = 0.18f), r)
            drawCircle(c.gold, r, style = Stroke(1.2f))
            drawChartStar(center, r * 0.62f, c.gold)
        } else {
            drawCircle(c.engraving, r, style = Stroke(1.2f))
        }
    }
}

// --- Edit -------------------------------------------------------------------------------------

data class AssignmentForm(
    val title: String = "",
    val subjectId: Long? = null,
    val description: String = "",
    val date: LocalDate = LocalDate.now().plusDays(1),
    val time: LocalTime = LocalTime.of(12, 0),
    val done: Boolean = false,
)

class HomeworkEditViewModel(private val graph: DataGraph, private val id: Long) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    val form = MutableStateFlow(AssignmentForm())
    val finished = MutableStateFlow(false)
    val subjects: StateFlow<List<Subject>> = graph.timetable.observeSubjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (id != 0L) viewModelScope.launch {
            graph.homework.get(id)?.let { a ->
                val due = a.deadline.atZone(zone)
                form.value = AssignmentForm(a.title, a.subjectId, a.description, due.toLocalDate(), due.toLocalTime(), a.done)
            }
        }
    }

    fun edit(change: (AssignmentForm) -> AssignmentForm) = form.update(change)

    fun save() {
        val f = form.value
        if (f.title.isBlank()) return
        viewModelScope.launch {
            graph.homework.save(
                Assignment(id, f.title, f.subjectId, f.description, f.date.atTime(f.time).atZone(zone).toInstant(), f.done),
            )
            finished.value = true
        }
    }

    fun delete() = viewModelScope.launch {
        graph.homework.delete(id)
        finished.value = true
    }
}

@Composable
fun HomeworkEditRoute(id: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "hw-$id") { graph, _ -> HomeworkEditViewModel(graph, id) }
    val form by vm.form.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onBack() }
    HomeworkEditScreen(id == 0L, form, subjects, vm::edit, vm::save, vm::delete, onBack)
}

@Composable
fun HomeworkEditScreen(
    isNew: Boolean,
    form: AssignmentForm,
    subjects: List<Subject>,
    onChange: ((AssignmentForm) -> AssignmentForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Atlas.colors
    var choosingSubject by remember { mutableStateOf(false) }
    AtlasPage(
        mastheadLeft = "Homework",
        mastheadRight = null,
        title = if (isNew) "A New Assignment" else "The Assignment",
        onBack = onBack,
    ) {
        LedgerField(form.title, { v -> onChange { it.copy(title = v) } }, label = "Title", placeholder = "Example Sheet 3")
        Spacer(Modifier.height(Atlas.space.xl))
        Text("SUBJECT", style = Atlas.type.overline, color = c.textSecondary)
        val subject = subjects.firstOrNull { it.id == form.subjectId }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { choosingSubject = true }.padding(vertical = Atlas.space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Atlas.space.s),
        ) {
            if (subject != null) SubjectMark(c.subject(subject.colorIndex))
            Text(subject?.name ?: "None", style = Atlas.type.title, color = if (subject != null) c.text else c.textSecondary)
        }
        Hairline()
        Spacer(Modifier.height(Atlas.space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Atlas.space.xl)) {
            DateField("Due", form.date, { d -> onChange { it.copy(date = d) } }, Modifier.weight(1f))
            TimeField("At", form.time, { t -> onChange { it.copy(time = t) } }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Atlas.space.xl))
        LedgerField(form.description, { v -> onChange { it.copy(description = v) } }, label = "Description", singleLine = false, placeholder = "Questions 1 to 6; read chapter 4 first")
        Spacer(Modifier.height(Atlas.space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Status", style = Atlas.type.body, color = c.text, modifier = Modifier.weight(1f))
            ChoiceRow(listOf(false to "Not done", true to "Done"), form.done, { v -> onChange { it.copy(done = v) } })
        }
        Spacer(Modifier.height(Atlas.space.xxl))
        AtlasButton("Save", onSave, style = ActionStyle.Filled, enabled = form.title.isNotBlank(), modifier = Modifier.fillMaxWidth())
        if (!isNew) {
            Spacer(Modifier.height(Atlas.space.m))
            AtlasButton("Delete", onDelete, style = ActionStyle.Text)
        }
    }

    if (choosingSubject) {
        AtlasDialog(
            title = "Subject",
            onDismiss = { choosingSubject = false },
            actions = { AtlasButton("Cancel", { choosingSubject = false }, style = ActionStyle.Text) },
        ) {
            (listOf<Subject?>(null) + subjects).forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        onChange { it.copy(subjectId = s?.id) }
                        choosingSubject = false
                    }.padding(vertical = Atlas.space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Atlas.space.s),
                ) {
                    if (s != null) SubjectMark(c.subject(s.colorIndex))
                    Text(s?.name ?: "None", style = Atlas.type.body, color = c.text)
                }
                Hairline()
            }
        }
    }
}

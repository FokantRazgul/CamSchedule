package app.camplanner.ui.calendar

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.repo.Scope
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.model.Subject
import app.camplanner.model.TravelMode
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class EventEditorViewModel(
    private val graph: DataGraph,
    private val eventId: Long?,
    date: LocalDate?,
    private val scope: Scope,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val _form = MutableStateFlow<EditorForm?>(null)
    val form: StateFlow<EditorForm?> = _form.asStateFlow()
    var initial: EditorForm? = null
        private set

    val subjects: StateFlow<List<Subject>> = graph.timetable.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val loaded = if (eventId == null) {
                EditorForm.new(date ?: LocalDate.now(zone), LocalDateTime.now(zone))
            } else {
                val e = graph.timetable.event(eventId) ?: return@launch
                EditorForm.of(e, e.seriesId?.let { graph.timetable.series(it) }, scope == Scope.ALL, zone)
            }
            initial = loaded
            _form.value = loaded
        }
    }

    fun change(f: (EditorForm) -> EditorForm) {
        _form.value = _form.value?.let(f)
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val f = _form.value ?: return@launch
        f.problem?.let {
            Notices.show(it)
            return@launch
        }
        val draft = f.toDraft()
        if (eventId == null) {
            graph.timetable.create(draft)
            Notices.show(if (draft.repeat != null) "Added ${draft.title}, ${f.summary()?.replaceFirstChar(Char::lowercase)}" else "Added ${draft.title}")
        } else {
            graph.timetable.update(eventId, draft, scope)
            Notices.show("Saved")
        }
        onDone()
    }
}

@Composable
fun EventEditorRoute(eventId: Long?, date: LocalDate?, scope: Scope, onBack: () -> Unit) {
    val vm = appViewModel(key = "editor-$eventId-$date-$scope") { graph, _ -> EventEditorViewModel(graph, eventId, date, scope) }
    val form by vm.form.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val dirty = form != null && form != vm.initial
    val leave = { if (dirty) confirmLeave = true else onBack() }
    BackHandler(enabled = dirty) { confirmLeave = true }

    val f = form
    SubPage(
        title = when {
            f == null -> ""
            f.isNew -> "New event"
            f.imported -> "Change this lecture"
            scope == Scope.ALL -> "Edit all in series"
            else -> "Edit event"
        },
        onBack = leave,
        actions = { TertiaryButton("Save", { vm.save(onBack) }, color = if (f?.problem == null) Atlas.colors.accentText else Atlas.colors.textSecondary) },
    ) {
        if (f != null) EventEditorContent(f, subjects, vm::change)
    }
    if (confirmLeave) {
        Confirm("Discard changes?", "What you changed here won't be saved.", "Discard", onBack, { confirmLeave = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditorContent(f: EditorForm, subjects: List<Subject>, change: ((EditorForm) -> EditorForm) -> Unit) {
    val c = Atlas.colors
    Spacer(Modifier.height(8.dp))
    if (f.imported) {
        Text(f.title, style = Atlas.type.groupTitle, color = c.text)
        Text(
            "From your timetable. Changes here apply to this lecture only and survive a re-import.",
            style = Atlas.type.supporting, color = c.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
    } else {
        AppTextField(f.title, { v -> change { it.copy(title = v) } }, "Title", placeholder = "Supervision, society, gym…")
        Spacer(Modifier.height(12.dp))
    }

    DateBox("Date", f.date, { d -> change { it.copy(date = d) } }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeBox("Starts", f.start, { t -> change { it.withStart(t) } }, Modifier.weight(1f))
        TimeBox("Ends", f.end, { t -> change { it.copy(end = t) } }, Modifier.weight(1f))
    }
    if (f.overnight) Hint("Ends the next day")

    if (f.canRepeat) {
        Label("Repeats")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatChoice.entries.forEach { r -> AppChip(r.label, f.repeat == r, { change { it.withRepeat(r) } }) }
        }
        if (f.repeat == RepeatChoice.WEEKLY || f.repeat == RepeatChoice.FORTNIGHTLY) {
            Spacer(Modifier.height(12.dp))
            WeekdayPicker(f.days) { d -> change { st -> st.copy(days = if (d in st.days && st.days.size > 1) st.days - d else st.days + d) } }
        }
        if (f.repeat != RepeatChoice.NEVER) {
            Label("Ends")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EndsChoice.entries.forEach { e -> AppChip(e.label, f.ends == e, { change { it.copy(ends = e) } }) }
            }
            when (f.ends) {
                EndsChoice.ON_DATE -> {
                    Spacer(Modifier.height(10.dp))
                    DateBox("Last day", f.until, { d -> change { it.copy(until = d) } }, Modifier.fillMaxWidth())
                }
                EndsChoice.AFTER -> {
                    Spacer(Modifier.height(10.dp))
                    AppTextField(
                        f.count, { v -> change { it.copy(count = v.filter(Char::isDigit).take(3)) } }, "How many times",
                        keyboardType = KeyboardType.Number, suffix = "times",
                    )
                }
                EndsChoice.NEVER -> Unit
            }
            f.summary()?.let { Hint(it) }
        }
    }

    if (!f.imported && subjects.isNotEmpty()) {
        Label("Subject")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("None", f.subjectId == null, { change { it.copy(subjectId = null) } })
            subjects.forEach { s -> AppChip(s.name, f.subjectId == s.id, { change { it.copy(subjectId = s.id) } }) }
        }
    }

    Spacer(Modifier.height(18.dp))
    AppTextField(f.location, { v -> change { it.copy(location = v) } }, "Place", placeholder = "Used for routes and leave alerts")

    Label("Getting there")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppChip("Usual", f.mode == null, { change { it.copy(mode = null) } })
        AppChip("Walk", f.mode == TravelMode.WALK, { change { it.copy(mode = TravelMode.WALK) } })
        AppChip("Bike", f.mode == TravelMode.BIKE, { change { it.copy(mode = TravelMode.BIKE) } })
    }

    Label("Reminder")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Usual", 0 to "None", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 1440 to "1 day").forEach { (m, label) ->
            AppChip(label, f.reminder == m, { change { it.copy(reminder = m) } })
        }
    }

    if (!f.imported) {
        Spacer(Modifier.height(18.dp))
        AppTextField(f.note, { v -> change { it.copy(note = v) } }, "Notes", singleLine = false)
    }
    f.problem?.takeIf { f.title.isNotBlank() || !f.isNew }?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = Atlas.type.supporting, color = c.accentText)
    }
}

@Composable
private fun WeekdayPicker(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val c = Atlas.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val on = d in days
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (on) c.accentFill else Color.Transparent)
                    .border(1.dp, if (on) c.accentFill else c.hairline, CircleShape)
                    .clickable(role = Role.Checkbox) { onToggle(d) }
                    .semantics {
                        selected = on
                        contentDescription = d.getDisplayName(TextStyle.FULL, Locale.UK)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(d.getDisplayName(TextStyle.NARROW, Locale.UK), style = Atlas.type.button, color = if (on) c.text else c.textSecondary)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = Atlas.type.caption, color = Atlas.colors.textSecondary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = Atlas.type.supporting, color = Atlas.colors.textSecondary)
}

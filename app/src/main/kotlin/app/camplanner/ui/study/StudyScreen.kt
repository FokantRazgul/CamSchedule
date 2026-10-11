package app.camplanner.ui.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.kit.AddButton
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.EmptyState
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.LargeHeader
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SheetBody
import app.camplanner.designsystem.kit.Stepper
import app.camplanner.designsystem.kit.SubjectBar
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.Reading
import app.camplanner.model.Assignment
import app.camplanner.model.Subject
import app.camplanner.ui.common.AppSheet
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.TabPage
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.common.minuteTicker
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReadingRow(val subject: Subject, val pages: Int, val scheduled: Boolean)

data class HomeworkRow(
    val id: Long,
    val title: String,
    val detail: String,
    val subjectColor: Int?,
    val overdue: Boolean,
    val done: Boolean,
)

data class StudyState(
    val today: LocalDate,
    val readingDate: LocalDate,
    val minPages: Int,
    val maxPages: Int,
    /** Subjects with a lecture that day, or pages logged. */
    val reading: List<ReadingRow>,
    /** Every other academic subject, for reading done without a lecture. */
    val otherSubjects: List<Subject>,
    val todo: List<HomeworkRow>,
    val done: List<HomeworkRow>,
)

object StudyBuilder {
    fun homeworkRows(list: List<Assignment>, subjects: Map<Long, Subject>, now: Instant, zone: ZoneId): Pair<List<HomeworkRow>, List<HomeworkRow>> {
        val today = now.atZone(zone).toLocalDate()
        fun row(a: Assignment): HomeworkRow {
            val due = a.deadline.atZone(zone)
            val overdue = !a.done && a.deadline.isBefore(now)
            val subject = a.subjectId?.let(subjects::get)
            val whenText = "${Formats.dayLabel(due.toLocalDate(), today)} ${Formats.time(due.toLocalTime())}"
            return HomeworkRow(
                a.id, a.title,
                listOfNotNull(if (overdue) "Overdue · $whenText" else if (a.done) "Was due $whenText" else "Due $whenText", subject?.name).joinToString(" · "),
                subject?.colorIndex, overdue, a.done,
            )
        }
        val todo = list.filter { !it.done }.sortedBy { it.deadline }.map(::row)
        val done = list.filter { it.done }.sortedByDescending { it.completedAt ?: it.deadline }.map(::row)
        return todo to done
    }
}

class StudyViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    /** 0 = reading for today, 1 = for yesterday. */
    private val readingBack = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StudyState?> = combine(
        minuteTicker().map { it.toLocalDate() }.distinctUntilChanged(), readingBack,
    ) { today, back -> today to today.minusDays(back) }
        .flatMapLatest { (today, day) ->
            combine(
                graph.timetable.observeDays(day, day, zone),
                graph.timetable.observeSubjects(),
                graph.reading.observeDay(day),
                graph.homework.observeAll(),
                graph.settings.settings,
            ) { events, subjects, logs, homework, settings ->
                val academic = subjects.filter { it.isAcademic }
                val scheduled = Reading.subjectsOn(day, events, academic.map { it.id }.toSet(), zone)
                val pages = logs.associate { it.subjectId to it.pages }
                val shown = academic.filter { it.id in scheduled || (pages[it.id] ?: 0) > 0 }
                val (todo, done) = StudyBuilder.homeworkRows(homework, subjects.associateBy { it.id }, Instant.now(), zone)
                StudyState(
                    today = today,
                    readingDate = day,
                    minPages = settings.readingMinPages,
                    maxPages = settings.readingMaxPages,
                    reading = shown.map { ReadingRow(it, pages[it.id] ?: 0, it.id in scheduled) }
                        .sortedWith(compareByDescending<ReadingRow> { it.scheduled }.thenBy { it.subject.name }),
                    otherSubjects = academic.filter { s -> shown.none { it.id == s.id } },
                    todo = todo,
                    done = done,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun readingDay(back: Long) {
        readingBack.value = back.coerceIn(0, 1)
    }

    fun setPages(subjectId: Long, pages: Int) = viewModelScope.launch {
        val date = state.value?.readingDate ?: return@launch
        graph.reading.setPages(date, subjectId, pages.coerceAtLeast(0))
    }

    fun setDone(id: Long, done: Boolean) = viewModelScope.launch {
        graph.homework.setDone(id, done)
        Notices.show(if (done) "Marked as done" else "Back on the list") { graph.homework.setDone(id, !done) }
    }
}

@Composable
fun StudyRoute(onOpenHomework: (Long?) -> Unit, onSubjects: () -> Unit) {
    val vm = appViewModel { graph, _ -> StudyViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { StudyScreen(it, vm::readingDay, vm::setPages, vm::setDone, onOpenHomework, onSubjects) }
}

@Composable
fun StudyScreen(
    s: StudyState,
    onReadingDay: (Long) -> Unit,
    onPages: (Long, Int) -> Unit,
    onDone: (Long, Boolean) -> Unit,
    onOpenHomework: (Long?) -> Unit,
    onSubjects: () -> Unit,
) {
    val c = Atlas.colors
    var showDone by rememberSaveable { mutableStateOf(false) }
    var pagesFor by remember { mutableStateOf<ReadingRow?>(null) }
    var picking by remember { mutableStateOf(false) }
    val yesterday = s.readingDate != s.today
    TabPage(
        header = { LargeHeader("Homework") },
        fab = { AddButton("New", Icons.Outlined.Add, { onOpenHomework(null) }) },
    ) {
        GroupHeader("Reading") {
            AppChip("Today", !yesterday, { onReadingDay(0) })
            Spacer(Modifier.width(6.dp))
            AppChip("Yesterday", yesterday, { onReadingDay(1) })
        }
        Group {
            if (s.reading.isEmpty()) {
                EmptyState(
                    if (s.otherSubjects.isEmpty()) "Mark subjects as academic to track reading for them."
                    else "No lectures ${if (yesterday) "yesterday" else "today"}, so no reading is due.",
                    action = if (s.otherSubjects.isEmpty()) "Subjects" else null,
                    onAction = onSubjects,
                )
            }
            s.reading.forEachIndexed { i, r ->
                ReadingItem(r, s.minPages, s.maxPages, onPages = { onPages(r.subject.id, it) }, onClick = { pagesFor = r })
                if (i != s.reading.lastIndex) GroupDivider()
            }
            if (s.otherSubjects.isNotEmpty()) {
                if (s.reading.isNotEmpty()) GroupDivider(inset = 0.dp)
                TertiaryButton("Log reading for another subject", { picking = true }, Modifier.padding(horizontal = 4.dp), icon = Icons.Outlined.Add)
            }
        }
        Text(
            "Aim for ${s.minPages}–${s.maxPages} pages for each subject with a lecture that day.",
            style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )

        GroupHeader("Homework") {
            AppChip("To do", !showDone, { showDone = false })
            Spacer(Modifier.width(6.dp))
            AppChip("Done", showDone, { showDone = true })
        }
        val list = if (showDone) s.done else s.todo
        Group {
            if (list.isEmpty()) {
                EmptyState(
                    if (showDone) "Finished work shows up here." else "Nothing to hand in. Add work as it is set.",
                    action = if (showDone) null else "Add homework",
                    onAction = { onOpenHomework(null) },
                )
            }
            list.forEachIndexed { i, h ->
                HomeworkItem(h, onToggle = { onDone(h.id, it) }, onClick = { onOpenHomework(h.id) })
                if (i != list.lastIndex) GroupDivider(inset = 56.dp)
            }
        }
    }

    pagesFor?.let { r -> PagesSheet(r, s.readingDate, s.today, onSave = { onPages(r.subject.id, it) }, onDismiss = { pagesFor = null }) }
    if (picking) {
        AppSheet({ picking = false }) {
            SheetBody("Which subject?") {
                Group {
                    s.otherSubjects.forEachIndexed { i, subject ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                                picking = false
                                pagesFor = ReadingRow(subject, 0, false)
                            }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SubjectBar(c.subject(subject.colorIndex), height = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(subject.name, style = Atlas.type.itemTitle, color = c.text)
                        }
                        if (i != s.otherSubjects.lastIndex) GroupDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingItem(r: ReadingRow, min: Int, max: Int, onPages: (Int) -> Unit, onClick: () -> Unit) {
    val c = Atlas.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectBar(c.subject(r.subject.colorIndex), height = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.subject.name, style = Atlas.type.itemTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    r.pages >= max -> "Above the range · well done"
                    r.pages >= min -> "Target met"
                    r.scheduled -> "${min - r.pages} pages to go"
                    else -> "No lecture, extra reading"
                },
                style = Atlas.type.supporting, color = if (r.pages >= min) c.gold else c.textSecondary,
            )
        }
        Stepper(r.pages, onPages, step = 5, max = 999, description = "pages of ${r.subject.name}")
    }
}

@Composable
private fun HomeworkItem(h: HomeworkRow, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val c = Atlas.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = h.done, onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = c.gold, uncheckedColor = c.textSecondary, checkmarkColor = c.background),
        )
        Column(Modifier.weight(1f)) {
            Text(
                h.title, style = Atlas.type.itemTitle, color = if (h.done) c.textSecondary else c.text,
                textDecoration = if (h.done) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(h.detail, style = Atlas.type.supporting, color = if (h.overdue) c.accentText else c.textSecondary)
        }
        if (h.subjectColor != null) {
            Spacer(Modifier.width(8.dp))
            SubjectBar(c.subject(h.subjectColor), height = 28.dp)
        }
    }
}

@Composable
private fun PagesSheet(r: ReadingRow, date: LocalDate, today: LocalDate, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (r.pages > 0) r.pages.toString() else "") }
    AppSheet(onDismiss) {
        SheetBody(r.subject.name) {
            Text("Pages read ${Formats.dayLabel(date, today).lowercase()}", style = Atlas.type.reading, color = Atlas.colors.textSecondary)
            Spacer(Modifier.height(14.dp))
            AppTextField(text, { text = it.filter(Char::isDigit).take(4) }, "Pages", keyboardType = KeyboardType.Number, suffix = "pages")
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (r.pages > 0) TertiaryButton("Clear", { onSave(0); onDismiss() }, color = Atlas.colors.textSecondary)
                PrimaryButton("Save", { onSave(text.toIntOrNull() ?: 0); onDismiss() }, Modifier.weight(1f))
            }
        }
    }
}

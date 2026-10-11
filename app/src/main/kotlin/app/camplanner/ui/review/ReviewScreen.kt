package app.camplanner.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.designsystem.kit.CountButton
import app.camplanner.designsystem.kit.EmptyState
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.Stepper
import app.camplanner.designsystem.kit.SubjectBar
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.CheckIn
import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.Subject
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReviewReading(val subject: Subject, val pages: Int)
data class ReviewHomework(val id: Long, val title: String, val detail: String, val overdue: Boolean, val done: Boolean)
data class ReviewGap(val id: Long, val name: String, val unit: ExerciseUnit, val done: Int, val target: Int) {
    val remaining: Int get() = (target - done).coerceAtLeast(0)
}

data class ReviewState(
    val date: LocalDate,
    val minPages: Int,
    val reading: List<ReviewReading>,
    val homework: List<ReviewHomework>,
    val fitness: List<ReviewGap>,
    val restDay: Boolean,
    val completed: Boolean,
)

class ReviewViewModel(private val graph: DataGraph, private val date: LocalDate) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    private data class Sources(
        val events: List<CalendarEvent>,
        val subjects: List<Subject>,
        val homework: List<Assignment>,
        val exercises: List<Exercise>,
        val sets: List<ExerciseSet>,
    )

    private val sources = combine(
        graph.timetable.observeDays(date, date, zone),
        graph.timetable.observeSubjects(),
        graph.homework.observeAll(),
        graph.fitness.observeExercises(),
        graph.fitness.observeSets(),
    ) { e, s, h, x, sets -> Sources(e, s, h, x, sets) }

    val state: StateFlow<ReviewState?> = combine(
        sources, graph.reading.observeDay(date), graph.settings.settings, graph.checkIns.observeCompleted(date),
    ) { src, reading, settings, completed ->
        val plan = CheckIn.plan(date, zone, src.subjects, src.events, src.homework, src.exercises, src.sets, settings.restDays)
        val pages = reading.associate { it.subjectId to it.pages }
        val names = src.subjects.associateBy { it.id }
        val now = Instant.now()
        ReviewState(
            date = date,
            minPages = settings.readingMinPages,
            reading = plan.readingSubjects.map { ReviewReading(it, pages[it.id] ?: 0) },
            homework = plan.homework.map { a ->
                val due = a.deadline.atZone(zone)
                val overdue = !a.done && a.deadline.isBefore(now)
                ReviewHomework(
                    a.id, a.title,
                    listOfNotNull(
                        (if (overdue) "Overdue · " else "Due ") + "${Formats.dayLabel(due.toLocalDate(), date)} ${Formats.time(due.toLocalTime())}",
                        a.subjectId?.let(names::get)?.name,
                    ).joinToString(" · "),
                    overdue, a.done,
                )
            },
            fitness = plan.fitnessShortfall.map { ReviewGap(it.exercise.id, it.exercise.name, it.exercise.unit, it.done, it.target) },
            restDay = plan.isRestDay,
            completed = completed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPages(subjectId: Long, pages: Int) = viewModelScope.launch { graph.reading.setPages(date, subjectId, pages) }

    fun setDone(id: Long, done: Boolean) = viewModelScope.launch { graph.homework.setDone(id, done) }

    fun logRest(g: ReviewGap) = viewModelScope.launch {
        val id = graph.fitness.log(g.id, g.remaining, date) ?: return@launch
        Notices.show("Logged ${g.remaining} ${g.name.lowercase()}") { graph.fitness.deleteSet(id) }
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        graph.checkIns.complete(date)
        Notices.show("Review saved. Good night.")
        onDone()
    }
}

@Composable
fun ReviewRoute(onBack: () -> Unit) {
    val today = LocalDate.now()
    val vm = appViewModel(key = "review-$today") { graph, _ -> ReviewViewModel(graph, today) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Atlas.colors
    SubPage(
        "Evening review", onBack,
        bottomBar = {
            state?.let { s ->
                PrimaryButton(
                    if (s.completed) "Done" else "Finish review",
                    { if (s.completed) onBack() else vm.finish(onBack) },
                    Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        },
    ) {
        val s = state ?: return@SubPage
        Text(
            "${Formats.weekday(s.date)} ${Formats.dayMonth(s.date)}" + if (s.completed) " · reviewed" else "",
            style = Atlas.type.supporting, color = if (s.completed) c.gold else c.textSecondary,
        )

        GroupHeader("Reading")
        Group {
            if (s.reading.isEmpty()) EmptyState("No lectures today, so no reading is due.")
            s.reading.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SubjectBar(c.subject(r.subject.colorIndex), height = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.subject.name, style = Atlas.type.itemTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (r.pages >= s.minPages) "Target met" else "${s.minPages - r.pages} pages to go",
                            style = Atlas.type.supporting, color = if (r.pages >= s.minPages) c.gold else c.textSecondary,
                        )
                    }
                    Stepper(r.pages, { vm.setPages(r.subject.id, it) }, step = 5, description = "pages of ${r.subject.name}")
                }
                if (i != s.reading.lastIndex) GroupDivider()
            }
        }

        GroupHeader("Homework")
        Group {
            if (s.homework.isEmpty()) EmptyState("Nothing due in the next three days.")
            s.homework.forEachIndexed { i, h ->
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = h.done, onCheckedChange = { vm.setDone(h.id, it) },
                        colors = CheckboxDefaults.colors(checkedColor = c.gold, uncheckedColor = c.textSecondary, checkmarkColor = c.background),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = Atlas.type.itemTitle, color = if (h.done) c.textSecondary else c.text)
                        Text(h.detail, style = Atlas.type.supporting, color = if (h.overdue) c.accentText else c.textSecondary)
                    }
                }
                if (i != s.homework.lastIndex) GroupDivider(inset = 56.dp)
            }
        }

        GroupHeader("Fitness")
        Group {
            when {
                s.restDay -> EmptyState("Rest day. Nothing is expected.")
                s.fitness.isEmpty() -> EmptyState("Every target met today.")
            }
            s.fitness.forEachIndexed { i, g ->
                val unit = if (g.unit == ExerciseUnit.SECONDS) " s" else ""
                ItemRow(
                    title = g.name,
                    supporting = "${g.done} of ${g.target}$unit · ${g.remaining}$unit short",
                    trailing = { CountButton("+${g.remaining}", { vm.logRest(g) }, description = "Log the remaining ${g.remaining} ${g.name}") },
                )
                if (i != s.fitness.lastIndex) GroupDivider()
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

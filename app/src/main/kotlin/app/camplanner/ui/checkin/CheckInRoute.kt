package app.camplanner.ui.checkin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.domain.CheckIn
import app.camplanner.ui.common.appViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class CheckInViewModel(private val graph: DataGraph, private val date: LocalDate) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    /** What the user has typed, so the field never lags behind the database. */
    private val typed = MutableStateFlow<Map<Long, String>>(emptyMap())
    val closed = MutableStateFlow(false)

    private val sources = combine(
        graph.timetable.observeDays(date, date, zone),
        graph.timetable.observeSubjects(),
        graph.homework.observeAll(),
        graph.fitness.observeExercises(),
        graph.fitness.observeSets(),
    ) { events, subjects, homework, exercises, sets -> CheckInSources(events, subjects, homework, exercises, sets) }

    val state: StateFlow<CheckInUiState?> = combine(
        sources,
        graph.reading.observeDay(date),
        graph.settings.settings,
        graph.checkIns.observeCompleted(date),
        typed,
    ) { src, reading, settings, completed, typedPages ->
        val plan = CheckIn.plan(date, zone, src.subjects, src.events, src.homework, src.exercises, src.sets, settings.restDays)
        val pages = reading.associate { it.subjectId to it.pages }
        val subjectsById = src.subjects.associateBy { it.id }
        CheckInUiState(
            date = date,
            readingMin = settings.readingMinPages,
            readingMax = settings.readingMaxPages,
            reading = plan.readingSubjects.map { s ->
                ReadingEntry(s.id, s.name, s.colorIndex, typedPages[s.id] ?: pages[s.id]?.toString().orEmpty())
            },
            homework = plan.homework.map { a ->
                val subject = a.subjectId?.let(subjectsById::get)
                HomeworkEntry(a.id, a.title, subject?.name, subject?.colorIndex, a.deadline.atZone(zone).toLocalDate(), a.done)
            },
            fitness = plan.fitnessShortfall.map { FitnessGap(it.exercise.id, it.exercise.name, it.exercise.unit, it.done, it.target) },
            isRestDay = plan.isRestDay,
            saved = completed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPages(subjectId: Long, text: String) {
        typed.update { it + (subjectId to text) }
        viewModelScope.launch { graph.reading.setPages(date, subjectId, text.toIntOrNull()) }
    }

    fun setDone(id: Long, done: Boolean) = viewModelScope.launch { graph.homework.setDone(id, done) }

    fun logRemaining(exerciseId: Long) = viewModelScope.launch {
        val gap = state.value?.fitness?.firstOrNull { it.exerciseId == exerciseId } ?: return@launch
        graph.fitness.log(exerciseId, gap.remaining, date)
    }

    fun close() = viewModelScope.launch {
        graph.checkIns.complete(date)
        delay(400) // let "Saved" register before leaving
        closed.value = true
    }
}

private data class CheckInSources(
    val events: List<app.camplanner.model.CalendarEvent>,
    val subjects: List<app.camplanner.model.Subject>,
    val homework: List<app.camplanner.model.Assignment>,
    val exercises: List<app.camplanner.model.Exercise>,
    val sets: List<app.camplanner.model.ExerciseSet>,
)

@Composable
fun CheckInRoute(onDone: () -> Unit) {
    val today = LocalDate.now()
    val vm = appViewModel(key = "checkin-$today") { graph, _ -> CheckInViewModel(graph, today) }
    val state by vm.state.collectAsStateWithLifecycle()
    val closed by vm.closed.collectAsStateWithLifecycle()
    LaunchedEffect(closed) { if (closed) onDone() }
    state?.let {
        CheckInScreen(
            state = it,
            onPagesChange = vm::setPages,
            onHomeworkDone = { id, done -> vm.setDone(id, done) },
            onLogRemaining = { id -> vm.logRemaining(id) },
            onSave = { vm.close() },
        )
    }
}

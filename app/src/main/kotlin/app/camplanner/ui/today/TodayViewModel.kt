package app.camplanner.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.camplanner.alarms.AlarmPermissions
import app.camplanner.data.DataGraph
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.minuteTicker
import app.camplanner.ui.common.openUrl
import app.camplanner.ui.common.updateSettings
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
import java.time.ZoneId

class TodayViewModel(private val graph: DataGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val permissions = MutableStateFlow(true to null as String?)

    /** Everything that is not the timetable, gathered once so the main combine stays readable. */
    private data class Side(
        val assignments: List<app.camplanner.model.Assignment>,
        val exercises: List<app.camplanner.model.Exercise>,
        val sets: List<app.camplanner.model.ExerciseSet>,
        val hasTimetable: Boolean,
        val setupHidden: Boolean,
    )

    private val side = combine(
        graph.homework.observeAll(),
        graph.fitness.observeExercises(),
        graph.fitness.observeSets(),
        graph.timetable.observeImportedCount().map { it > 0 },
        graph.settings.setupHidden,
    ) { a, e, s, t, h -> Side(a, e, s, t, h) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayState?> = minuteTicker()
        .flatMapLatest { now ->
            val today = now.toLocalDate()
            val days = combine(
                graph.timetable.observeDays(today, today.plusDays(1), zone),
                graph.timetable.observeSubjects(),
                graph.settings.settings,
                graph.locations.observeAll(),
            ) { events, subjects, settings, _ -> Triple(events, subjects, settings) }
            combine(days, side, graph.checkIns.observeCompleted(today), permissions) { (events, subjects, settings), s, reviewed, (ok, attention) ->
                val built = TodayBuilder.build(
                    now = now, zone = zone, events = events, subjects = subjects, settings = settings,
                    locate = graph.locations.locator(), assignments = s.assignments, exercises = s.exercises, sets = s.sets,
                    reviewDone = reviewed, hasTimetable = s.hasTimetable, permissionsOk = ok, attention = attention,
                )
                if (s.setupHidden) built.copy(setup = emptyList()) else built
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshPermissions(context: Context) {
        val s = AlarmPermissions.status(context)
        permissions.value = s.essentialsGranted to when {
            !s.notifications -> "Notifications are off, so alarms and reminders can't reach you."
            !s.exactAlarms -> "Exact alarms are off, so the morning alarm may ring late."
            !s.fullScreen -> "Full-screen alarms are off, so the alarm only shows as a notification."
            else -> null
        }
    }

    /** Skips tomorrow's morning alarm, or brings it back. */
    fun toggleAlarm() = viewModelScope.launch {
        val tomorrow = state.value?.date?.plusDays(1) ?: return@launch
        val skipped = state.value?.alarmSkipped == true
        graph.updateSettings { s -> s.copy(skippedMornings = if (skipped) s.skippedMornings - tomorrow else s.skippedMornings + tomorrow) }
        if (!skipped) {
            Notices.show("No alarm tomorrow morning") {
                graph.updateSettings { s -> s.copy(skippedMornings = s.skippedMornings - tomorrow) }
            }
        } else {
            Notices.show("Alarm back on for tomorrow")
        }
    }

    fun setDone(id: Long, done: Boolean) = viewModelScope.launch {
        graph.homework.setDone(id, done)
        if (done) Notices.show("Marked as done") { graph.homework.setDone(id, false) }
    }

    fun hideSetup() = viewModelScope.launch {
        graph.settings.setSetupHidden(true)
        Notices.show("Checklist hidden") { graph.settings.setSetupHidden(false) }
    }

    fun route(context: Context, eventId: Long) = viewModelScope.launch {
        graph.routes.directionsUrl(eventId)?.let(context::openUrl)
    }
}

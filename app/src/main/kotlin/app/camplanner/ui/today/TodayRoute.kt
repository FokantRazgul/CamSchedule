package app.camplanner.ui.today

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.alarms.AlarmPermissions
import app.camplanner.data.DataGraph
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.common.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class TodayViewModel(private val graph: DataGraph, private val fixedDate: LocalDate?) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val attention = MutableStateFlow<String?>(null)
    private val locationsVersion = graph.locations.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState?> = minuteTicker()
        .map { now -> now to (fixedDate ?: now.toLocalDate()) }
        .flatMapLatest { (now, date) ->
            combine(
                graph.timetable.observeDays(date, date, zone),
                graph.timetable.observeSubjects(),
                graph.settings.settings,
                locationsVersion,
                attention,
            ) { events, subjects, settings, _, attn ->
                val locate = graph.locations.locator()
                TodayStateBuilder.build(date, now, events, subjects, settings, locate, zone, attn)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshPermissions(context: Context) {
        val s = AlarmPermissions.status(context)
        attention.value = when {
            !s.notifications -> "Notifications are off: alarms can't reach you"
            !s.exactAlarms -> "Exact alarms are off: the morning alarm may be late"
            !s.fullScreen -> "Full-screen alarms are off"
            else -> null
        }
    }

    fun openRoute(context: Context, eventId: Long) {
        viewModelScope.launch {
            val url = graph.routes.directionsUrl(eventId) ?: return@launch
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
fun TodayRoute(
    date: LocalDate?,
    onOpenEvent: (Long) -> Unit,
    onOpenCheckIn: () -> Unit,
    onFixPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val vm = appViewModel(key = "today-${date ?: "now"}") { graph, _ -> TodayViewModel(graph, date) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let {
        TodayScreen(
            state = it,
            onRoute = { id -> vm.openRoute(context, id) },
            onOpenEvent = onOpenEvent,
            onOpenCheckIn = onOpenCheckIn,
            onFixPermissions = onFixPermissions,
        )
    }
}

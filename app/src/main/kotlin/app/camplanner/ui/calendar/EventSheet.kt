package app.camplanner.ui.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.db.LocationStatus
import app.camplanner.data.repo.Scope
import app.camplanner.designsystem.kit.AppChip
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.kit.SheetBody
import app.camplanner.designsystem.kit.SubjectBar
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.Leg
import app.camplanner.domain.Recurrence
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSeries
import app.camplanner.model.EventSource
import app.camplanner.model.Subject
import app.camplanner.model.TravelMode
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class EventDetails(
    val event: CalendarEvent,
    val series: EventSeries?,
    val subject: Subject?,
    val leg: Leg?,
    /** true found, false not found, null still looking it up or no place. */
    val placeFound: Boolean?,
    val defaultMode: TravelMode,
    val defaultReminder: Int?,
    /** An imported lecture the user has changed (time, place, travel, reminder or cancelled). */
    val changed: Boolean = false,
)

class EventSheetViewModel(private val graph: DataGraph, private val id: Long) : ViewModel() {
    val details: StateFlow<EventDetails?> = combine(
        graph.timetable.observeEvent(id),
        graph.timetable.observeSubjects(),
        graph.locations.observeAll(),
        graph.settings.settings,
    ) { event, subjects, places, settings ->
        event ?: return@combine null
        val place = event.location?.let { raw -> places.firstOrNull { it.key == DayPlanner.normalizeLocation(raw) } }
        EventDetails(
            event = event,
            series = event.seriesId?.let { graph.timetable.series(it) },
            subject = subjects.firstOrNull { it.id == event.subjectId },
            leg = if (event.cancelled) null else graph.routes.legFor(event.id),
            placeFound = when (place?.status) {
                LocationStatus.GEOCODED.name, LocationStatus.MANUAL.name -> true
                LocationStatus.NOT_FOUND.name -> false
                else -> null
            },
            defaultMode = settings.defaultTravelMode,
            defaultReminder = settings.reminderLeadMinutes.takeIf { settings.reminderEnabled },
            changed = event.source == EventSource.IMPORTED && graph.db.events().override(event.key)?.changesAnything == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMode(key: String, mode: TravelMode?) = viewModelScope.launch { graph.timetable.setTravelMode(key, mode) }

    fun delete(scope: Scope, onDone: () -> Unit) = viewModelScope.launch {
        val e = details.value?.event ?: return@launch
        val undo = graph.timetable.delete(e.id, scope) ?: return@launch
        val text = when {
            e.source == EventSource.IMPORTED -> "Cancelled ${e.title}"
            scope == Scope.ALL && e.seriesId != null -> "Deleted every ${e.title}"
            else -> "Deleted ${e.title}"
        }
        val timetable = graph.timetable
        Notices.show(text) { timetable.undo(undo) }
        onDone()
    }

    fun restore() = viewModelScope.launch {
        val e = details.value?.event ?: return@launch
        graph.timetable.setCancelled(e.key, false)
    }

    fun reset() = viewModelScope.launch {
        val e = details.value?.event ?: return@launch
        graph.timetable.resetOverride(e.key)
        Notices.show("Back to the timetable's version")
    }

    fun route(context: Context) = viewModelScope.launch {
        val url = graph.routes.directionsUrl(id) ?: return@launch
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Everything about one event, as a bottom sheet: when and where, how to get there, and what
 * you can do with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    eventId: Long,
    onDismiss: () -> Unit,
    onEdit: (id: Long, scope: Scope) -> Unit,
    onOpenPlaces: () -> Unit,
) {
    val context = LocalContext.current
    val vm = appViewModel(key = "event-sheet-$eventId") { graph, _ -> EventSheetViewModel(graph, eventId) }
    val details by vm.details.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Atlas.colors.surfaceHigh, dragHandle = null) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            details?.let { d ->
                EventSheetContent(
                    d,
                    onRoute = { vm.route(context) },
                    onMode = { vm.setMode(d.event.key, it) },
                    onEdit = { scope -> onDismiss(); onEdit(d.event.id, scope) },
                    onDelete = { scope -> vm.delete(scope, onDismiss) },
                    onRestore = vm::restore,
                    onReset = vm::reset,
                    onOpenPlaces = { onDismiss(); onOpenPlaces() },
                )
            }
        }
    }
}

@Composable
fun EventSheetContent(
    d: EventDetails,
    onRoute: () -> Unit,
    onMode: (TravelMode?) -> Unit,
    onEdit: (Scope) -> Unit,
    onDelete: (Scope) -> Unit,
    onRestore: () -> Unit,
    onReset: () -> Unit,
    onOpenPlaces: () -> Unit,
) {
    val c = Atlas.colors
    val e = d.event
    val zone = ZoneId.systemDefault()
    val date = e.start.atZone(zone).toLocalDate()
    val imported = e.source == EventSource.IMPORTED
    var askScope by remember { mutableStateOf<String?>(null) } // "edit" or "delete"

    SheetBody(e.title) {
        Text(
            "${Formats.weekday(date)} ${Formats.dayMonth(date)} · ${Formats.time(e.start.atZone(zone).toLocalTime())}–${Formats.time(e.end.atZone(zone).toLocalTime())}",
            style = Atlas.type.reading, color = c.text,
        )
        if (e.cancelled) Text("Cancelled", style = Atlas.type.itemTitle, color = c.accentText)
        d.series?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Repeat, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Recurrence.describe(it.rule, it.startDate), style = Atlas.type.supporting, color = c.textSecondary)
            }
        }
        if (imported) Text("From your timetable", style = Atlas.type.supporting, color = c.textSecondary)
        d.subject?.let { s ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectBar(c.subject(s.colorIndex), height = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(s.name, style = Atlas.type.supporting, color = c.textSecondary)
            }
        }

        if (e.location != null && !e.cancelled) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Place, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.location!!, style = Atlas.type.itemTitle, color = c.text)
                    val leg = d.leg
                    val travel = leg?.travel?.toMinutes()
                    Text(
                        when {
                            d.placeFound == false -> "Not found on the map, so no leave alert"
                            travel == 0L -> "Same place as the event before"
                            travel != null && leg.leaveAt != null ->
                                "Leave at ${Formats.time(leg.leaveAt!!.atZone(zone).toLocalTime())} · ${Formats.mode(leg.mode)} $travel min"
                            d.placeFound == null -> "Looking it up on the map…"
                            else -> "Set your home address to get a leave time"
                        },
                        style = Atlas.type.supporting,
                        color = if (d.placeFound == false) c.accentText else c.textSecondary,
                    )
                    if (d.placeFound == false) TertiaryButton("Fix the place", onOpenPlaces)
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Route in Maps", onRoute, Modifier.fillMaxWidth(), icon = Icons.Outlined.Directions)
            Spacer(Modifier.height(14.dp))
            Text("Getting there", style = Atlas.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("Usual (${Formats.mode(d.defaultMode)})", e.travelModeOverride == null, { onMode(null) })
                AppChip("Walk", e.travelModeOverride == TravelMode.WALK, { onMode(TravelMode.WALK) })
                AppChip("Bike", e.travelModeOverride == TravelMode.BIKE, { onMode(TravelMode.BIKE) })
            }
        }

        if (!e.cancelled) {
            Spacer(Modifier.height(12.dp))
            Text(
                when (val m = e.reminderMinutes ?: d.defaultReminder) {
                    null, 0 -> "No reminder"
                    else -> "Reminder ${Formats.duration(m.toLong())} before"
                },
                style = Atlas.type.supporting, color = c.textSecondary,
            )
        }
        e.note?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = Atlas.type.reading, color = c.text)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!e.cancelled) {
                SecondaryButton("Edit", {
                    if (d.series != null) askScope = "edit" else onEdit(Scope.THIS)
                }, Modifier.weight(1f), icon = Icons.Outlined.Edit)
            }
            if (imported && e.cancelled) {
                PrimaryButton("Restore lecture", onRestore, Modifier.weight(1f))
            } else {
                SecondaryButton(if (imported) "Cancel lecture" else "Delete", {
                    if (d.series != null) askScope = "delete" else onDelete(Scope.THIS)
                }, Modifier.weight(1f), icon = Icons.Outlined.EventBusy)
            }
        }
        if (imported && d.changed) TertiaryButton("Back to the timetable's version", onReset, color = c.textSecondary)
    }

    askScope?.let { what ->
        ScopeDialog(
            title = if (what == "edit") "Edit which events?" else "Delete which events?",
            onPick = { scope -> askScope = null; if (what == "edit") onEdit(scope) else onDelete(scope) },
            onDismiss = { askScope = null },
        )
    }
}

@Composable
fun ScopeDialog(title: String, onPick: (Scope) -> Unit, onDismiss: () -> Unit) {
    val c = Atlas.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(title, style = Atlas.type.groupTitle, color = c.text) },
        text = {
            Column {
                TertiaryButton("Only this one", { onPick(Scope.THIS) }, Modifier.fillMaxWidth())
                TertiaryButton("All in the series", { onPick(Scope.ALL) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { TertiaryButton("Cancel", onDismiss, color = c.textSecondary) },
    )
}

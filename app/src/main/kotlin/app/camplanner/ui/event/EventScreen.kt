package app.camplanner.ui.event

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.db.LocationStatus
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.ChoiceRow
import app.camplanner.designsystem.components.Glyphs
import app.camplanner.designsystem.components.LedgerField
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.domain.DayPlanner
import app.camplanner.domain.Leg
import app.camplanner.model.CalendarEvent
import app.camplanner.model.EventSource
import app.camplanner.model.Subject
import app.camplanner.model.TravelMode
import app.camplanner.ui.common.DateField
import app.camplanner.ui.common.TimeField
import app.camplanner.ui.common.appViewModel
import app.camplanner.ui.format.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class EventForm(
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime = LocalTime.of(10, 0),
    val location: String = "",
    val note: String = "",
) {
    val valid: Boolean get() = title.isNotBlank() && end.isAfter(start)
}

data class EventDetail(
    val event: CalendarEvent,
    val subject: Subject?,
    val leg: Leg?,
    val located: Boolean?,
    val defaultMode: TravelMode,
)

class EventViewModel(private val graph: DataGraph, private val id: Long, newDate: LocalDate?) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    val editing = MutableStateFlow(id == 0L)
    val form = MutableStateFlow(EventForm(date = newDate ?: LocalDate.now()))
    val finished = MutableStateFlow(false)

    val detail: StateFlow<EventDetail?> = combine(
        graph.timetable.observeEvent(id),
        graph.timetable.observeSubjects(),
        graph.locations.observeAll(),
        graph.settings.settings,
    ) { event, subjects, locations, settings ->
        event ?: return@combine null
        val loc = event.location?.let { raw -> locations.firstOrNull { it.key == DayPlanner.normalizeLocation(raw) } }
        EventDetail(
            event = event,
            subject = subjects.firstOrNull { it.id == event.subjectId },
            leg = graph.routes.legFor(event.id),
            located = when (loc?.status) {
                LocationStatus.GEOCODED.name, LocationStatus.MANUAL.name -> true
                LocationStatus.NOT_FOUND.name -> false
                else -> null // still being looked up
            },
            defaultMode = settings.defaultTravelMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startEditing(e: CalendarEvent) {
        form.value = EventForm(
            title = e.title,
            date = e.start.atZone(zone).toLocalDate(),
            start = e.start.atZone(zone).toLocalTime(),
            end = e.end.atZone(zone).toLocalTime(),
            location = e.location.orEmpty(),
            note = e.note.orEmpty(),
        )
        editing.value = true
    }

    fun edit(change: (EventForm) -> EventForm) = form.update(change)

    fun save() {
        val f = form.value
        if (!f.valid) return
        viewModelScope.launch {
            graph.timetable.saveManual(
                id = id.takeIf { it != 0L },
                title = f.title,
                start = f.date.atTime(f.start).atZone(zone).toInstant(),
                end = f.date.atTime(f.end).atZone(zone).toInstant(),
                location = f.location,
                note = f.note,
            )
            if (id == 0L) finished.value = true else editing.value = false
        }
    }

    fun delete() = viewModelScope.launch {
        graph.timetable.deleteManual(id)
        finished.value = true
    }

    fun setMode(key: String, mode: TravelMode?) = viewModelScope.launch { graph.timetable.setTravelMode(key, mode) }

    fun openRoute(context: Context) = viewModelScope.launch {
        val url = graph.routes.directionsUrl(id) ?: return@launch
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun EventRoute(id: Long, newDate: LocalDate?, onBack: () -> Unit, onOpenLocations: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel(key = "event-$id-$newDate") { graph, _ -> EventViewModel(graph, id, newDate) }
    val editing by vm.editing.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onBack() }

    if (editing) {
        EventEditScreen(
            isNew = id == 0L, form = form, onChange = vm::edit, onSave = vm::save,
            onCancel = { if (id == 0L) onBack() else vm.editing.value = false },
        )
    } else {
        detail?.let { d ->
            EventDetailScreen(
                detail = d,
                onBack = onBack,
                onMode = { vm.setMode(d.event.key, it) },
                onRoute = { vm.openRoute(context) },
                onEdit = { vm.startEditing(d.event) },
                onDelete = { vm.delete() },
                onOpenLocations = onOpenLocations,
            )
        }
    }
}

@Composable
fun EventDetailScreen(
    detail: EventDetail,
    onBack: () -> Unit,
    onMode: (TravelMode?) -> Unit,
    onRoute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLocations: () -> Unit,
) {
    val c = Atlas.colors
    val e = detail.event
    val zone = ZoneId.systemDefault()
    val date = e.start.atZone(zone).toLocalDate()
    val manual = e.source == EventSource.MANUAL
    AtlasPage(
        mastheadLeft = if (manual) "Added by hand" else "From the timetable",
        mastheadRight = Formats.shortDate(date),
        dateline = "${Formats.weekday(date)}, ${Formats.dayMonth(date)}",
        title = e.title,
        onBack = onBack,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${Formats.time(e.start.atZone(zone).toLocalTime())} – ${Formats.time(e.end.atZone(zone).toLocalTime())}",
                style = Atlas.type.numeral, color = c.text,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "   ${Formats.duration(Duration.between(e.start, e.end).toMinutes())}",
                style = Atlas.type.aside, color = c.textSecondary,
            )
        }
        detail.subject?.let { s ->
            Spacer(Modifier.height(Atlas.space.s))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubjectMark(c.subject(s.colorIndex))
                Text(s.name, style = Atlas.type.body, color = c.textSecondary)
            }
        }
        if (e.note != null) {
            Spacer(Modifier.height(Atlas.space.m))
            Text(e.note!!, style = Atlas.type.aside, color = c.textSecondary)
        }

        Spacer(Modifier.height(Atlas.space.xl))
        SectionTitle("I", "Whereabouts")
        Spacer(Modifier.height(Atlas.space.m))
        if (e.location == null) {
            Text("No place given.", style = Atlas.type.aside, color = c.textSecondary)
        } else {
            Text(e.location!!, style = Atlas.type.title, color = c.text)
            Text(
                when (detail.located) {
                    true -> "Located on the map"
                    false -> "Not found on the map, so no leave alert"
                    null -> "Looking it up"
                },
                style = Atlas.type.bodySmall,
                color = if (detail.located == false) c.accentText else c.textSecondary,
            )
            if (detail.located == false) AtlasButton("Correct it in Locations", onOpenLocations, style = ActionStyle.Text, glyph = Glyphs.ChevronRight)
        }

        if (e.location != null) {
            Spacer(Modifier.height(Atlas.space.xl))
            SectionTitle("II", "The Way There")
            Spacer(Modifier.height(Atlas.space.m))
            ChoiceRow(
                listOf(null to "Default (${Formats.mode(detail.defaultMode)})", TravelMode.WALK to "Walk", TravelMode.BIKE to "Bike"),
                selected = e.travelModeOverride,
                onSelect = onMode,
            )
            Spacer(Modifier.height(Atlas.space.m))
            val leg = detail.leg
            val travel = leg?.travel?.toMinutes()
            Text(
                when {
                    leg == null -> ""
                    travel == 0L -> "Same place as the engagement before; no need to move."
                    travel != null -> "${Formats.mode(leg.mode).replaceFirstChar { it.uppercase() }} $travel min, leave at ${Formats.time(leg.leaveAt!!.atZone(zone).toLocalTime())}"
                    else -> "Travel time unknown until both places are on the map."
                },
                style = Atlas.type.time, color = c.textSecondary,
            )
            Spacer(Modifier.height(Atlas.space.l))
            AtlasButton("Open the route in Maps", onRoute, style = ActionStyle.Filled, glyph = Glyphs.Route, modifier = Modifier.fillMaxWidth())
        }

        if (manual) {
            Spacer(Modifier.height(Atlas.space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Atlas.space.s)) {
                AtlasButton("Edit", onEdit)
                AtlasButton("Delete", onDelete, style = ActionStyle.Text)
            }
        }
    }
}

@Composable
fun EventEditScreen(
    isNew: Boolean,
    form: EventForm,
    onChange: ((EventForm) -> EventForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AtlasPage(
        mastheadLeft = "Added by hand",
        mastheadRight = null,
        title = if (isNew) "A New Engagement" else "Amend the Engagement",
        onBack = onCancel,
    ) {
        LedgerField(form.title, { v -> onChange { it.copy(title = v) } }, label = "Title", placeholder = "Supervision, society, appointment")
        Spacer(Modifier.height(Atlas.space.xl))
        DateField("Date", form.date, { d -> onChange { it.copy(date = d) } }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Atlas.space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Atlas.space.xl)) {
            TimeField("Begins", form.start, { t ->
                onChange { f -> f.copy(start = t, end = if (f.end.isAfter(t)) f.end else t.plusHours(1)) }
            }, Modifier.weight(1f))
            TimeField("Ends", form.end, { t -> onChange { it.copy(end = t) } }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Atlas.space.xl))
        LedgerField(form.location, { v -> onChange { it.copy(location = v) } }, label = "Place", placeholder = "e.g. Sidgwick Site, Room 2")
        Spacer(Modifier.height(Atlas.space.xl))
        LedgerField(form.note, { v -> onChange { it.copy(note = v) } }, label = "Note", singleLine = false, placeholder = "Optional")
        Spacer(Modifier.height(Atlas.space.xxl))
        if (!form.end.isAfter(form.start)) {
            Text("It must end after it begins.", style = Atlas.type.bodySmall, color = Atlas.colors.accentText)
            Spacer(Modifier.height(Atlas.space.s))
        }
        Column {
            AtlasButton("Save", onSave, style = ActionStyle.Filled, enabled = form.valid, modifier = Modifier.fillMaxWidth())
        }
    }
}

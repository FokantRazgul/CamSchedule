package app.camplanner.ui.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.GeocodeWorker
import app.camplanner.data.db.LocationEntity
import app.camplanner.data.db.LocationStatus
import app.camplanner.data.geo.GeoResolver
import app.camplanner.data.geo.GeoResult
import app.camplanner.designsystem.kit.AppTextField
import app.camplanner.designsystem.kit.EmptyState
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.HeaderAction
import app.camplanner.designsystem.kit.ItemRow
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.kit.SheetBody
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.ui.common.AppSheet
import app.camplanner.ui.common.Notices
import app.camplanner.ui.common.SubPage
import app.camplanner.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class PlacesViewModel(private val graph: DataGraph) : ViewModel() {
    val places: StateFlow<List<LocationEntity>?> = graph.locations.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun relookup(key: String, query: String): GeoResult = graph.locations.relookup(key, query)

    fun setManual(key: String, text: String): Boolean {
        val point = GeoResolver.parseCoordinates(text) ?: return false
        viewModelScope.launch {
            graph.locations.setManual(key, point)
            Notices.show("Place saved")
        }
        return true
    }
}

@Composable
fun PlacesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { graph, _ -> PlacesViewModel(graph) }
    val places by vm.places.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<LocationEntity?>(null) }
    val c = Atlas.colors
    SubPage(
        "Places", onBack,
        actions = {
            if (places.orEmpty().any { it.status == LocationStatus.PENDING.name }) {
                HeaderAction(Icons.Outlined.Refresh, "Look up waiting places now", {
                    GeocodeWorker.enqueue(context)
                    Notices.show("Looking them up")
                })
            }
        },
    ) {
        val list = places ?: return@SubPage
        Text(
            "Every place in your timetable, as found on the map. Leave times use these. Tap one to correct it.",
            style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        val sorted = list.sortedWith(compareBy({ it.status != LocationStatus.NOT_FOUND.name }, { it.rawText.lowercase() }))
        Group {
            if (sorted.isEmpty()) EmptyState("No places yet. They appear when events have a place.")
            sorted.forEachIndexed { i, p ->
                val (line, warn) = describe(p)
                ItemRow(title = p.rawText, supporting = line, supportingColor = if (warn) c.accentText else c.textSecondary, onClick = { editing = p })
                if (i != sorted.lastIndex) GroupDivider()
            }
        }
    }
    editing?.let { p -> PlaceSheet(p, vm, onMap = { showOnMap(context, p) }, onDismiss = { editing = null }) }
}

private fun coords(p: LocationEntity) = String.format(Locale.ROOT, "%.5f, %.5f", p.lat, p.lng)

private fun describe(p: LocationEntity): Pair<String, Boolean> = when (LocationStatus.entries.firstOrNull { it.name == p.status }) {
    LocationStatus.GEOCODED -> (p.label ?: "Found on the map") to false
    LocationStatus.MANUAL -> "Set by you · ${coords(p)}" to false
    LocationStatus.NOT_FOUND -> "Not found. No leave alerts until it is fixed." to true
    else -> "Waiting to be looked up (needs the internet)" to false
}

private fun showOnMap(context: Context, p: LocationEntity) {
    val lat = p.lat ?: return
    val lng = p.lng ?: return
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(p.rawText)})")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun PlaceSheet(p: LocationEntity, vm: PlacesViewModel, onMap: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember(p.key) { mutableStateOf(p.rawText) }
    var coordinates by remember(p.key) { mutableStateOf(if (p.lat != null) coords(p) else "") }
    var message by remember(p.key) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val c = Atlas.colors
    AppSheet(onDismiss) {
        SheetBody(p.rawText) {
            Text(describe(p).first, style = Atlas.type.supporting, color = c.textSecondary)
            Spacer(Modifier.height(16.dp))
            AppTextField(query, { query = it }, "Search as", placeholder = "Street, college or building")
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (busy) "Looking up…" else "Look up", {
                busy = true
                scope.launch {
                    when (val r = vm.relookup(p.key, query)) {
                        is GeoResult.Found -> {
                            Notices.show("Found: ${r.hit.label ?: query}")
                            onDismiss()
                        }
                        GeoResult.NotFound -> message = "Still not found. Try a street or college name, or enter coordinates."
                        GeoResult.Unavailable -> message = "The map service can't be reached. Try again when online."
                    }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && query.isNotBlank())
            Spacer(Modifier.height(20.dp))
            AppTextField(coordinates, { coordinates = it }, "Or coordinates", placeholder = "52.2015, 0.1170")
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Use these coordinates", {
                if (vm.setManual(p.key, coordinates)) onDismiss() else message = "Enter latitude and longitude, like 52.2015, 0.1170"
            }, Modifier.fillMaxWidth())
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = Atlas.type.supporting, color = c.accentText)
            }
            if (p.lat != null) {
                Row { TertiaryButton("Show on map", onMap, icon = Icons.Outlined.Map) }
            }
        }
    }
}

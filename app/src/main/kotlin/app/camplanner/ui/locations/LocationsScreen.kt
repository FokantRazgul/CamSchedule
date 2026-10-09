package app.camplanner.ui.locations

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.camplanner.data.DataGraph
import app.camplanner.data.GeocodeWorker
import app.camplanner.data.db.LocationEntity
import app.camplanner.data.db.LocationStatus
import app.camplanner.data.geo.GeoResolver
import app.camplanner.data.geo.GeoResult
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasDialog
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.LedgerField
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class LocationsViewModel(private val graph: DataGraph) : ViewModel() {
    val locations: StateFlow<List<LocationEntity>?> = graph.locations.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun relookup(key: String, query: String): GeoResult = graph.locations.relookup(key, query)

    fun setManual(key: String, text: String): Boolean {
        val point = GeoResolver.parseCoordinates(text) ?: return false
        viewModelScope.launch { graph.locations.setManual(key, point) }
        return true
    }
}

@Composable
fun LocationsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { graph, _ -> LocationsViewModel(graph) }
    val locations by vm.locations.collectAsStateWithLifecycle()
    locations?.let { list ->
        LocationsScreen(
            list, onBack,
            onRetryAll = { GeocodeWorker.enqueue(context) },
            onRelookup = vm::relookup,
            onSetManual = vm::setManual,
            onShowOnMap = { showOnMap(context, it) },
        )
    }
}

private fun showOnMap(context: Context, loc: LocationEntity) {
    val lat = loc.lat ?: return
    val lng = loc.lng ?: return
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(loc.rawText)})")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun coords(loc: LocationEntity) = String.format(Locale.ROOT, "%.5f, %.5f", loc.lat, loc.lng)

@Composable
fun LocationsScreen(
    locations: List<LocationEntity>,
    onBack: () -> Unit,
    onRetryAll: () -> Unit,
    onRelookup: suspend (String, String) -> GeoResult,
    onSetManual: (String, String) -> Boolean,
    onShowOnMap: (LocationEntity) -> Unit,
) {
    val c = Atlas.colors
    var editing by remember { mutableStateOf<LocationEntity?>(null) }
    val unresolved = locations.count { it.lat == null }
    AtlasPage(
        mastheadLeft = "Locations",
        mastheadRight = if (unresolved > 0) "$unresolved unplaced" else "all placed",
        title = "The Gazetteer",
        aside = "Each place in your timetable, as found on the map.",
        onBack = onBack,
    ) {
        if (locations.isEmpty()) {
            Text("No places yet. They appear when engagements have a location.", style = Atlas.type.aside, color = c.textSecondary)
        }
        if (locations.any { it.status == LocationStatus.PENDING.name }) {
            AtlasButton("Look up pending places now", onRetryAll, style = ActionStyle.Outlined)
            Spacer(Modifier.height(Atlas.space.l))
        }
        locations.forEachIndexed { i, loc ->
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { editing = loc }.padding(vertical = Atlas.space.l),
            ) {
                Text(loc.rawText, style = Atlas.type.title, color = c.text)
                val (line, warn) = when (LocationStatus.entries.firstOrNull { it.name == loc.status }) {
                    LocationStatus.GEOCODED -> "${coords(loc)}  ·  ${loc.label ?: "found"}" to false
                    LocationStatus.MANUAL -> "${coords(loc)}  ·  set by you" to false
                    LocationStatus.NOT_FOUND -> "Not found. Tap to search differently or enter coordinates." to true
                    else -> "Waiting to be looked up" to false
                }
                Text(line, style = Atlas.type.bodySmall, color = if (warn) c.accentText else c.textSecondary, maxLines = 2)
            }
            if (i != locations.lastIndex) Hairline()
        }
    }

    editing?.let { loc ->
        val scope = rememberCoroutineScope()
        var query by remember(loc.key) { mutableStateOf(loc.rawText) }
        var coordinates by remember(loc.key) { mutableStateOf(if (loc.lat != null) coords(loc) else "") }
        var message by remember(loc.key) { mutableStateOf<String?>(null) }
        AtlasDialog(
            title = loc.rawText,
            onDismiss = { editing = null },
            actions = {
                if (loc.lat != null) AtlasButton("Show on map", { onShowOnMap(loc) }, style = ActionStyle.Text)
                AtlasButton("Close", { editing = null }, style = ActionStyle.Text)
            },
        ) {
            LedgerField(query, { query = it }, label = "Search as")
            Spacer(Modifier.height(Atlas.space.s))
            AtlasButton("Look up", {
                scope.launch {
                    message = when (val r = onRelookup(loc.key, query)) {
                        is GeoResult.Found -> "Found: ${r.hit.label ?: "a match"}".also { editing = null }
                        GeoResult.NotFound -> "Still not found. Try a street or college name, or enter coordinates."
                        GeoResult.Unavailable -> "The map service can't be reached; try again when online."
                    }
                }
            })
            Spacer(Modifier.height(Atlas.space.xl))
            LedgerField(coordinates, { coordinates = it }, label = "Or coordinates", placeholder = "52.2015, 0.1170", keyboardType = KeyboardType.Text)
            Spacer(Modifier.height(Atlas.space.s))
            AtlasButton("Use these coordinates", {
                if (onSetManual(loc.key, coordinates)) editing = null else message = "Enter latitude and longitude, e.g. 52.2015, 0.1170"
            })
            message?.let {
                Spacer(Modifier.height(Atlas.space.m))
                Text(it, style = Atlas.type.bodySmall, color = c.accentText)
            }
        }
    }
}

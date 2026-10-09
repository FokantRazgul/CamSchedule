package app.camplanner.data.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import app.camplanner.domain.RouteLinks
import app.camplanner.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GeoHit(val point: GeoPoint, val label: String?)

sealed interface GeoResult {
    data class Found(val hit: GeoHit) : GeoResult
    data object NotFound : GeoResult

    /** No geocoder on this phone or no network: try again later, don't mark the place as unknown. */
    data object Unavailable : GeoResult
}

/**
 * The platform Geocoder (free, no key). Queries get ", Cambridge, UK" appended and are biased to
 * a box around Cambridge, falling back to an unbounded search.
 */
class GeoResolver(context: Context) {
    private val appContext = context.applicationContext

    suspend fun lookup(text: String): GeoResult {
        if (!Geocoder.isPresent()) return GeoResult.Unavailable
        val query = RouteLinks.withCity(text)
        val geocoder = Geocoder(appContext, Locale.UK)
        return try {
            val hit = search(geocoder, query, bounded = true) ?: search(geocoder, query, bounded = false)
            if (hit != null) GeoResult.Found(hit) else GeoResult.NotFound
        } catch (e: java.io.IOException) {
            GeoResult.Unavailable
        } catch (e: IllegalArgumentException) {
            GeoResult.NotFound
        }
    }

    private suspend fun search(geocoder: Geocoder, query: String, bounded: Boolean): GeoHit? {
        val results: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                    override fun onError(errorMessage: String?) = cont.resume(emptyList())
                }
                if (bounded) {
                    geocoder.getFromLocationName(query, 1, BOX_SOUTH, BOX_WEST, BOX_NORTH, BOX_EAST, listener)
                } else {
                    geocoder.getFromLocationName(query, 1, listener)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                if (bounded) {
                    geocoder.getFromLocationName(query, 1, BOX_SOUTH, BOX_WEST, BOX_NORTH, BOX_EAST)
                } else {
                    geocoder.getFromLocationName(query, 1)
                }.orEmpty()
            }
        }
        val a = results.firstOrNull() ?: return null
        if (!a.hasLatitude() || !a.hasLongitude()) return null
        return GeoHit(GeoPoint(a.latitude, a.longitude), a.getAddressLine(0))
    }

    companion object {
        // Roughly Girton to Fulbourn, Trumpington to Milton.
        private const val BOX_SOUTH = 52.150
        private const val BOX_WEST = 0.030
        private const val BOX_NORTH = 52.260
        private const val BOX_EAST = 0.230

        /** Parses "52.2053, 0.1183" (also with spaces only). */
        fun parseCoordinates(text: String): GeoPoint? {
            val parts = text.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
            if (parts.size != 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lng = parts[1].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            return GeoPoint(lat, lng)
        }
    }
}

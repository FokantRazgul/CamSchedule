package app.camplanner.domain

import app.camplanner.model.GeoPoint
import app.camplanner.model.TravelMode
import java.time.Duration
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline travel-time estimate: straight-line distance × [DETOUR_FACTOR] at a fixed speed per mode.
 * Cambridge is flat and compact, so this is close enough for "when should I leave".
 */
object TravelEstimator {
    const val DETOUR_FACTOR = 1.3
    const val WALK_KMH = 5.0
    const val BIKE_KMH = 15.0
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun speedKmh(mode: TravelMode): Double = when (mode) {
        TravelMode.WALK -> WALK_KMH
        TravelMode.BIKE -> BIKE_KMH
    }

    /** Travel time rounded up to whole minutes. Does not include the buffer. */
    fun travelTime(from: GeoPoint, to: GeoPoint, mode: TravelMode): Duration {
        val routeMeters = distanceMeters(from, to) * DETOUR_FACTOR
        val minutes = routeMeters / 1000.0 / speedKmh(mode) * 60.0
        return Duration.ofMinutes(ceil(minutes - 1e-9).toLong().coerceAtLeast(0))
    }
}

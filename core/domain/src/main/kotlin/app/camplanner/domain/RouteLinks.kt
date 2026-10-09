package app.camplanner.domain

import app.camplanner.model.GeoPoint
import app.camplanner.model.TravelMode
import java.net.URLEncoder
import java.util.Locale

/** A route endpoint: coordinates when we have them, otherwise the free-text address. */
sealed interface Place {
    data class Coordinates(val point: GeoPoint) : Place
    data class Address(val text: String) : Place
}

object RouteLinks {
    const val CITY_SUFFIX = "Cambridge, UK"

    /** Appends ", Cambridge, UK" unless the text already mentions Cambridge. */
    fun withCity(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.contains("cambridge", ignoreCase = true)) trimmed else "$trimmed, $CITY_SUFFIX"
    }

    /**
     * Google Maps directions URL (https://developers.google.com/maps/documentation/urls/get-started#directions-action).
     * A null [origin] lets Maps start from the phone's current position.
     */
    fun directionsUrl(origin: Place?, destination: Place, mode: TravelMode): String = buildString {
        append("https://www.google.com/maps/dir/?api=1")
        if (origin != null) append("&origin=").append(encode(origin))
        append("&destination=").append(encode(destination))
        append("&travelmode=").append(
            when (mode) {
                TravelMode.WALK -> "walking"
                TravelMode.BIKE -> "bicycling"
            },
        )
    }

    private fun encode(place: Place): String {
        val raw = when (place) {
            is Place.Coordinates -> String.format(Locale.ROOT, "%.6f,%.6f", place.point.lat, place.point.lng)
            is Place.Address -> withCity(place.text)
        }
        return URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")
    }
}

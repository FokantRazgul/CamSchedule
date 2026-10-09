package app.camplanner.domain

import app.camplanner.model.GeoPoint
import app.camplanner.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLinksTest {

    @Test
    fun `coordinates to coordinates walking`() {
        val url = RouteLinks.directionsUrl(
            Place.Coordinates(GeoPoint(52.207, 0.117)),
            Place.Coordinates(GeoPoint(52.2015, 0.117)),
            TravelMode.WALK,
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=52.207000%2C0.117000" +
                "&destination=52.201500%2C0.117000&travelmode=walking",
            url,
        )
    }

    @Test
    fun `address gets city appended and is percent-encoded`() {
        val url = RouteLinks.directionsUrl(null, Place.Address("Mill Lane Lecture Rooms & Annex"), TravelMode.BIKE)
        assertEquals(
            "https://www.google.com/maps/dir/?api=1" +
                "&destination=Mill%20Lane%20Lecture%20Rooms%20%26%20Annex%2C%20Cambridge%2C%20UK&travelmode=bicycling",
            url,
        )
    }

    @Test
    fun `city is not appended twice`() {
        assertEquals("Downing Site, Cambridge CB2 3EA", RouteLinks.withCity("Downing Site, Cambridge CB2 3EA"))
        assertEquals("Sidgwick Site, Cambridge, UK", RouteLinks.withCity("  Sidgwick Site "))
    }
}

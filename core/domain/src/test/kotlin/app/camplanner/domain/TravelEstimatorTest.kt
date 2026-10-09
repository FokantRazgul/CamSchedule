package app.camplanner.domain

import app.camplanner.model.GeoPoint
import app.camplanner.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TravelEstimatorTest {

    @Test
    fun `distance of a known north-south offset`() {
        // 0.009 degrees of latitude is about 1000.8 m anywhere on Earth.
        val d = TravelEstimator.distanceMeters(GeoPoint(52.2, 0.12), GeoPoint(52.209, 0.12))
        assertEquals(1000.8, d, 1.0)
    }

    @Test
    fun `distance is symmetric and zero for the same point`() {
        val a = Places.HOME
        val b = Places.CAVENDISH
        assertEquals(TravelEstimator.distanceMeters(a, b), TravelEstimator.distanceMeters(b, a), 1e-6)
        assertEquals(0.0, TravelEstimator.distanceMeters(a, a), 1e-9)
    }

    @Test
    fun `walking applies detour factor and 5 kmh, rounded up`() {
        // 1000.8 m * 1.3 = 1301 m at 5 km/h = 15.6 min -> 16 min
        val t = TravelEstimator.travelTime(GeoPoint(52.2, 0.12), GeoPoint(52.209, 0.12), TravelMode.WALK)
        assertEquals(Duration.ofMinutes(16), t)
    }

    @Test
    fun `cycling uses 15 kmh`() {
        // 1301 m at 15 km/h = 5.2 min -> 6 min
        val t = TravelEstimator.travelTime(GeoPoint(52.2, 0.12), GeoPoint(52.209, 0.12), TravelMode.BIKE)
        assertEquals(Duration.ofMinutes(6), t)
    }

    @Test
    fun `same point needs no travel`() {
        assertEquals(Duration.ZERO, TravelEstimator.travelTime(Places.HOME, Places.HOME, TravelMode.WALK))
    }

    @Test
    fun `cross-town walk is in a plausible range`() {
        // Home (town centre) to the Cavendish in West Cambridge: ~1.9 km straight line.
        val t = TravelEstimator.travelTime(Places.HOME, Places.CAVENDISH, TravelMode.WALK).toMinutes()
        assert(t in 25..35) { "was $t" }
    }
}

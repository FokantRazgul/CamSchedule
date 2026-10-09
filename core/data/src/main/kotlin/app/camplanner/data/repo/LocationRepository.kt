package app.camplanner.data.repo

import app.camplanner.data.db.CamPlannerDatabase
import app.camplanner.data.db.LocationEntity
import app.camplanner.data.db.LocationStatus
import app.camplanner.data.geo.GeoResolver
import app.camplanner.data.geo.GeoResult
import app.camplanner.domain.DayPlanner
import app.camplanner.model.GeoPoint
import kotlinx.coroutines.flow.Flow

/** Locations as stored, plus the lookup the alarm planner uses. */
class LocationRepository(
    private val db: CamPlannerDatabase,
    private val resolver: GeoResolver,
    private val onScheduleChanged: () -> Unit,
) {
    fun observeAll(): Flow<List<LocationEntity>> = db.locations().observeAll()

    /** Raw LOCATION text -> coordinates, for every location that has them. */
    suspend fun locator(): (String) -> GeoPoint? {
        val byKey = db.locations().all()
            .filter { it.lat != null && it.lng != null }
            .associate { it.key to GeoPoint(it.lat!!, it.lng!!) }
        return { raw -> byKey[DayPlanner.normalizeLocation(raw)] }
    }

    /**
     * Geocodes every pending location. Returns false if the geocoder was unavailable for any of
     * them, so the caller can retry later.
     */
    suspend fun resolvePending(): Boolean {
        var complete = true
        var changed = false
        for (loc in db.locations().pending()) {
            when (val r = resolver.lookup(loc.rawText)) {
                is GeoResult.Found -> {
                    db.locations().upsert(
                        loc.copy(
                            lat = r.hit.point.lat, lng = r.hit.point.lng, status = LocationStatus.GEOCODED.name,
                            label = r.hit.label, updatedAtUtc = System.currentTimeMillis(),
                        ),
                    )
                    changed = true
                }
                GeoResult.NotFound -> db.locations().upsert(
                    loc.copy(status = LocationStatus.NOT_FOUND.name, updatedAtUtc = System.currentTimeMillis()),
                )
                GeoResult.Unavailable -> complete = false
            }
        }
        if (changed) onScheduleChanged()
        return complete
    }

    /** Looks [key] up again using [query] instead of the original text. */
    suspend fun relookup(key: String, query: String): GeoResult {
        val loc = db.locations().get(key) ?: return GeoResult.NotFound
        val r = resolver.lookup(query)
        if (r is GeoResult.Found) {
            db.locations().upsert(
                loc.copy(
                    lat = r.hit.point.lat, lng = r.hit.point.lng, status = LocationStatus.MANUAL.name,
                    label = r.hit.label ?: query, updatedAtUtc = System.currentTimeMillis(),
                ),
            )
            onScheduleChanged()
        }
        return r
    }

    suspend fun setManual(key: String, point: GeoPoint) {
        val loc = db.locations().get(key) ?: return
        db.locations().upsert(
            loc.copy(lat = point.lat, lng = point.lng, status = LocationStatus.MANUAL.name, label = null, updatedAtUtc = System.currentTimeMillis()),
        )
        onScheduleChanged()
    }

    suspend fun lookupAddress(text: String): GeoResult = resolver.lookup(text)
}

package app.camplanner.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.camplanner.data.db.CamPlannerDatabase
import app.camplanner.data.geo.GeoResolver
import app.camplanner.data.repo.CheckInRepository
import app.camplanner.data.repo.FitnessRepository
import app.camplanner.data.repo.HomeworkRepository
import app.camplanner.data.repo.LocationRepository
import app.camplanner.data.repo.ReadingRepository
import app.camplanner.data.repo.RouteService
import app.camplanner.data.repo.TimetableRepository
import app.camplanner.data.settings.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Process-wide data objects, shared by the UI, receivers and workers. The app sets
 * [onScheduleChanged] at start-up to trigger an alarm recalculation.
 */
class DataGraph private constructor(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    var onScheduleChanged: () -> Unit = {}

    val db = CamPlannerDatabase.get(appContext)
    val settings = SettingsStore(appContext)
    val geo = GeoResolver(appContext)
    val timetable = TimetableRepository(db, { onScheduleChanged() }, { GeocodeWorker.enqueue(appContext) })
    val locations = LocationRepository(db, geo) { onScheduleChanged() }
    val homework = HomeworkRepository(db)
    val reading = ReadingRepository(db)
    val fitness = FitnessRepository(db)
    val checkIns = CheckInRepository(db)
    val routes = RouteService(timetable, locations, settings)

    companion object {
        @Volatile private var instance: DataGraph? = null

        fun get(context: Context): DataGraph = instance ?: synchronized(this) {
            instance ?: DataGraph(context).also { instance = it }
        }
    }
}

/** Geocodes new LOCATION strings once the phone is online; retries while the geocoder is unreachable. */
class GeocodeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val complete = DataGraph.get(applicationContext).locations.resolvePending()
        return if (complete) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "geocode-locations"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<GeocodeWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

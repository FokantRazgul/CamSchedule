package app.camplanner

import android.app.Application
import app.camplanner.alarms.Notifications
import app.camplanner.alarms.Reschedule
import app.camplanner.data.DataGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CamPlannerApp : Application() {
    val graph: DataGraph by lazy { DataGraph.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Every change that can move an alarm ends up here.
        graph.onScheduleChanged = { Reschedule.soon(this) }
        Notifications.ensureChannels(this)
        Reschedule.ensurePeriodic(this)
        scope.launch {
            graph.fitness.ensureSeeded()
            Reschedule.soon(this@CamPlannerApp)
        }
    }
}

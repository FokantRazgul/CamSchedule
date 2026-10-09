package app.camplanner.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Background recalculation of the alarm plan. */
class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AlarmScheduler(applicationContext).reschedule(fresh = inputData.getBoolean(KEY_FRESH, false))
        return Result.success()
    }

    companion object {
        const val KEY_FRESH = "fresh"
    }
}

object Reschedule {
    private const val SOON = "reschedule-soon"
    private const val PERIODIC = "reschedule-periodic"

    /**
     * Recalculate shortly. Bursts of edits (an import, typing in settings) collapse into one run
     * because each call replaces the pending one.
     */
    fun soon(context: Context) {
        val request = OneTimeWorkRequestBuilder<RescheduleWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
    }

    /** Safety net: keep the 72-hour window rolling even if nothing else happens. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RescheduleWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun fresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<RescheduleWorker>()
            .setInputData(workDataOf(RescheduleWorker.KEY_FRESH to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Reboot, app update, clock or time-zone change, exact-alarm permission change: rebuild everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Registrations do not survive a reboot or an update, so start from a clean slate then.
        val fresh = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        val pending = goAsync()
        scope.launch {
            try {
                Notifications.ensureChannels(context)
                AlarmScheduler(context).reschedule(fresh = fresh)
                Reschedule.ensurePeriodic(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

package app.camplanner.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import app.camplanner.data.DataGraph
import app.camplanner.data.db.ScheduledAlarmEntity
import app.camplanner.domain.AlarmKind
import app.camplanner.domain.AlarmPlanner
import app.camplanner.domain.PlannedAlarm
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Turns the pure alarm plan into AlarmManager registrations. Idempotent: run it after any change
 * to the timetable, settings or locations, after boot, and when the clock or zone changes.
 */
class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val graph = DataGraph.get(appContext)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    /** @param fresh true when nothing is registered any more (after boot or an app update). */
    suspend fun reschedule(fresh: Boolean = false) = lock.withLock {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val settings = graph.settings.current()
        val horizon = AlarmPlanner.DEFAULT_HORIZON
        val events = graph.timetable.between(now - Duration.ofDays(2), now + horizon + Duration.ofDays(1))
        val plan = AlarmPlanner.plan(events, settings, zone, now, horizon, graph.locations.locator())

        val dao = graph.db.scheduledAlarms()
        val registered = if (fresh) {
            dao.clear()
            emptyList()
        } else {
            dao.all().map { Registered(it.key, it.kind, Instant.ofEpochMilli(it.triggerAtUtc)) }
        }
        val changes = AlarmDiff.diff(plan, registered, keep = { it == AlarmContract.SNOOZE_KEY })
        changes.toCancel.forEach { r ->
            cancel(r.key, r.kind)
            dao.delete(r.key)
        }
        changes.toSet.forEach { p ->
            set(p)
            dao.upsert(ScheduledAlarmEntity(p.key, p.kind.name, p.triggerAt.toEpochMilli(), p.eventId))
        }
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingFor(p: PlannedAlarm): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        p.key.hashCode(),
        AlarmContract.alarmIntent(appContext, p.key, p.kind.name, p.eventId, p.date.toEpochDay()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun set(p: PlannedAlarm) {
        val operation = pendingFor(p)
        val at = p.triggerAt.toEpochMilli()
        when {
            p.kind == AlarmKind.MORNING && canScheduleExact() -> alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(at, AlarmContract.openApp(appContext, AlarmContract.ROUTE_TODAY, SHOW_REQUEST)),
                operation,
            )
            canScheduleExact() -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            // Without the exact-alarm permission the system may deliver a few minutes late.
            else -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun cancel(key: String, kind: String) {
        val intent = AlarmContract.alarmIntent(appContext, key, kind, null, 0)
        val pi = PendingIntent.getBroadcast(
            appContext, key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    /** Snooze: ring again in [minutes], as a real alarm clock entry. */
    suspend fun snooze(minutes: Int) {
        val at = Instant.now().plus(Duration.ofMinutes(minutes.toLong()))
        val intent = AlarmContract.alarmIntent(appContext, AlarmContract.SNOOZE_KEY, AlarmContract.KIND_SNOOZE, null, 0)
        val pi = PendingIntent.getBroadcast(
            appContext, AlarmContract.SNOOZE_KEY.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (canScheduleExact()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(at.toEpochMilli(), AlarmContract.openApp(appContext, AlarmContract.ROUTE_TODAY, SHOW_REQUEST)), pi,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
        graph.db.scheduledAlarms().upsert(
            ScheduledAlarmEntity(AlarmContract.SNOOZE_KEY, AlarmContract.KIND_SNOOZE, at.toEpochMilli(), null),
        )
    }

    companion object {
        private val lock = Mutex()
        private const val SHOW_REQUEST = 4_201
    }
}

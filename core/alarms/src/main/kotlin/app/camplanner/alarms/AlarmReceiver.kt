package app.camplanner.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.camplanner.data.DataGraph
import app.camplanner.domain.AlarmKind
import app.camplanner.domain.Fitness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Receives every alarm AlarmManager fires and turns it into a notification or the ringing alarm. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(AlarmContract.EXTRA_KEY) ?: return
        val kind = intent.getStringExtra(AlarmContract.EXTRA_KIND) ?: return
        val eventId = intent.getLongExtra(AlarmContract.EXTRA_EVENT_ID, -1L).takeIf { it >= 0 }
        val date = LocalDate.ofEpochDay(intent.getLongExtra(AlarmContract.EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay()))

        // The ringing alarm must start while we still hold the broadcast's wake lock and FGS exemption.
        if (kind == AlarmKind.MORNING.name || kind == AlarmContract.KIND_SNOOZE) {
            ContextCompat.startForegroundService(context, AlarmRingingService.startIntent(context, eventId))
        }

        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    val graph = DataGraph.get(context)
                    when (kind) {
                        AlarmKind.LEAVE.name -> eventId?.let { leave(context, it) }
                        AlarmKind.REMINDER.name -> eventId?.let { reminder(context, it) }
                        AlarmKind.CHECK_IN.name -> checkIn(context, date)
                        AlarmKind.MIDDAY_NUDGE.name -> nudge(context, date)
                    }
                    graph.db.scheduledAlarms().delete(key)
                    // Each firing rolls the 72-hour window forward.
                    AlarmScheduler(context).reschedule()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun leave(context: Context, eventId: Long) {
        val graph = DataGraph.get(context)
        val leg = graph.routes.legFor(eventId) ?: return
        val event = leg.event
        val start = time(event.start)
        val travel = leg.travel?.toMinutes()
        val mode = if (leg.mode.name == "BIKE") "Cycle" else "Walk"
        val text = buildString {
            if (travel != null) append("$mode $travel min")
            event.location?.let { append(" to ").append(it) }
            append(" · starts $start")
        }
        val builder = Notifications.simple(
            context, Notifications.CHANNEL_LEAVE, "Time to leave for ${event.title}", text,
            AlarmContract.openApp(context, AlarmContract.routeEvent(eventId), ("leave$eventId").hashCode()),
        ).setPriority(NotificationCompat.PRIORITY_HIGH)
        graph.routes.directionsUrl(eventId)?.let { url ->
            builder.addAction(Notifications.routeAction(context, url, ("route$eventId").hashCode()))
        }
        Notifications.post(context, ("leave$eventId").hashCode(), builder.build())
    }

    private suspend fun reminder(context: Context, eventId: Long) {
        val event = DataGraph.get(context).timetable.event(eventId) ?: return
        val text = listOfNotNull("At ${time(event.start)}", event.location).joinToString(" · ")
        val n = Notifications.simple(
            context, Notifications.CHANNEL_REMINDER, event.title, text,
            AlarmContract.openApp(context, AlarmContract.routeEvent(eventId), ("reminder$eventId").hashCode()),
        ).build()
        Notifications.post(context, ("reminder$eventId").hashCode(), n)
    }

    private suspend fun checkIn(context: Context, date: LocalDate) {
        val n = Notifications.simple(
            context, Notifications.CHANNEL_CHECK_IN, "The day in review",
            "Pages read, homework due and what is left of today's exercise.",
            AlarmContract.openApp(context, AlarmContract.ROUTE_CHECK_IN, CHECK_IN_ID),
        ).build()
        Notifications.post(context, CHECK_IN_ID + date.dayOfYear, n)
    }

    private suspend fun nudge(context: Context, date: LocalDate) {
        val graph = DataGraph.get(context)
        if (date.dayOfWeek in graph.settings.current().restDays) return
        val progress = Fitness.progress(graph.fitness.exercises(), graph.fitness.setsOn(date), date)
        if (!Fitness.needsMiddayNudge(progress)) return
        val upper = progress.filter { it.exercise.builtInKey in setOf("pushups", "pullups") }
        val text = upper.joinToString("  ·  ") { "${it.exercise.name} ${it.done} of ${it.target}" }
        val n = Notifications.simple(
            context, Notifications.CHANNEL_NUDGE, "Halfway through the day", text,
            AlarmContract.openApp(context, AlarmContract.ROUTE_FITNESS, NUDGE_ID),
        ).build()
        Notifications.post(context, NUDGE_ID, n)
    }

    private fun time(i: java.time.Instant): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.UK).format(i.atZone(ZoneId.systemDefault()))

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private const val CHECK_IN_ID = 7_000
        private const val NUDGE_ID = 8_000
    }
}

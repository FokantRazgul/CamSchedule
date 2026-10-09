package app.camplanner.alarms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Intents shared between the alarm engine and the app's single activity. */
object AlarmContract {
    const val EXTRA_KEY = "app.camplanner.extra.ALARM_KEY"
    const val EXTRA_KIND = "app.camplanner.extra.ALARM_KIND"
    const val EXTRA_EVENT_ID = "app.camplanner.extra.EVENT_ID"
    const val EXTRA_EPOCH_DAY = "app.camplanner.extra.EPOCH_DAY"

    /** MainActivity listens for this action; [EXTRA_ROUTE] says where to go. */
    const val ACTION_OPEN = "app.camplanner.action.OPEN"
    const val EXTRA_ROUTE = "app.camplanner.extra.ROUTE"

    const val ROUTE_TODAY = "today"
    const val ROUTE_CHECK_IN = "checkin"
    const val ROUTE_FITNESS = "fitness"
    fun routeEvent(id: Long) = "event/$id"

    const val SNOOZE_KEY = "SNOOZE"
    const val KIND_SNOOZE = "SNOOZE"

    fun openApp(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(ACTION_OPEN)
            .setPackage(context.packageName)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Broadcast fired by AlarmManager for [key]. The action is unique per key so PendingIntents never collide. */
    fun alarmIntent(context: Context, key: String, kind: String, eventId: Long?, epochDay: Long): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction("app.camplanner.ALARM/$key")
            .putExtra(EXTRA_KEY, key)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_EVENT_ID, eventId ?: -1L)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
}

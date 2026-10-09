package app.camplanner.alarms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.toArgb
import app.camplanner.designsystem.theme.DarkAtlasColors

/** Notification channels and builders. Copy is plain and specific: what, when, where. */
object Notifications {
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_LEAVE = "leave"
    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_CHECK_IN = "checkin"
    const val CHANNEL_NUDGE = "nudge"

    const val RINGING_ID = 1

    /** Burgundy accent for the small-icon tint, taken from the design tokens. */
    private val ACCENT_ARGB = DarkAtlasColors.accentFill.toArgb()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CHANNEL_ALARM, "Morning alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "The full-screen alarm before your first engagement"
                setSound(null, null) // The ringing service plays the alarm sound itself.
                enableVibration(false)
            },
            NotificationChannel(CHANNEL_LEAVE, "Time to leave", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When to set off, with a route"
            },
            NotificationChannel(CHANNEL_REMINDER, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A few minutes before each engagement"
            },
            NotificationChannel(CHANNEL_CHECK_IN, "Evening review", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The daily check-in after your last engagement"
            },
            NotificationChannel(CHANNEL_NUDGE, "Midday nudge", NotificationManager.IMPORTANCE_LOW).apply {
                description = "When push-ups and pull-ups are behind at midday"
            },
        )
        nm.createNotificationChannels(channels)
    }

    fun post(context: Context, id: Int, notification: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the call.
        }
    }

    fun simple(
        context: Context,
        channel: String,
        title: String,
        text: String,
        contentIntent: PendingIntent,
        category: String = NotificationCompat.CATEGORY_REMINDER,
    ): NotificationCompat.Builder = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_star)
        .setColor(ACCENT_ARGB)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setCategory(category)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)

    fun routeAction(context: Context, url: String, requestCode: Int): NotificationCompat.Action {
        val pi = PendingIntent.getActivity(
            context, requestCode, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_route, "Route", pi).build()
    }
}

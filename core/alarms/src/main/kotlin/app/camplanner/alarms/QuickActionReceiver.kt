package app.camplanner.alarms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.camplanner.data.DataGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Actions taken straight from a notification, such as "+10 push-ups". */
class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG_SET) return
        val exerciseId = intent.getLongExtra(EXTRA_EXERCISE, -1L).takeIf { it >= 0 } ?: return
        val amount = intent.getIntExtra(EXTRA_AMOUNT, 0)
        val pending = goAsync()
        scope.launch {
            try {
                DataGraph.get(context).fitness.log(exerciseId, amount, LocalDate.now())
                NotificationManagerCompat.from(context).cancel(AlarmReceiver.NUDGE_ID)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_LOG_SET = "app.camplanner.action.LOG_SET"
        private const val EXTRA_EXERCISE = "exercise"
        private const val EXTRA_AMOUNT = "amount"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun logSet(context: Context, exerciseId: Long, amount: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            ("log$exerciseId+$amount").hashCode(),
            Intent(context, QuickActionReceiver::class.java)
                .setAction(ACTION_LOG_SET)
                .putExtra(EXTRA_EXERCISE, exerciseId)
                .putExtra(EXTRA_AMOUNT, amount),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

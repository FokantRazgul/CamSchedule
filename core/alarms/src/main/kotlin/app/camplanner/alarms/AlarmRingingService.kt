package app.camplanner.alarms

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.camplanner.data.DataGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the alarm screen shows while the alarm rings. */
sealed interface RingingState {
    data object Idle : RingingState
    data class Ringing(val firstEngagement: String?, val snoozeMinutes: Int) : RingingState
}

object AlarmRinging {
    internal val mutableState = MutableStateFlow<RingingState>(RingingState.Idle)
    val state: StateFlow<RingingState> = mutableState
}

/**
 * Plays the morning alarm: alarm-stream sound that swells over half a minute, a gentle vibration,
 * and a full-screen notification with Snooze and Dismiss. Silences itself after ten minutes.
 */
class AlarmRingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volume = 0.15f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> {
                scope.launch {
                    val minutes = DataGraph.get(this@AlarmRingingService).settings.current().snoozeMinutes
                    AlarmScheduler(this@AlarmRingingService).snooze(minutes)
                    stopRinging()
                }
            }
            ACTION_DISMISS -> stopRinging()
            else -> start(intent?.getLongExtra(AlarmContract.EXTRA_EVENT_ID, -1L) ?: -1L)
        }
        return START_NOT_STICKY
    }

    private fun start(eventId: Long) {
        Notifications.ensureChannels(this)
        // Foreground first, with a placeholder line, so the system's start deadline is met.
        startInForeground(buildNotification(null))
        AlarmRinging.mutableState.value = RingingState.Ringing(null, 9)
        startSound()
        startVibration()
        handler.postDelayed({ stopRinging() }, AUTO_SILENCE_MS)

        scope.launch {
            val graph = DataGraph.get(this@AlarmRingingService)
            val snooze = graph.settings.current().snoozeMinutes
            val first = if (eventId >= 0) graph.timetable.event(eventId) else null
            val line = first?.let {
                val t = DateTimeFormatter.ofPattern("HH:mm", Locale.UK).format(it.start.atZone(ZoneId.systemDefault()))
                listOfNotNull("${it.title} at $t", it.location).joinToString(", ")
            }
            AlarmRinging.mutableState.value = RingingState.Ringing(line, snooze)
            startInForeground(buildNotification(line))
        }
    }

    private fun startInForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this, Notifications.RINGING_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    private fun buildNotification(line: String?): android.app.Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 1, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_star)
            .setContentTitle("Good morning")
            .setContentText(line ?: "Your first engagement is coming up")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze", serviceIntent(this, ACTION_SNOOZE, 2))
            .addAction(0, "Dismiss", serviceIntent(this, ACTION_DISMISS, 3))
            .build()
    }

    private fun startSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setWakeMode(this@AlarmRingingService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@AlarmRingingService, uri)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
        } catch (_: Exception) {
            null
        }
        handler.post(swell)
    }

    /** Raise the volume a little every two seconds: waking, not startling. */
    private val swell = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (volume < 1f) {
                volume = (volume + 0.06f).coerceAtMost(1f)
                p.setVolume(volume, volume)
                handler.postDelayed(this, 2_000)
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 900), 0))
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        AlarmRinging.mutableState.value = RingingState.Idle
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { release() }
        vibrator?.cancel()
        AlarmRinging.mutableState.value = RingingState.Idle
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.camplanner.alarms.START"
        const val ACTION_SNOOZE = "app.camplanner.alarms.SNOOZE"
        const val ACTION_DISMISS = "app.camplanner.alarms.DISMISS"
        private const val AUTO_SILENCE_MS = 10 * 60 * 1000L

        fun startIntent(context: Context, eventId: Long?): Intent =
            Intent(context, AlarmRingingService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmContract.EXTRA_EVENT_ID, eventId ?: -1L)

        fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode, Intent(context, AlarmRingingService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

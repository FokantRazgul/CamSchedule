package app.camplanner.alarms

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.camplanner.designsystem.components.MoonGlyph
import app.camplanner.designsystem.components.StarFieldBackground
import app.camplanner.designsystem.components.SunGlyph
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.designsystem.theme.CamPlannerTheme
import app.camplanner.domain.Almanac
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The full-screen morning alarm, shown over the lock screen. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            CamPlannerTheme {
                val state by AlarmRinging.state.collectAsStateWithLifecycle()
                LaunchedEffect(state) { if (state is RingingState.Idle) finish() }
                val ringing = state as? RingingState.Ringing
                AlarmContent(
                    firstEngagement = ringing?.firstEngagement,
                    snoozeMinutes = ringing?.snoozeMinutes ?: 9,
                    onSnooze = { send(AlarmRingingService.ACTION_SNOOZE) },
                    onDismiss = { send(AlarmRingingService.ACTION_DISMISS) },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmRingingService::class.java).setAction(action))
    }
}

@Composable
fun AlarmContent(
    firstEngagement: String?,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    now: LocalTime? = null,
    today: LocalDate? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val c = Atlas.colors
    val clock by produceState(now ?: LocalTime.now()) {
        if (now == null) while (true) { value = LocalTime.now(); delay(1_000) }
    }
    val date = today ?: LocalDate.now()
    val sun = Almanac.sunTimes(date, Almanac.CAMBRIDGE_LAT, Almanac.CAMBRIDGE_LNG)
    val moon = Almanac.moon(date.atTime(21, 0).atZone(zone).toInstant())
    val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    StarFieldBackground(Modifier.fillMaxSize(), graticule = false, starsPer10k = 1.4f) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("Good morning", style = Atlas.type.groupTitle, color = c.gold)
            Text(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK).format(date), style = Atlas.type.reading, color = c.textSecondary)
            Text(
                hm.format(clock),
                style = Atlas.type.heroNumber.copy(fontSize = Atlas.type.heroNumber.fontSize * 2.3f, lineHeight = Atlas.type.heroNumber.fontSize * 2.3f),
                color = c.text,
            )
            Spacer(Modifier.height(20.dp))
            if (firstEngagement != null) {
                Group {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("FIRST TODAY", style = Atlas.type.caption, color = c.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(firstEngagement, style = Atlas.type.groupTitle, color = c.text)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sun.sunrise != null) {
                    SunGlyph()
                    Text("Sunrise ${hm.format(sun.sunrise!!.atZone(zone))}", style = Atlas.type.supporting, color = c.textSecondary)
                    Spacer(Modifier.width(8.dp))
                }
                MoonGlyph(moon.illumination.toFloat(), moon.waxing)
                Text(moon.phase.label, style = Atlas.type.supporting, color = c.textSecondary)
            }
            Spacer(Modifier.weight(1.3f))
            PrimaryButton("Dismiss", onDismiss, Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Snooze $snoozeMinutes min", onSnooze, Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}

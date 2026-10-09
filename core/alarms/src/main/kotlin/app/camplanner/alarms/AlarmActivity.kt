package app.camplanner.alarms

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.DoubleRule
import app.camplanner.designsystem.components.MoonGlyph
import app.camplanner.designsystem.components.StarFieldBackground
import app.camplanner.designsystem.components.SunGlyph
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.designsystem.theme.CamPlannerTheme
import app.camplanner.domain.Almanac
import app.camplanner.domain.Roman
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

    StarFieldBackground(Modifier.fillMaxSize(), starsPer10k = 1.6f) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text("MANE  ·  ${Roman.of(date.year)}", style = Atlas.type.overline, color = c.textSecondary)
            Spacer(Modifier.height(Atlas.space.s))
            DoubleRule(Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Text(
                DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.UK).format(date),
                style = Atlas.type.dateline, color = c.textSecondary,
            )
            Text(
                hm.format(clock),
                style = Atlas.type.numeralLarge.copy(fontSize = Atlas.type.numeralLarge.fontSize * 1.9f, lineHeight = Atlas.type.numeralLarge.fontSize * 1.9f),
                color = c.text,
            )
            Spacer(Modifier.height(Atlas.space.l))
            Text(
                firstEngagement?.let { "First, $it." } ?: "Good morning.",
                style = Atlas.type.aside.copy(fontSize = Atlas.type.title.fontSize),
                color = c.gold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Atlas.space.xl))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sun.sunrise != null) {
                    SunGlyph()
                    Text("Sunrise ${hm.format(sun.sunrise!!.atZone(zone))}", style = Atlas.type.time, color = c.textSecondary)
                    Spacer(Modifier.height(1.dp).padding(horizontal = 6.dp))
                }
                MoonGlyph(moon.illumination.toFloat(), moon.waxing)
                Text(moon.phase.label, style = Atlas.type.time, color = c.textSecondary)
            }
            Spacer(Modifier.weight(1.2f))
            Box(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Atlas.space.m)) {
                    AtlasButton(
                        "Dismiss", onDismiss, style = ActionStyle.Filled, modifier = Modifier.fillMaxWidth().height(60.dp),
                        textStyle = Atlas.type.title,
                    )
                    AtlasButton(
                        "Snooze $snoozeMinutes min", onSnooze, style = ActionStyle.Outlined, modifier = Modifier.fillMaxWidth().height(56.dp),
                        textStyle = Atlas.type.label,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

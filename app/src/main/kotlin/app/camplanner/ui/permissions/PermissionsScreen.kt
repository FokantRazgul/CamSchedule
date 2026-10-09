package app.camplanner.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.camplanner.alarms.AlarmPermissions
import app.camplanner.alarms.PermissionStatus
import app.camplanner.alarms.Reschedule
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.AtlasPage
import app.camplanner.designsystem.components.ChartStar
import app.camplanner.designsystem.components.Hairline
import app.camplanner.designsystem.components.SectionTitle
import app.camplanner.designsystem.theme.Atlas

@Composable
fun PermissionsRoute(onDone: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(AlarmPermissions.status(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = AlarmPermissions.status(context)
        Reschedule.soon(context) // permissions may have changed what can be scheduled
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = AlarmPermissions.status(context)
    }
    PermissionsScreen(
        status = status,
        onNotifications = {
            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(context, AlarmPermissions.notificationSettings(context))
        },
        onExact = { AlarmPermissions.exactAlarmSettings(context)?.let { open(context, it) } },
        onFullScreen = { AlarmPermissions.fullScreenSettings(context)?.let { open(context, it) } },
        onBattery = { open(context, AlarmPermissions.batterySettings()) },
        onDone = onDone,
        onBack = onBack,
    )
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun PermissionsScreen(
    status: PermissionStatus,
    onNotifications: () -> Unit,
    onExact: () -> Unit,
    onFullScreen: () -> Unit,
    onBattery: () -> Unit,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
) {
    AtlasPage(
        mastheadLeft = "Permissions",
        mastheadRight = if (status.essentialsGranted) "all granted" else "action needed",
        title = "Before the Alarms Can Ring",
        aside = "Android asks for each of these separately.",
        onBack = onBack,
    ) {
        SectionTitle("I", "Required")
        Item("Notifications", "Reminders, time to leave, the evening review.", status.notifications, onNotifications)
        Item("Exact alarms", "So the morning alarm and leave alerts fire on the minute.", status.exactAlarms, onExact)
        Item("Full-screen alarm", "So the morning alarm can wake the screen over the lock screen.", status.fullScreen, onFullScreen, last = true)

        Spacer(Modifier.height(Atlas.space.xl))
        SectionTitle("II", "Advised")
        Item(
            "Battery optimisation",
            "Some phones delay or drop alarms for optimised apps. Choose “All apps”, find Cam Planner and set it to “Don't optimise” (or “Unrestricted”).",
            status.batteryUnrestricted, onBattery, last = true, advisory = true,
        )

        Spacer(Modifier.height(Atlas.space.xxl))
        AtlasButton(
            if (status.essentialsGranted) "Continue" else "Continue anyway",
            onDone,
            style = if (status.essentialsGranted) ActionStyle.Filled else ActionStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Item(title: String, why: String, granted: Boolean, onFix: () -> Unit, last: Boolean = false, advisory: Boolean = false) {
    val c = Atlas.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Atlas.space.l), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Atlas.type.title, color = c.text)
            Text(why, style = Atlas.type.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(Atlas.space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (granted) {
                    ChartStar(size = 9.dp)
                    Text("  Granted", style = Atlas.type.time, color = c.gold)
                } else {
                    Text(if (advisory) "Not set" else "Not granted", style = Atlas.type.time, color = c.accentText)
                }
            }
        }
        if (!granted) AtlasButton(if (advisory) "Open" else "Allow", onFix)
    }
    if (!last) Hairline()
}

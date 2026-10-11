package app.camplanner.ui.more

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
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
import app.camplanner.designsystem.kit.Group
import app.camplanner.designsystem.kit.GroupDivider
import app.camplanner.designsystem.kit.GroupHeader
import app.camplanner.designsystem.kit.PrimaryButton
import app.camplanner.designsystem.kit.SecondaryButton
import app.camplanner.designsystem.kit.TertiaryButton
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.ui.common.SubPage

@Composable
fun PermissionsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(AlarmPermissions.status(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = AlarmPermissions.status(context)
        Reschedule.soon(context) // permissions may have changed what can be scheduled
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = AlarmPermissions.status(context)
    }
    PermissionsScreen(
        status,
        onNotifications = {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(context, AlarmPermissions.notificationSettings(context))
        },
        onExact = { AlarmPermissions.exactAlarmSettings(context)?.let { open(context, it) } },
        onFullScreen = { AlarmPermissions.fullScreenSettings(context)?.let { open(context, it) } },
        onBattery = { open(context, AlarmPermissions.batterySettings()) },
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
    onBack: () -> Unit,
) {
    val c = Atlas.colors
    SubPage(
        "Alarms and notifications", onBack,
        bottomBar = {
            PrimaryButton(if (status.essentialsGranted) "Done" else "Not now", onBack, Modifier.fillMaxWidth().padding(16.dp))
        },
    ) {
        Text(
            "Android asks for each of these separately. Without them the morning alarm can't ring on time.",
            style = Atlas.type.supporting, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
        )
        GroupHeader("Needed")
        Group {
            Item("Notifications", "Reminders, time to leave and the evening review.", status.notifications, onNotifications)
            GroupDivider()
            Item("Exact alarms", "So the alarm and leave alerts fire on the minute.", status.exactAlarms, onExact)
            GroupDivider()
            Item("Full-screen alarm", "So the alarm can wake the screen over the lock screen.", status.fullScreen, onFullScreen)
        }
        GroupHeader("Advised")
        Group {
            Item(
                "Battery",
                "Some phones delay alarms for optimised apps. Choose All apps, find Cam Planner and set it to Unrestricted or Don't optimise.",
                status.batteryUnrestricted, onBattery, advisory = true,
            )
        }
    }
}

@Composable
private fun Item(title: String, why: String, granted: Boolean, onFix: () -> Unit, advisory: Boolean = false) {
    val c = Atlas.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
            tint = if (granted) c.gold else if (advisory) c.textSecondary else c.accentText, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Atlas.type.itemTitle, color = c.text)
            Text(why, style = Atlas.type.supporting, color = c.textSecondary)
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            if (advisory) TertiaryButton("Open", onFix) else SecondaryButton("Allow", onFix)
        }
    }
}

package app.camplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.camplanner.alarms.AlarmContract
import app.camplanner.designsystem.theme.CamPlannerTheme
import app.camplanner.ui.CamPlannerNav
import kotlinx.coroutines.flow.MutableStateFlow

/** The single activity; every screen is a Compose destination. */
class MainActivity : ComponentActivity() {
    private val deepLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            CamPlannerTheme {
                CamPlannerNav(deepLink, onDeepLinkHandled = { deepLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action != AlarmContract.ACTION_OPEN) return
        deepLink.value = when (val route = intent.getStringExtra(AlarmContract.EXTRA_ROUTE)) {
            null, AlarmContract.ROUTE_TODAY -> "today"
            else -> route
        }
    }
}

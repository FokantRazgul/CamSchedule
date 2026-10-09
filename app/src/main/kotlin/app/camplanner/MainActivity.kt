package app.camplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.camplanner.designsystem.components.AtlasNavBar
import app.camplanner.designsystem.components.NavItem
import app.camplanner.designsystem.theme.CamPlannerTheme
import app.camplanner.ui.checkin.CheckInScreen
import app.camplanner.ui.fitness.FitnessScreen
import app.camplanner.ui.preview.SampleData
import app.camplanner.ui.today.TodayScreen

/**
 * Design-review build: shows the three approved-for-review screens with sample data.
 * Navigation, data and alarms are wired up after the design is signed off.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CamPlannerTheme {
                var tab by rememberSaveable { mutableStateOf("today") }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            "today" -> TodayScreen(SampleData.today, {}, {}, { tab = "checkin" }, {})
                            "checkin" -> CheckInScreen(SampleData.checkIn, { _, _ -> }, { _, _ -> }, {}, {})
                            else -> FitnessScreen(SampleData.fitness, {}, { _, _ -> }, {}, {}, {}, {}, {})
                        }
                    }
                    AtlasNavBar(
                        items = listOf(NavItem("today", "Today"), NavItem("checkin", "Check-in"), NavItem("fitness", "Fitness")),
                        selected = tab,
                        onSelect = { tab = it },
                    )
                }
            }
        }
    }
}

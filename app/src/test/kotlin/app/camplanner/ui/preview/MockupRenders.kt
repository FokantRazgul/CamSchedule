package app.camplanner.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.designsystem.theme.CamPlannerTheme
import app.camplanner.ui.next.CalendarNextScreen
import app.camplanner.ui.next.EventEditorSheet
import app.camplanner.ui.next.FitnessNextScreen
import app.camplanner.ui.next.NextSamples
import app.camplanner.ui.next.TodayNextScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the redesign mockups to app/build/mockups/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h860dp-xxhdpi", application = android.app.Application::class)
class MockupRenders {
    private fun render(name: String, heightDp: Int, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+h${heightDp}dp")
        captureRoboImage("build/mockups/$name.png") { CamPlannerTheme { content() } }
    }

    @Test fun today() = render("1-today", 1320) { TodayNextScreen(NextSamples.today) }
    @Test fun todayPhone() = render("1b-today-first-screen", 860) { TodayNextScreen(NextSamples.today) }
    @Test fun calendar() = render("2-calendar", 860) { CalendarNextScreen(NextSamples.calendar) }

    @Test fun editor() = render("3-new-event-sheet", 1240) {
        Box(Modifier.fillMaxSize().background(Atlas.colors.background), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Atlas.colors.surfaceHigh).verticalScroll(rememberScrollState()).padding(top = 0.dp),
            ) { EventEditorSheet(NextSamples.editor) }
        }
    }

    @Test fun fitness() = render("4-fitness", 1700) { FitnessNextScreen(NextSamples.fitness, snackbar = "Removed 20 push-ups") }
    @Test fun fitnessPhone() = render("4b-fitness-first-screen", 860) { FitnessNextScreen(NextSamples.fitness) }
}

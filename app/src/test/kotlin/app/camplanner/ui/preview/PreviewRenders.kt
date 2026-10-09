package app.camplanner.ui.preview

import androidx.compose.runtime.Composable
import app.camplanner.designsystem.preview.DesignSystemSheet
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the design-review previews to PNG (app/build/previews/) with the real fonts and
 * drawing code. Run: ./gradlew :app:testDebugUnitTest --tests '*PreviewRenders*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the renders are pure UI and must not start WorkManager or alarms.
@Config(qualifiers = "w393dp-h960dp-xxhdpi", application = android.app.Application::class)
class PreviewRenders {

    private fun render(name: String, heightDp: Int, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+h${heightDp}dp")
        captureRoboImage("build/previews/$name.png", content = content)
    }

    @Test fun today() = render("1-today", 1250) { TodayPreview() }

    @Test fun todayAttention() = render("1b-today-permission-missing", 700) { TodayAttentionPreview() }

    @Test fun checkIn() = render("2-check-in", 1250) { CheckInPreview() }

    @Test fun fitness() = render("3-fitness", 1360) { FitnessPreview() }

    @Test fun designSystem() = render("0-design-system", 900) { DesignSystemSheet() }
}

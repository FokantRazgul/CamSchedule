package app.camplanner

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.camplanner.alarms.AlarmScheduler
import app.camplanner.data.DataGraph
import app.camplanner.model.Assignment
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/** CamPlannerApp with WorkManager set up the way androidx.startup would on a phone. */
class SmokeTestApp : CamPlannerApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this, Configuration.Builder().setMinimumLoggingLevel(Log.WARN).setExecutor(SynchronousExecutor()).build(),
        )
        super.onCreate()
    }
}

/**
 * End to end on Robolectric: import a timetable, schedule alarms, open every screen of the real
 * app and save a screenshot of each to app/build/smoke/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h900dp-xxhdpi", application = SmokeTestApp::class)
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/London")

    @Before
    fun seed() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val graph = DataGraph.get(context)
        val today = LocalDate.now(zone)
        val d = DateTimeFormatter.BASIC_ISO_DATE.format(today)
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Smoke//EN
            BEGIN:VEVENT
            UID:la@test
            DTSTART;TZID=Europe/London:${d}T090000
            DTEND;TZID=Europe/London:${d}T100000
            RRULE:FREQ=WEEKLY;COUNT=8
            SUMMARY:Linear Algebra - Lecture
            LOCATION:Mill Lane Lecture Rooms
            END:VEVENT
            BEGIN:VEVENT
            UID:phys@test
            DTSTART;TZID=Europe/London:${d}T140000
            DTEND;TZID=Europe/London:${d}T170000
            RRULE:FREQ=WEEKLY;COUNT=8
            SUMMARY:Physics Practical
            LOCATION:Cavendish Laboratory
            END:VEVENT
            BEGIN:VEVENT
            UID:supo@test
            DTSTART;TZID=Europe/London:${d}T173000
            DTEND;TZID=Europe/London:${d}T183000
            SUMMARY:Linear Algebra (Supervision)
            LOCATION:Trinity College
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        runBlocking {
            graph.fitness.ensureSeeded()
            val result = graph.timetable.parse(ics.byteInputStream(), Instant.now().plusSeconds(400L * 86_400))
            graph.timetable.replaceImported(result, zone)
            graph.settings.update { it.copy(termStart = today.minusDays(12), termEnd = today.plusDays(50), homeAddress = "Trinity Street") }
            graph.homework.save(Assignment(0, "Example Sheet 2", null, "Questions 1 to 6", today.plusDays(1).atTime(12, 0).atZone(zone).toInstant(), false))
            graph.homework.save(Assignment(0, "Lab write-up", null, "", today.minusDays(1).atTime(12, 0).atZone(zone).toInstant(), false))
            val pushups = graph.fitness.exercises().first { it.builtInKey == "pushups" }
            graph.fitness.log(pushups.id, 40, today)
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/smoke/$name.png")
    }

    private fun tab(label: String) {
        compose.onAllNodesWithText(label).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirst() = this[0]

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun importScheduleAndVisitEveryScreen() {
        // Alarms: the plan for the next 72 hours lands in AlarmManager.
        runBlocking { AlarmScheduler(context).reschedule(fresh = true) }
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        val stored = runBlocking { DataGraph.get(context).db.scheduledAlarms().all() }
        assertTrue("alarms registered: ${alarms.size}", alarms.isNotEmpty())
        assertTrue("alarm rows: ${stored.map { it.key }}", stored.any { it.kind == "CHECK_IN" })

        ActivityScenario.launch(MainActivity::class.java).use {
            // First launch walks through permissions when something essential is missing.
            if (compose.onAllNodes(hasText("Before the Alarms Can Ring")).fetchSemanticsNodes().isNotEmpty()) {
                shot("00-permissions")
                compose.onNodeWithText("Continue", substring = true).performScrollTo().performClick()
            }
            waitFor("The Order of the Day")
            shot("01-today")

            tab("Week"); waitFor("The Week"); shot("02-week")
            compose.onAllNodesWithText("Physics Practical").onFirst().performClick()
            waitFor("Whereabouts"); shot("03-event")
            compose.onNodeWithText("Back").performClick()

            tab("Fitness"); waitFor("Upper Body"); shot("04-fitness")
            tab("Homework"); waitFor("Assignments"); shot("05-homework")
            compose.onNodeWithText("Add an assignment").performClick()
            waitFor("A New Assignment"); shot("06-homework-new")
            compose.onNodeWithText("Back").performClick()

            tab("Index"); waitFor("Index"); shot("07-index")
            listOf(
                "The Evening Review" to "The Day in Review",
                "The Reckoning" to "Reading",
                "Subjects" to "The Subjects",
                "The Gazetteer" to "The Gazetteer",
                "Import a Timetable" to "Import a Calendar",
                "Exercises" to "The Exercises",
                "Settings" to "The Settings",
                "Permissions" to "Before the Alarms Can Ring",
            ).forEachIndexed { i, (entry, expect) ->
                compose.onAllNodesWithText(entry).onFirst().performScrollTo().performClick()
                waitFor(expect)
                shot("%02d-%s".format(8 + i, entry.lowercase().replace(' ', '-')))
                androidx.test.espresso.Espresso.pressBack()
                waitFor("Everything else, in order.")
            }
        }
    }
}

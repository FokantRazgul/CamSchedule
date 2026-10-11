package app.camplanner

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.camplanner.alarms.AlarmScheduler
import app.camplanner.data.DataGraph
import app.camplanner.model.Assignment
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
 * End to end on Robolectric: import a timetable, then use the real app the way a student would:
 * add a repeating event, log and undo push-ups, remove a set, edit homework, cancel and restore
 * a lecture, and open every page. Screenshots go to app/build/smoke/.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi", application = SmokeTestApp::class)
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val graph: DataGraph get() = DataGraph.get(context)
    private val zone = ZoneId.of("Europe/London")
    private val today: LocalDate get() = LocalDate.now(zone)

    @Before
    fun seed() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
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
        captureScreenRoboImage("build/smoke/$name.png")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun gone(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty() }
    }

    private fun text(t: String): SemanticsNodeInteraction = compose.onAllNodesWithText(t)[0]

    private fun click(t: String) {
        // Buttons outside a scrolling page (the add button, sheet actions) can't be scrolled to.
        runCatching { text(t).performScrollTo() }
        text(t).performClick()
        compose.waitForIdle()
    }

    private fun clickDescription(d: String) {
        compose.onAllNodesWithContentDescription(d)[0].performClick()
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        compose.onAllNodes(hasText(label) and !hasSetTextAction())[0].performClick()
        compose.waitForIdle()
    }

    private fun typeInto(label: String, value: String) {
        compose.onAllNodes(hasSetTextAction() and hasText(label, substring = true))[0].performTextInput(value)
        compose.waitForIdle()
    }

    private fun pushUpsDone(): Int = runBlocking {
        val id = graph.fitness.exercises().first { it.builtInKey == "pushups" }.id
        graph.fitness.setsOn(today).filter { it.exerciseId == id }.sumOf { it.amount }
    }

    @Test
    fun aStudentsDay() {
        // Alarms: the plan for the next 72 hours lands in AlarmManager.
        runBlocking { AlarmScheduler(context).reschedule(fresh = true) }
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue("alarms registered: ${alarms.size}", alarms.isNotEmpty())

        ActivityScenario.launch(MainActivity::class.java).use {
            // First launch asks for what the alarm needs.
            if (compose.onAllNodes(hasText("Alarms and notifications")).fetchSemanticsNodes().isNotEmpty()) {
                shot("00-permissions")
                click("Not now")
            }
            waitFor("Schedule")
            shot("01-today")

            // Add a repeating event from Today.
            click("Add")
            waitFor("New event")
            typeInto("Title", "Rowing")
            click("Weekly")
            shot("02-new-event")
            click("Save")
            waitFor("Added Rowing")
            val series = runBlocking { graph.db.series().all() }
            assertEquals(1, series.size)
            assertTrue(runBlocking { graph.timetable.allTimedEvents() }.count { it.title == "Rowing" } > 10)

            // Calendar shows it; open a lecture, cancel it, then undo.
            tab("Calendar")
            waitFor("Physics Practical")
            shot("03-calendar")
            click("Physics Practical")
            waitFor("Cancel lecture")
            shot("04-event-sheet")
            click("Cancel lecture")
            waitFor("Cancelled Physics Practical")
            assertTrue(runBlocking { graph.timetable.allTimedEvents() }.any { it.title == "Physics Practical" && it.cancelled })
            click("Undo")
            compose.waitUntil(10_000) { runBlocking { graph.timetable.allTimedEvents() }.none { it.cancelled } }

            // Fitness: an accidental +10 is undone, then a set is removed by hand.
            tab("Fitness")
            waitFor("Push-ups")
            compose.onAllNodes(hasContentDescription("Add 10 Push-ups"))[0].performClick()
            waitFor("Added 10 push-ups")
            assertEquals(50, pushUpsDone())
            shot("05-fitness-added")
            click("Undo")
            compose.waitUntil(10_000) { pushUpsDone() == 40 }
            // Wait for the list to drop the undone set before removing the remaining one.
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Remove this set")).fetchSemanticsNodes().size == 1 }
            clickDescription("Remove this set")
            compose.waitUntil(10_000) { pushUpsDone() == 0 }
            waitFor("Removed a set")
            shot("06-fitness")
            click("Undo")
            compose.waitUntil(10_000) { pushUpsDone() == 40 }

            // Homework: edit an assignment and tick another off.
            tab("Homework")
            waitFor("Example Sheet 2")
            shot("07-homework")
            click("Example Sheet 2")
            waitFor("Notes")
            compose.onAllNodes(hasSetTextAction() and hasText("Title", substring = true))[0].performTextReplacement("Example Sheet 2 (Q1-6)")
            shot("08-homework-edit")
            click("Save")
            compose.waitUntil(10_000) { runBlocking { graph.homework.all() }.any { it.title == "Example Sheet 2 (Q1-6)" } }
            waitFor("Saved")

            // Every page under More.
            tab("More")
            waitFor("Statistics")
            shot("09-more")
            listOf(
                "Statistics" to "Streaks",
                "Evening review" to "Finish review",
                "Runs" to "No runs yet",
                "Exercises and targets" to "Upper body",
                "Import timetable" to "Choose an .ics file",
                "Subjects" to "Linear Algebra",
                "Places" to "Cavendish Laboratory",
                "Settings" to "Morning alarm",
                "Alarms and notifications" to "Exact alarms",
            ).forEachIndexed { i, (entry, marker) ->
                click(entry)
                waitFor(marker)
                shot("%02d-%s".format(10 + i, entry.lowercase().replace(' ', '-')))
                clickDescription("Back")
                waitFor("Statistics")
            }
        }
    }
}

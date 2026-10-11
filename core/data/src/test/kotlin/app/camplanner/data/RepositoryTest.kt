package app.camplanner.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.camplanner.data.db.CamPlannerDatabase
import app.camplanner.data.repo.BackupService
import app.camplanner.data.repo.EventDraft
import app.camplanner.data.repo.FitnessRepository
import app.camplanner.data.repo.Scope
import app.camplanner.data.repo.TimetableRepository
import app.camplanner.data.settings.SettingsStore
import app.camplanner.ics.IcsImporter
import app.camplanner.model.CalendarEvent
import app.camplanner.model.Frequency
import app.camplanner.model.RepeatRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private val zone = ZoneId.of("Europe/London")
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: CamPlannerDatabase
    private lateinit var repo: TimetableRepository

    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CamPlannerDatabase::class.java)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CamPlannerDatabase::class.java).allowMainThreadQueries().build()
        repo = TimetableRepository(db, {}, {}, { zone })
    }

    @After
    fun tearDown() = db.close()

    private val tue = LocalDate.parse("2026-10-20")

    private fun draft(
        title: String = "Climbing",
        date: LocalDate = tue,
        start: LocalTime = LocalTime.of(19, 0),
        end: LocalTime = LocalTime.of(20, 30),
        repeat: RepeatRule? = RepeatRule(Frequency.WEEKLY, days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), until = LocalDate.parse("2026-11-05")),
    ) = EventDraft(title, null, date, start, end, repeat, "Kelsey Kerridge", null, null, 30, )

    private suspend fun all(): List<CalendarEvent> = repo.between(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2028-01-01T00:00:00Z"))
    private fun CalendarEvent.date() = start.atZone(zone).toLocalDate()
    private fun CalendarEvent.time() = start.atZone(zone).toLocalTime()

    @Test
    fun `repeating event - create, edit one, edit all, delete one, delete all, undo`() = runTest {
        repo.create(draft())
        var events = all()
        assertEquals(listOf("2026-10-20", "2026-10-22", "2026-10-27", "2026-10-29", "2026-11-03", "2026-11-05"), events.map { it.date().toString() })
        assertTrue(events.all { it.time() == LocalTime.of(19, 0) && it.reminderMinutes == 30 })

        // Edit only the 27th: it moves to 18:00 and leaves the series.
        val the27th = events.first { it.date() == LocalDate.parse("2026-10-27") }
        repo.update(the27th.id, draft(date = the27th.date(), start = LocalTime.of(18, 0), end = LocalTime.of(19, 0)), Scope.THIS)
        events = all()
        assertEquals(LocalTime.of(18, 0), events.first { it.date() == LocalDate.parse("2026-10-27") }.time())
        assertNull(events.first { it.date() == LocalDate.parse("2026-10-27") }.seriesId)

        // Rename all: the detached one keeps its own title.
        val first = events.first()
        repo.update(first.id, draft(title = "Climbing club"), Scope.ALL)
        events = all()
        assertEquals(5, events.count { it.title == "Climbing club" })
        assertEquals("Climbing", events.first { it.date() == LocalDate.parse("2026-10-27") }.title)

        // Delete one, then undo.
        val the29th = events.first { it.date() == LocalDate.parse("2026-10-29") }
        val undoOne = repo.delete(the29th.id, Scope.THIS)!!
        assertFalse(all().any { it.date() == LocalDate.parse("2026-10-29") })
        repo.undo(undoOne)
        assertTrue(all().any { it.date() == LocalDate.parse("2026-10-29") && it.title == "Climbing club" })

        // Delete the whole series, then undo.
        val undoAll = repo.delete(all().first { it.seriesId != null }.id, Scope.ALL)!!
        assertEquals(listOf("2026-10-27"), all().map { it.date().toString() })
        repo.undo(undoAll)
        assertEquals(6, all().size)
    }

    @Test
    fun `one-off event, made to repeat, then back to one-off`() = runTest {
        repo.create(draft(repeat = null))
        val single = all().single()
        repo.update(single.id, draft(repeat = RepeatRule(Frequency.DAILY, count = 3)), Scope.ALL)
        assertEquals(3, all().size)
        repo.update(all().first().id, draft(repeat = null), Scope.ALL)
        assertEquals(1, all().size)
        assertNull(all().single().seriesId)
    }

    @Test
    fun `single delete undo restores the same event`() = runTest {
        repo.create(draft(repeat = null))
        val e = all().single()
        val undo = repo.delete(e.id, Scope.THIS)!!
        assertTrue(all().isEmpty())
        repo.undo(undo)
        assertEquals(e, all().single())
    }

    private val ics = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:an@test
        DTSTART;TZID=Europe/London:20261020T110000
        DTEND;TZID=Europe/London:20261020T120000
        RRULE:FREQ=WEEKLY;COUNT=3
        SUMMARY:Analysis I - Lecture
        LOCATION:Mill Lane Lecture Rooms
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    private suspend fun import() = repo.replaceImported(IcsImporter().parse(ics, Instant.parse("2027-01-01T00:00:00Z")), zone)

    @Test
    fun `cancelled and moved lectures survive a re-import, and cancelling undoes`() = runTest {
        val tz = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
        try {
            import()
            val lectures = all()
            assertEquals(3, lectures.size)
            val undo = repo.delete(lectures[0].id, Scope.THIS)!!
            assertTrue(all()[0].cancelled)
            repo.update(lectures[1].id, draft(title = "ignored", date = lectures[1].date(), start = LocalTime.of(14, 0), end = LocalTime.of(15, 0), repeat = null), Scope.THIS)

            import() // re-import the same file
            val after = all()
            assertTrue(after[0].cancelled)
            assertEquals(LocalTime.of(14, 0), after[1].time())
            assertEquals("Analysis I - Lecture", after[1].title) // titles of imported lectures stay as imported

            repo.undo(undo)
            assertFalse(all()[0].cancelled)
        } finally {
            java.util.TimeZone.setDefault(tz)
        }
    }

    @Test
    fun `taking sets off never goes below zero`() = runTest {
        val fitness = FitnessRepository(db)
        fitness.ensureSeeded()
        val push = fitness.exercises().first { it.builtInKey == "pushups" }
        fitness.log(push.id, 5, tue)
        fitness.log(push.id, -10, tue)
        assertEquals(0, fitness.setsOn(tue).sumOf { it.amount })
        val id = fitness.log(push.id, 20, tue)!!
        val removed = fitness.deleteSet(id)!!
        assertEquals(0, fitness.setsOn(tue).sumOf { it.amount })
        fitness.restoreSet(removed)
        assertEquals(20, fitness.setsOn(tue).sumOf { it.amount })
    }

    @Test
    fun `backup round trip`() = runTest {
        repo.create(draft())
        FitnessRepository(db).ensureSeeded()
        val settings = SettingsStore(context)
        settings.update { it.copy(homeAddress = "Trinity Street", bufferMinutes = 7) }
        val out = ByteArrayOutputStream()
        BackupService(db, settings) {}.export(out)

        val other = Room.inMemoryDatabaseBuilder(context, CamPlannerDatabase::class.java).allowMainThreadQueries().build()
        settings.update { it.copy(homeAddress = "", bufferMinutes = 5) }
        BackupService(other, settings) {}.restore(ByteArrayInputStream(out.toByteArray()))
        val restored = TimetableRepository(other, {}, {}, { zone }).between(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2028-01-01T00:00:00Z"))
        assertEquals(all(), restored)
        assertEquals("Trinity Street", settings.current().homeAddress)
        assertEquals(7, settings.current().bufferMinutes)
        other.close()
    }

    @Test
    fun `version 1 databases upgrade to version 2 with their data`() {
        migrations.createDatabase("migrate.db", 1).apply {
            execSQL("INSERT INTO subject (id, name, colorIndex, isAcademic) VALUES (1, 'Analysis I', 0, 1)")
            execSQL(
                "INSERT INTO event (id, eventKey, source, title, subjectId, startUtc, endUtc, allDay, location, note) " +
                    "VALUES (1, 'k', 'IMPORTED', 'Analysis I', 1, 1000, 2000, 0, 'Mill Lane', NULL)",
            )
            execSQL("INSERT INTO event_override (eventKey, travelMode) VALUES ('k', 'BIKE')")
            close()
        }
        val db2 = migrations.runMigrationsAndValidate("migrate.db", 2, true)
        db2.query("SELECT title, seriesId, reminderMinutes FROM event").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Analysis I", c.getString(0))
            assertTrue(c.isNull(1))
        }
        db2.query("SELECT travelMode, cancelled FROM event_override").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("BIKE", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
    }
}

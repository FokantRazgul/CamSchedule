package app.camplanner.domain

import app.camplanner.domain.DayStatus.MET
import app.camplanner.domain.DayStatus.MISSED
import app.camplanner.domain.DayStatus.SKIP
import app.camplanner.model.AppSettings
import app.camplanner.model.Assignment
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.ReadingLog
import app.camplanner.model.RunLog
import app.camplanner.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

class StreaksAndFitnessTest {

    private val today = LocalDate.parse("2026-10-23")

    private fun statuses(vararg s: DayStatus): (LocalDate) -> DayStatus = { d ->
        val back = java.time.temporal.ChronoUnit.DAYS.between(d, today).toInt()
        s.getOrElse(s.size - 1 - back) { SKIP }
    }

    @Test
    fun `streak counts back to the last miss, skipping rest days`() {
        // oldest .. today
        val status = statuses(MET, MISSED, MET, SKIP, MET, MET)
        assertEquals(3, Streaks.current(today, today.minusDays(30), status))
    }

    @Test
    fun `today not yet met does not break the streak`() {
        assertEquals(2, Streaks.current(today, today.minusDays(30), statuses(MISSED, MET, MET, MISSED)))
    }

    @Test
    fun `best streak ignores skips`() {
        val status = statuses(MET, MET, MISSED, MET, SKIP, MET, MET, MISSED)
        assertEquals(3, Streaks.best(today.minusDays(7), today, status))
    }

    private fun ex(id: Long, key: String?, target: Int, active: Boolean = true, unit: ExerciseUnit = ExerciseUnit.REPS) =
        Exercise(id, key, key ?: "custom", unit, ExerciseGroup.UPPER, active, target, id.toInt())

    private fun set(exId: Long, amount: Int, date: LocalDate = today) =
        ExerciseSet(0, exId, date, amount, date.atStartOfDay(LONDON).toInstant())

    private val pushups = ex(1, "pushups", 100)
    private val pullups = ex(2, "pullups", 100)
    private val plank = ex(3, "plank", 90, unit = ExerciseUnit.SECONDS)
    private val inactive = ex(4, "crunches", 40, active = false)

    @Test
    fun `progress sums sets per exercise and reports remaining`() {
        val p = Fitness.progress(listOf(pushups, pullups, plank, inactive), listOf(set(1, 20), set(1, 10), set(2, 100)), today)
        assertEquals(listOf(1L, 2L, 3L), p.map { it.exercise.id })
        assertEquals(70, p[0].remaining)
        assertTrue(p[1].complete)
        assertEquals(0f, p[2].fraction)
    }

    @Test
    fun `fitness day status`() {
        val all = Fitness.progress(listOf(pushups), listOf(set(1, 100)), today)
        val some = Fitness.progress(listOf(pushups), listOf(set(1, 60)), today)
        assertEquals(MET, Fitness.dayStatus(today, emptySet(), all))
        assertEquals(MISSED, Fitness.dayStatus(today, emptySet(), some))
        assertEquals(SKIP, Fitness.dayStatus(today, setOf(today.dayOfWeek), some))
    }

    @Test
    fun `midday nudge when push-ups or pull-ups below half`() {
        assertTrue(Fitness.needsMiddayNudge(Fitness.progress(listOf(pushups, pullups), listOf(set(1, 60), set(2, 40)), today)))
        assertFalse(Fitness.needsMiddayNudge(Fitness.progress(listOf(pushups, pullups), listOf(set(1, 50), set(2, 50)), today)))
    }

    @Test
    fun `pace and duration formatting`() {
        assertEquals(Duration.ofSeconds(324), Fitness.pace(5000, 1620))
        assertEquals("5:24", Fitness.formatPace(Duration.ofSeconds(324)))
        assertNull(Fitness.pace(0, 100))
        assertEquals("1:05:10", Fitness.formatDuration(3910))
        assertEquals(3910, Fitness.parseDuration("1:05:10"))
        assertEquals(1620, Fitness.parseDuration("27"))
        assertEquals(1630, Fitness.parseDuration("27:10"))
        assertNull(Fitness.parseDuration("abc"))
    }

    @Test
    fun `stats aggregate reading, homework, reps and runs`() {
        val maths = Subject(1, "Maths", 0, isAcademic = true)
        val physics = Subject(2, "Physics", 1, isAcademic = true)
        val club = Subject(3, "Chess Club", 2, isAcademic = false)
        val events = (0L..4L).flatMap { back ->
            val d = today.minusDays(back)
            listOf(
                event(d.atTime(9, 0).atZone(LONDON).toInstant(), subjectId = 1),
                event(d.atTime(11, 0).atZone(LONDON).toInstant(), subjectId = if (back % 2 == 0L) 2 else 3),
            )
        }
        val reading = listOf(
            ReadingLog(today, 1, 25), ReadingLog(today, 2, 30),
            ReadingLog(today.minusDays(1), 1, 20),
            ReadingLog(today.minusDays(2), 1, 22), ReadingLog(today.minusDays(2), 2, 10), // physics short
        )
        val snapshot = StatsCalculator.compute(
            StatsInput(
                today = today,
                now = today.atTime(20, 0).atZone(LONDON).toInstant(),
                zone = LONDON,
                settings = AppSettings(restDays = setOf(DayOfWeek.SUNDAY)),
                subjects = listOf(maths, physics, club),
                events = events,
                reading = reading,
                assignments = listOf(
                    Assignment(1, "Sheet 1", 1, "", today.minusDays(1).atStartOfDay(LONDON).toInstant(), done = true),
                    Assignment(2, "Sheet 2", 1, "", today.minusDays(1).atStartOfDay(LONDON).toInstant(), done = false),
                    Assignment(3, "Sheet 3", 1, "", today.plusDays(3).atStartOfDay(LONDON).toInstant(), done = false),
                ),
                exercises = listOf(pushups, plank),
                sets = listOf(set(1, 100), set(3, 90), set(1, 30, today.minusDays(1))),
                runs = listOf(RunLog(1, today, 5000, 1620), RunLog(2, today.minusDays(30), 3000, 1000)),
            ),
        )
        val bySubject = snapshot.readingBySubject.associateBy { it.subject.id }
        assertEquals(setOf(1L, 2L), bySubject.keys)
        assertEquals(67, bySubject.getValue(1).pagesWeek)
        assertEquals(3, bySubject.getValue(1).streak)
        assertEquals(1, bySubject.getValue(2).streak) // missed two days ago, skipped yesterday
        assertEquals(2, snapshot.readingStreak) // today and yesterday; two days ago physics fell short
        assertEquals(1, snapshot.assignmentsCompleted)
        assertEquals(1, snapshot.assignmentsOverdue)
        assertEquals(1, snapshot.fitnessStreak)
        assertEquals(130, snapshot.repsWeek) // plank seconds are not reps
        assertEquals(5.0, snapshot.runKmWeek, 1e-9)
        assertEquals(14, snapshot.fitnessRecent.size)
    }
}

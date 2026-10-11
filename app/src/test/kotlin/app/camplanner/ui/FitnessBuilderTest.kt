package app.camplanner.ui

import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.model.AppSettings
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.RunLog
import app.camplanner.ui.fitness.FitnessBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FitnessBuilderTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 10, 13)
    private val pushUps = Exercise(1, "pushups", "Push-ups", ExerciseUnit.REPS, ExerciseGroup.UPPER, true, 100, 0)
    private val plank = Exercise(2, "plank", "Plank", ExerciseUnit.SECONDS, ExerciseGroup.ABS, true, 90, 1)

    private fun set(id: Long, exercise: Long, amount: Int, date: LocalDate = today, minute: Long = id) =
        ExerciseSet(id, exercise, date, amount, date.atTime(12, 0).plusMinutes(minute).atZone(zone).toInstant())

    @Test
    fun countersSplitMainFromOthersAndCorrectionsCount() {
        val s = FitnessBuilder.build(
            today, today, listOf(pushUps, plank), listOf(set(1, 1, 50), set(2, 1, -10), set(3, 2, 45)), emptyList(), AppSettings(), zone,
        )
        assertEquals(listOf("Push-ups"), s.main.map { it.name })
        assertEquals(40, s.main.single().done)
        assertEquals(listOf("Plank"), s.others.map { it.name })
        assertEquals(15, s.others.single().step)
        // Newest first, and the correction is listed so it can be removed too.
        assertEquals(listOf(3L, 2L, 1L), s.log.map { it.id })
    }

    @Test
    fun anEarlierDayShowsThatDaysSets() {
        val yesterday = today.minusDays(1)
        val s = FitnessBuilder.build(yesterday, today, listOf(pushUps), listOf(set(1, 1, 100, yesterday), set(2, 1, 20)), emptyList(), AppSettings(), zone)
        assertEquals(100, s.main.single().done)
        assertEquals(listOf(1L), s.log.map { it.id })
    }

    @Test
    fun streakAndMoons() {
        val sets = (0L..2L).map { set(it + 1, 1, 100, today.minusDays(it)) }
        val s = FitnessBuilder.build(today, today, listOf(pushUps), sets, emptyList(), AppSettings(), zone)
        assertEquals(3, s.streak)
        assertEquals(MoonPhase.FULL, s.recent.last().second)
        assertEquals(MoonPhase.NEW, s.recent.first().second)
    }

    @Test
    fun runsThisWeekAndTheLastOne() {
        val runs = listOf(RunLog(1, today.minusDays(1), 5_000, 1_650), RunLog(2, today.minusDays(9), 8_000, 2_700))
        val s = FitnessBuilder.build(today, today, listOf(pushUps), emptyList(), runs, AppSettings(), zone)
        assertEquals(5.0, s.weekKm, 0.001)
        assertEquals("Mon 12 Oct · 5.0 km in 27:30 · 5:30 /km", s.lastRun)
    }
}

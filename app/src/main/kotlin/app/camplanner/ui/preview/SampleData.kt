package app.camplanner.ui.preview

import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.designsystem.components.StarState
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.TravelMode
import app.camplanner.ui.checkin.CheckInUiState
import app.camplanner.ui.checkin.FitnessGap
import app.camplanner.ui.checkin.HomeworkEntry
import app.camplanner.ui.checkin.ReadingEntry
import app.camplanner.ui.fitness.ExerciseLine
import app.camplanner.ui.fitness.FitnessUiState
import app.camplanner.ui.fitness.RunSummary
import app.camplanner.ui.today.TodayItem
import app.camplanner.ui.today.TodayUiState
import java.time.LocalDate
import java.time.LocalTime

/** A plausible Michaelmas Tuesday, used by previews and the design-review build. */
object SampleData {
    val date: LocalDate = LocalDate.of(2026, 10, 20)

    val today = TodayUiState(
        date = date,
        morningAlarm = LocalTime.of(7, 30),
        checkIn = LocalTime.of(18, 30),
        items = listOf(
            TodayItem.Event(
                key = "e1", id = 1, title = "Vectors & Matrices", subject = "Mathematics", subjectColor = 1,
                start = LocalTime.of(9, 0), end = LocalTime.of(10, 0), location = "Mill Lane Lecture Rooms",
                state = StarState.PAST, mode = TravelMode.WALK, travelMinutes = 8, leaveAt = LocalTime.of(8, 47),
            ),
            TodayItem.Gap("g1", 60),
            TodayItem.Event(
                key = "e2", id = 2, title = "Analysis I", subject = "Mathematics", subjectColor = 1,
                start = LocalTime.of(11, 0), end = LocalTime.of(12, 0), location = "Mill Lane Lecture Rooms",
                state = StarState.CURRENT, mode = TravelMode.WALK, travelMinutes = null, leaveAt = null,
            ),
            TodayItem.Now("now", LocalTime.of(11, 20)),
            TodayItem.Event(
                key = "e3", id = 3, title = "Oscillations Practical", subject = "Physics", subjectColor = 3,
                start = LocalTime.of(14, 0), end = LocalTime.of(17, 0), location = "Cavendish Laboratory",
                state = StarState.FUTURE, mode = TravelMode.BIKE, travelMinutes = 10, leaveAt = LocalTime.of(13, 45),
                note = "Bring lab book and calculator",
            ),
            TodayItem.Event(
                key = "e4", id = 4, title = "Probability Supervision", subject = "Mathematics", subjectColor = 1,
                start = LocalTime.of(17, 30), end = LocalTime.of(18, 0), location = "Trinity College, Great Court",
                state = StarState.FUTURE, mode = TravelMode.BIKE, travelMinutes = 9, leaveAt = LocalTime.of(17, 16),
            ),
        ),
    )

    val checkIn = CheckInUiState(
        date = date,
        readingMin = 20,
        readingMax = 50,
        reading = listOf(
            ReadingEntry(1, "Mathematics", 1, "32"),
            ReadingEntry(3, "Physics", 3, "12"),
        ),
        homework = listOf(
            HomeworkEntry(1, "Oscillations lab write-up", "Physics", 3, date, done = true),
            HomeworkEntry(2, "Example Sheet 2", "Mathematics", 1, date.plusDays(1), done = false),
            HomeworkEntry(3, "Essay plan: The Copernican turn", "History & Philosophy of Science", 4, date.plusDays(3), done = false),
        ),
        fitness = listOf(
            FitnessGap(2, "Pull-ups", ExerciseUnit.REPS, 60, 100),
            FitnessGap(3, "Plank", ExerciseUnit.SECONDS, 45, 90),
            FitnessGap(7, "Squats", ExerciseUnit.REPS, 30, 60),
        ),
        isRestDay = false,
    )

    val fitness = FitnessUiState(
        date = date,
        isRestDay = false,
        main = listOf(
            ExerciseLine(1, "Push-ups", ExerciseUnit.REPS, 64, 100),
            ExerciseLine(2, "Pull-ups", ExerciseUnit.REPS, 40, 100),
        ),
        selectedMainId = 1,
        customAmount = "",
        others = listOf(
            ExerciseLine(3, "Plank", ExerciseUnit.SECONDS, 45, 90),
            ExerciseLine(4, "Leg raises", ExerciseUnit.REPS, 30, 30),
            ExerciseLine(7, "Squats", ExerciseUnit.REPS, 30, 60),
            ExerciseLine(10, "Glute bridges", ExerciseUnit.REPS, 10, 40),
        ),
        run = RunSummary(
            weekKm = 11.2, targetKm = 15.0,
            lastRunDate = "Sun 18 Oct", lastRunStats = "5.0 km  ·  27:00  ·  5:24 /km",
        ),
        recent = listOf(
            MoonPhase.FULL, MoonPhase.FULL, MoonPhase.NEW, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.HALF,
            MoonPhase.FULL, MoonPhase.NEW, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.HALF, MoonPhase.CRESCENT,
        ),
        streakDays = 4,
    )
}

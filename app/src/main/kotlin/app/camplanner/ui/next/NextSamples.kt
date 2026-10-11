package app.camplanner.ui.next

import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.designsystem.kit.DayCell
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.TravelMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Sample data for the redesign mockups: a Michaelmas Tuesday at 11:20. */
object NextSamples {
    val date: LocalDate = LocalDate.of(2026, 10, 20)

    private val va = ScheduleRow(1, LocalTime.of(9, 0), LocalTime.of(10, 0), "Vectors & Matrices", "Mill Lane Lecture Rooms", 1, When.PAST, repeats = true)
    private val an = ScheduleRow(2, LocalTime.of(11, 0), LocalTime.of(12, 0), "Analysis I", "Mill Lane Lecture Rooms", 1, When.NOW, repeats = true)
    private val ph = ScheduleRow(3, LocalTime.of(14, 0), LocalTime.of(17, 0), "Oscillations Practical", "Cavendish Laboratory", 3, When.LATER, repeats = true, leave = "Leave 13:45 · bike 10 min")
    private val su = ScheduleRow(4, LocalTime.of(17, 30), LocalTime.of(18, 0), "Probability supervision", "Trinity College, Great Court", 1, When.LATER, leave = "Leave 17:16 · bike 9 min")
    private val gym = ScheduleRow(5, LocalTime.of(19, 0), LocalTime.of(20, 0), "Climbing club", "Kelsey Kerridge", 5, When.LATER, repeats = true)

    val today = TodayNextState(
        date = date,
        termLine = "Michaelmas · Week 2",
        hero = Hero(
            nowTitle = "Analysis I", nowUntil = LocalTime.of(12, 0), nowProgress = 0.33f, nowPlace = "Mill Lane Lecture Rooms",
            next = ph, nextIn = "at 14:00", nextLeaveAt = LocalTime.of(13, 45), nextTravelMinutes = 10, nextMode = TravelMode.BIKE,
        ),
        schedule = listOf(va, an, ph, su, gym),
        alarmTomorrow = LocalTime.of(7, 30),
        alarmSkipped = false,
        checkIn = LocalTime.of(20, 30),
        pushUps = 64 to 100,
        dueSoon = listOf(
            DueRow(1, "Oscillations lab write-up", "Today 23:59 · Physics", overdue = false, done = false),
            DueRow(2, "Example Sheet 2", "Tomorrow 12:00 · Mathematics", overdue = false, done = false),
        ),
        sunrise = LocalTime.of(7, 32), sunset = LocalTime.of(17, 55),
        moonName = "Waxing gibbous", moonIllumination = 0.72f, moonWaxing = true,
    )

    val calendar = CalendarNextState(
        today = date,
        selected = date,
        termLine = "Michaelmas · Week 2",
        week = (0L..6L).map { i ->
            val d = LocalDate.of(2026, 10, 19).plusDays(i)
            DayCell(d, events = listOf(4, 5, 3, 4, 2, 0, 1)[i.toInt()], isToday = d == date)
        },
        dayRows = listOf(va, an, ph, su, gym),
    )

    val editor = EventEditorState(
        isNew = true,
        title = "Climbing club",
        date = date,
        start = LocalTime.of(19, 0),
        end = LocalTime.of(20, 30),
        repeat = RepeatChoice.WEEKLY,
        days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        ends = EndsChoice.ON_DATE,
        until = LocalDate.of(2026, 12, 4),
        count = null,
        location = "Kelsey Kerridge Sports Centre",
        mode = TravelMode.BIKE,
        alertMinutes = 30,
        note = "",
    )

    val fitness = FitnessNextState(
        date = date,
        restDay = false,
        main = listOf(
            Counter(1, "Push-ups", ExerciseUnit.REPS, 64, 100),
            Counter(2, "Pull-ups", ExerciseUnit.REPS, 40, 100),
        ),
        others = listOf(
            Counter(3, "Plank", ExerciseUnit.SECONDS, 45, 90),
            Counter(4, "Leg raises", ExerciseUnit.REPS, 30, 30),
            Counter(7, "Squats", ExerciseUnit.REPS, 30, 60),
            Counter(10, "Glute bridges", ExerciseUnit.REPS, 10, 40),
        ),
        log = listOf(
            LoggedSet(11, "Push-ups", 20, ExerciseUnit.REPS, LocalTime.of(10, 41)),
            LoggedSet(10, "Pull-ups", 10, ExerciseUnit.REPS, LocalTime.of(8, 30)),
            LoggedSet(9, "Push-ups", 24, ExerciseUnit.REPS, LocalTime.of(8, 12)),
            LoggedSet(8, "Plank", 45, ExerciseUnit.SECONDS, LocalTime.of(8, 5)),
        ),
        weekKm = 11.2,
        targetKm = 15.0,
        lastRun = "Sun 18 Oct · 5.0 km · 27:00 · 5:24/km",
        recent = listOf(
            MoonPhase.FULL, MoonPhase.FULL, MoonPhase.NEW, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.HALF,
            MoonPhase.FULL, MoonPhase.NEW, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.FULL, MoonPhase.HALF, MoonPhase.CRESCENT,
        ),
        streak = 4,
    )
}

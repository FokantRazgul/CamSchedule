package app.camplanner.ui.fitness

import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.domain.DayStatus
import app.camplanner.domain.Fitness
import app.camplanner.domain.Periods
import app.camplanner.domain.Streaks
import app.camplanner.model.AppSettings
import app.camplanner.model.BuiltInExercises
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.RunLog
import app.camplanner.ui.format.Formats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Counter(val id: Long, val name: String, val unit: ExerciseUnit, val done: Int, val target: Int) {
    val remaining: Int get() = (target - done).coerceAtLeast(0)
    val fraction: Float get() = if (target <= 0) 1f else (done / target.toFloat()).coerceIn(0f, 1f)
    val suffix: String get() = if (unit == ExerciseUnit.SECONDS) " s" else ""
    /** The quick-add step: 15 seconds for holds, 5 reps otherwise. */
    val step: Int get() = if (unit == ExerciseUnit.SECONDS) 15 else 5
}

data class LoggedSet(val id: Long, val exercise: String, val amount: Int, val unit: ExerciseUnit, val at: LocalTime)

data class FitnessState(
    val date: LocalDate,
    val today: LocalDate,
    val restDay: Boolean,
    val main: List<Counter>,
    val others: List<Counter>,
    val log: List<LoggedSet>,
    val weekKm: Double,
    val targetKm: Double,
    val lastRun: String?,
    val recent: List<Pair<LocalDate, MoonPhase>>,
    val streak: Int,
)

object FitnessBuilder {
    private val mainKeys = setOf(BuiltInExercises.PUSH_UPS, BuiltInExercises.PULL_UPS)

    fun status(exercises: List<Exercise>, sets: List<ExerciseSet>, restDays: Set<DayOfWeek>): (LocalDate) -> DayStatus {
        val byDay = sets.groupBy { it.date }
        return { d -> Fitness.dayStatus(d, restDays, Fitness.progress(exercises, byDay[d].orEmpty(), d)) }
    }

    fun moon(status: DayStatus, isToday: Boolean): MoonPhase = when {
        status == DayStatus.MET -> MoonPhase.FULL
        status == DayStatus.SKIP -> MoonPhase.HALF
        isToday -> MoonPhase.CRESCENT
        else -> MoonPhase.NEW
    }

    fun build(
        date: LocalDate,
        today: LocalDate,
        exercises: List<Exercise>,
        sets: List<ExerciseSet>,
        runs: List<RunLog>,
        settings: AppSettings,
        zone: ZoneId,
    ): FitnessState {
        val progress = Fitness.progress(exercises, sets, date).map {
            Counter(it.exercise.id, it.exercise.name, it.exercise.unit, it.done, it.target)
        }
        val mainIds = exercises.filter { it.builtInKey in mainKeys }.map { it.id }.toSet()
        val names = exercises.associateBy { it.id }
        val week = Periods.week(date, settings.weekStart)
        val last = runs.maxWithOrNull(compareBy({ it.date }, { it.id }))
        val status = status(exercises, sets, settings.restDays)
        return FitnessState(
            date = date,
            today = today,
            restDay = date.dayOfWeek in settings.restDays,
            main = progress.filter { it.id in mainIds },
            others = progress.filter { it.id !in mainIds },
            log = sets.filter { it.date == date }
                .sortedByDescending { it.loggedAt }
                .mapNotNull { s ->
                    val e = names[s.exerciseId] ?: return@mapNotNull null
                    LoggedSet(s.id, e.name, s.amount, e.unit, s.loggedAt.atZone(zone).toLocalTime())
                },
            weekKm = runs.filter { it.date in week }.sumOf { it.distanceMeters } / 1000.0,
            targetKm = settings.weeklyRunTargetKm,
            lastRun = last?.let(::describeRun),
            recent = Streaks.recent(today, 14, status).map { (d, s) -> d to moon(s, d == today) },
            streak = Streaks.current(today, sets.minOfOrNull { it.date } ?: today, status),
        )
    }

    /** "Sat 10 Oct · 5.2 km in 27:30 · 5:17 /km" */
    fun describeRun(r: RunLog): String {
        val pace = Fitness.pace(r.distanceMeters, r.durationSeconds)?.let { " · ${Fitness.formatPace(it)} /km" } ?: ""
        return "${Formats.shortDate(r.date)} · ${Formats.km(r.distanceMeters)} km in ${Fitness.formatDuration(r.durationSeconds)}$pace"
    }
}

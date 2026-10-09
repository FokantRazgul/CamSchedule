package app.camplanner.domain

import app.camplanner.model.BuiltInExercises
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseSet
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToLong

data class ExerciseProgress(val exercise: Exercise, val done: Int) {
    val target: Int get() = exercise.dailyTarget
    val remaining: Int get() = (target - done).coerceAtLeast(0)
    val fraction: Float get() = if (target <= 0) 1f else (done.toFloat() / target).coerceIn(0f, 1f)
    val complete: Boolean get() = done >= target
}

object Fitness {

    /** Progress for every active exercise on [date], in display order. */
    fun progress(exercises: List<Exercise>, sets: List<ExerciseSet>, date: LocalDate): List<ExerciseProgress> {
        val doneById = sets.filter { it.date == date }.groupBy { it.exerciseId }.mapValues { (_, s) -> s.sumOf { it.amount } }
        return exercises
            .filter { it.active && it.dailyTarget > 0 }
            .sortedBy { it.sortOrder }
            .map { ExerciseProgress(it, doneById[it.id] ?: 0) }
    }

    fun dayStatus(date: LocalDate, restDays: Set<DayOfWeek>, progress: List<ExerciseProgress>): DayStatus = when {
        date.dayOfWeek in restDays -> DayStatus.SKIP
        progress.isEmpty() -> DayStatus.SKIP
        progress.all { it.complete } -> DayStatus.MET
        else -> DayStatus.MISSED
    }

    /** True when push-ups or pull-ups are still below half of today's target. */
    fun needsMiddayNudge(progress: List<ExerciseProgress>): Boolean =
        progress
            .filter { it.exercise.builtInKey == BuiltInExercises.PUSH_UPS || it.exercise.builtInKey == BuiltInExercises.PULL_UPS }
            .any { it.done * 2 < it.target }

    /** Time per kilometre, rounded to the second; null for a zero distance. */
    fun pace(distanceMeters: Int, durationSeconds: Int): Duration? {
        if (distanceMeters <= 0 || durationSeconds <= 0) return null
        return Duration.ofSeconds((durationSeconds * 1000.0 / distanceMeters).roundToLong())
    }

    /** "5:24" (per km). */
    fun formatPace(pace: Duration): String {
        val s = pace.seconds
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** "45:10" or "1:05:10". */
    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Parses "45", "45:10" or "1:05:10" into seconds; a bare number is minutes. */
    fun parseDuration(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.isEmpty() || parts.size > 3) return null
        val nums = parts.map { it.trim().toIntOrNull() ?: return null }
        if (nums.any { it < 0 }) return null
        return when (nums.size) {
            1 -> nums[0] * 60
            2 -> nums[0] * 60 + nums[1]
            else -> nums[0] * 3600 + nums[1] * 60 + nums[2]
        }.takeIf { it > 0 }
    }
}

package app.camplanner.domain

import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseSet
import app.camplanner.model.Subject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class CheckInPlan(
    val date: LocalDate,
    /** Academic subjects that met today; each gets a "pages read" field. */
    val readingSubjects: List<Subject>,
    /** Assignments due today or in the next [CheckIn.HOMEWORK_WINDOW_DAYS] days, earliest first. */
    val homework: List<Assignment>,
    /** Active exercises still short of today's target; empty on a rest day. */
    val fitnessShortfall: List<ExerciseProgress>,
    val isRestDay: Boolean,
)

object CheckIn {
    const val HOMEWORK_WINDOW_DAYS = 3L

    fun plan(
        date: LocalDate,
        zone: ZoneId,
        subjects: List<Subject>,
        events: List<CalendarEvent>,
        assignments: List<Assignment>,
        exercises: List<Exercise>,
        sets: List<ExerciseSet>,
        restDays: Set<DayOfWeek>,
    ): CheckInPlan {
        val academic = subjects.filter { it.isAcademic }
        val metToday = Reading.subjectsOn(date, events, academic.map { it.id }.toSet(), zone)
        val lastDay = date.plusDays(HOMEWORK_WINDOW_DAYS)
        val homework = assignments
            .filter {
                val due = it.deadline.atZone(zone).toLocalDate()
                !due.isBefore(date) && !due.isAfter(lastDay)
            }
            .sortedBy { it.deadline }
        val rest = date.dayOfWeek in restDays
        return CheckInPlan(
            date = date,
            readingSubjects = academic.filter { it.id in metToday }.sortedBy { it.name },
            homework = homework,
            fitnessShortfall = if (rest) emptyList() else Fitness.progress(exercises, sets, date).filter { !it.complete },
            isRestDay = rest,
        )
    }
}

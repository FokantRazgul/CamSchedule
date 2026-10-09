package app.camplanner.domain

import app.camplanner.model.AppSettings
import app.camplanner.model.Assignment
import app.camplanner.model.CalendarEvent
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.ReadingLog
import app.camplanner.model.RunLog
import app.camplanner.model.Subject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class Period(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
}

object Periods {
    fun week(date: LocalDate, weekStart: java.time.DayOfWeek): Period {
        val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
        return Period(start, start.plusDays(6))
    }

    /** The configured term, or the span of the timetable if no dates were set. */
    fun term(settings: AppSettings, firstEventDate: LocalDate?, lastEventDate: LocalDate?, today: LocalDate): Period {
        val start = settings.termStart ?: firstEventDate ?: today
        val end = settings.termEnd ?: lastEventDate ?: today
        return if (end.isBefore(start)) Period(start, start) else Period(start, end)
    }
}

object Reading {
    /** Academic subjects that had at least one timed event on [date]. */
    fun subjectsOn(date: LocalDate, events: List<CalendarEvent>, academicIds: Set<Long>, zone: ZoneId): Set<Long> =
        events.asSequence()
            .filter { !it.allDay && it.subjectId in academicIds && it.start.atZone(zone).toLocalDate() == date }
            .mapNotNull { it.subjectId }
            .toSet()

    fun subjectStatus(pages: Int?, minPages: Int, scheduled: Boolean): DayStatus = when {
        !scheduled -> DayStatus.SKIP
        (pages ?: 0) >= minPages -> DayStatus.MET
        else -> DayStatus.MISSED
    }

    /** A day counts when every academic subject that met that day reached the minimum. */
    fun dayStatus(scheduled: Set<Long>, pagesFor: (Long) -> Int, minPages: Int): DayStatus = when {
        scheduled.isEmpty() -> DayStatus.SKIP
        scheduled.all { pagesFor(it) >= minPages } -> DayStatus.MET
        else -> DayStatus.MISSED
    }
}

data class StatsInput(
    val today: LocalDate,
    val now: Instant,
    val zone: ZoneId,
    val settings: AppSettings,
    val subjects: List<Subject>,
    val events: List<CalendarEvent>,
    val reading: List<ReadingLog>,
    val assignments: List<Assignment>,
    val exercises: List<Exercise>,
    val sets: List<ExerciseSet>,
    val runs: List<RunLog>,
)

data class SubjectReading(
    val subject: Subject,
    val pagesWeek: Int,
    val pagesTerm: Int,
    val streak: Int,
)

data class StatsSnapshot(
    val week: Period,
    val term: Period,
    val readingBySubject: List<SubjectReading>,
    val readingStreak: Int,
    val readingRecent: List<Pair<LocalDate, DayStatus>>,
    val assignmentsCompleted: Int,
    val assignmentsOverdue: Int,
    val fitnessStreak: Int,
    val fitnessBestStreak: Int,
    val fitnessRecent: List<Pair<LocalDate, DayStatus>>,
    val repsWeek: Int,
    val repsTerm: Int,
    val runKmWeek: Double,
    val runKmTerm: Double,
)

object StatsCalculator {

    const val RECENT_DAYS = 14

    fun compute(input: StatsInput): StatsSnapshot {
        val s = input.settings
        val zone = input.zone
        val timedDates = input.events.filter { !it.allDay }.map { it.start.atZone(zone).toLocalDate() }
        val week = Periods.week(input.today, s.weekStart)
        val term = Periods.term(s, timedDates.minOrNull(), timedDates.maxOrNull(), input.today)

        val academic = input.subjects.filter { it.isAcademic }
        val academicIds = academic.map { it.id }.toSet()
        val pages = input.reading.associateBy({ it.date to it.subjectId }, { it.pages })
        val scheduledByDate: Map<LocalDate, Set<Long>> = input.events.asSequence()
            .filter { !it.allDay && it.subjectId in academicIds }
            .groupBy({ it.start.atZone(zone).toLocalDate() }, { it.subjectId!! })
            .mapValues { it.value.toSet() }
        val earliest = listOfNotNull(
            scheduledByDate.keys.minOrNull(),
            input.reading.minOfOrNull { it.date },
            input.sets.minOfOrNull { it.date },
        ).minOrNull() ?: input.today

        val readingStatus: (LocalDate) -> DayStatus = { d ->
            Reading.dayStatus(scheduledByDate[d].orEmpty(), { id -> pages[d to id] ?: 0 }, s.readingMinPages)
        }
        val readingBySubject = academic.map { subject ->
            val logs = input.reading.filter { it.subjectId == subject.id }
            SubjectReading(
                subject = subject,
                pagesWeek = logs.filter { it.date in week }.sumOf { it.pages },
                pagesTerm = logs.filter { it.date in term }.sumOf { it.pages },
                streak = Streaks.current(input.today, earliest) { d ->
                    Reading.subjectStatus(pages[d to subject.id], s.readingMinPages, subject.id in scheduledByDate[d].orEmpty())
                },
            )
        }

        val setsByDate = input.sets.groupBy { it.date }
        val fitnessStatus: (LocalDate) -> DayStatus = { d ->
            Fitness.dayStatus(d, s.restDays, Fitness.progress(input.exercises, setsByDate[d].orEmpty(), d))
        }
        val repExerciseIds = input.exercises.filter { it.unit == ExerciseUnit.REPS }.map { it.id }.toSet()
        fun reps(p: Period) = input.sets.filter { it.exerciseId in repExerciseIds && it.date in p }.sumOf { it.amount }
        fun km(p: Period) = input.runs.filter { it.date in p }.sumOf { it.distanceMeters } / 1000.0

        return StatsSnapshot(
            week = week,
            term = term,
            readingBySubject = readingBySubject,
            readingStreak = Streaks.current(input.today, earliest, readingStatus),
            readingRecent = Streaks.recent(input.today, RECENT_DAYS, readingStatus),
            assignmentsCompleted = input.assignments.count { it.done },
            assignmentsOverdue = input.assignments.count { !it.done && it.deadline.isBefore(input.now) },
            fitnessStreak = Streaks.current(input.today, earliest, fitnessStatus),
            fitnessBestStreak = Streaks.best(earliest, input.today, fitnessStatus),
            fitnessRecent = Streaks.recent(input.today, RECENT_DAYS, fitnessStatus),
            repsWeek = reps(week),
            repsTerm = reps(term),
            runKmWeek = km(week),
            runKmTerm = km(term),
        )
    }
}

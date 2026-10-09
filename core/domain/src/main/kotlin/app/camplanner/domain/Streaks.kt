package app.camplanner.domain

import java.time.LocalDate

enum class DayStatus {
    /** Target met. */
    MET,

    /** Target applied and was not met. */
    MISSED,

    /** Target didn't apply (rest day, no academic subject that day, ...). Neither extends nor breaks a streak. */
    SKIP,
}

object Streaks {

    /**
     * Length of the streak ending today. Today counts if already met; if it's not met yet it is
     * still in progress and doesn't break the streak.
     *
     * @param earliest no data exists before this day; stops the walk back
     */
    fun current(today: LocalDate, earliest: LocalDate, status: (LocalDate) -> DayStatus): Int {
        var count = 0
        var day = today
        while (!day.isBefore(earliest)) {
            when (status(day)) {
                DayStatus.MET -> count++
                DayStatus.MISSED -> if (day != today) return count
                DayStatus.SKIP -> Unit
            }
            day = day.minusDays(1)
        }
        return count
    }

    /** Longest run of MET days in [from]..[to], with SKIP days transparent. */
    fun best(from: LocalDate, to: LocalDate, status: (LocalDate) -> DayStatus): Int {
        var best = 0
        var run = 0
        var day = from
        while (!day.isAfter(to)) {
            when (status(day)) {
                DayStatus.MET -> { run++; best = maxOf(best, run) }
                DayStatus.MISSED -> run = 0
                DayStatus.SKIP -> Unit
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** Statuses for the last [count] days ending at [today], oldest first; used for the moon-phase row. */
    fun recent(today: LocalDate, count: Int, status: (LocalDate) -> DayStatus): List<Pair<LocalDate, DayStatus>> =
        (count - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to status(it) }
}

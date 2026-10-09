package app.camplanner.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Offline almanac: sunrise, sunset and the phase of the moon. Accurate to a minute or two for the
 * sun and a few hours for the moon, which is plenty for a page heading.
 */
object Almanac {

    /** Cambridge, Great St Mary's: the conventional centre of the town. */
    const val CAMBRIDGE_LAT = 52.2053
    const val CAMBRIDGE_LNG = 0.1183

    data class SunTimes(val sunrise: Instant?, val sunset: Instant?)

    /**
     * Sunrise and sunset on [date] (by UTC calendar day, fine away from the poles) using the
     * standard sunrise equation with the usual −0.833° altitude for refraction and the solar disc.
     * Either is null on days when the sun doesn't rise or set.
     */
    fun sunTimes(date: LocalDate, lat: Double, lng: Double): SunTimes {
        val n = (date.toEpochDay() - 10_957).toDouble() // days since 2000-01-01
        val jStar = n - lng / 360.0
        val m = norm360(357.5291 + 0.98560028 * jStar)
        val mRad = m.rad
        val c = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val lambda = norm360(m + c + 180.0 + 102.9372).rad
        val jTransit = 2_451_545.0 + jStar + 0.0053 * sin(mRad) - 0.0069 * sin(2 * lambda)
        val sinDecl = sin(lambda) * sin(23.4397.rad)
        val cosDecl = cos(asin(sinDecl))
        val cosOmega = (sin((-0.833).rad) - sin(lat.rad) * sinDecl) / (cos(lat.rad) * cosDecl)
        if (cosOmega > 1.0 || cosOmega < -1.0) return SunTimes(null, null)
        val omega = acos(cosOmega) * 180.0 / PI
        return SunTimes(julianToInstant(jTransit - omega / 360.0), julianToInstant(jTransit + omega / 360.0))
    }

    enum class MoonPhaseName(val label: String) {
        NEW("New moon"),
        WAXING_CRESCENT("Waxing crescent"),
        FIRST_QUARTER("First quarter"),
        WAXING_GIBBOUS("Waxing gibbous"),
        FULL("Full moon"),
        WANING_GIBBOUS("Waning gibbous"),
        LAST_QUARTER("Last quarter"),
        WANING_CRESCENT("Waning crescent"),
    }

    data class Moon(
        /** Days since the last new moon, 0 until [SYNODIC_MONTH]. */
        val age: Double,
        /** Lit fraction of the disc, 0..1. */
        val illumination: Double,
        val waxing: Boolean,
        val phase: MoonPhaseName,
    )

    const val SYNODIC_MONTH = 29.530588853
    private const val REFERENCE_NEW_MOON_JD = 2_451_550.259722 // 2000-01-06 18:14 UTC

    fun moon(at: Instant): Moon {
        val jd = at.epochSecond / 86_400.0 + 2_440_587.5
        val cycles = (jd - REFERENCE_NEW_MOON_JD) / SYNODIC_MONTH
        val age = (cycles - floor(cycles)) * SYNODIC_MONTH
        val illumination = (1 - cos(2 * PI * age / SYNODIC_MONTH)) / 2
        val index = floor(age / SYNODIC_MONTH * 8 + 0.5).toInt() % 8
        return Moon(age, illumination, age < SYNODIC_MONTH / 2, MoonPhaseName.entries[index])
    }

    private fun julianToInstant(jd: Double): Instant =
        Instant.ofEpochSecond(((jd - 2_440_587.5) * 86_400.0).roundToLong())

    private fun norm360(x: Double) = ((x % 360.0) + 360.0) % 360.0

    private val Double.rad: Double get() = this * PI / 180.0
}

/** Cambridge-style term reckoning: the term is named from its start month, and weeks run Thursday to Wednesday. */
object TermCalendar {

    enum class Term { MICHAELMAS, LENT, EASTER }

    fun termOf(termStart: LocalDate): Term = when (termStart.monthValue) {
        in 9..12 -> Term.MICHAELMAS
        in 1..3 -> Term.LENT
        else -> Term.EASTER
    }

    /**
     * Week number of [date] in a term whose Full Term begins on [termStart]. Week 1 begins on the
     * first Thursday on or after the start; the days before it are week 0. Null outside the term.
     */
    fun weekOf(date: LocalDate, termStart: LocalDate, termEnd: LocalDate?): Int? {
        if (date.isBefore(termStart) || (termEnd != null && date.isAfter(termEnd))) return null
        val firstThursday = termStart.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.THURSDAY))
        if (date.isBefore(firstThursday)) return 0
        return (java.time.temporal.ChronoUnit.DAYS.between(firstThursday, date) / 7 + 1).toInt()
    }

    fun termName(term: Term): String = when (term) {
        Term.MICHAELMAS -> "Michaelmas"
        Term.LENT -> "Lent"
        Term.EASTER -> "Easter"
    }
}

object Roman {
    private val table = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )

    /** 2026 -> "MMXXVI". Zero and negatives have no Roman form and come back as Arabic digits. */
    fun of(value: Int): String {
        if (value <= 0) return value.toString()
        var n = value
        return buildString {
            for ((v, s) in table) while (n >= v) { append(s); n -= v }
        }
    }
}

package app.camplanner.domain

/**
 * Derives a subject name from an event SUMMARY by dropping the session-type suffix, so
 * "Vectors & Matrices - Lecture 5" and "Vectors & Matrices (Supervision)" land in the same subject.
 * Anything this misses can be merged by hand on the Subjects screen.
 */
object SubjectNames {

    private const val TYPES =
        "lectures?|lect|lec|supervisions?|supos?|practicals?|prac|seminars?|examples? ?class(?:es)?|" +
            "class(?:es)?|labs?|laboratory|tutorials?|workshops?|revision|exam"

    private val trailingType = Regex(
        "(?:\\s*[-–—:|,/]\\s*|\\s+|\\s*\\(\\s*)(?:$TYPES)\\.?(?:\\s*(?:no\\.?\\s*)?#?\\d+[a-z]?)?\\s*\\)?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val trailingLectureNumber = Regex("\\s+[LS]\\d{1,2}\\s*$")
    private val trailingSeparator = Regex("\\s*[-–—:|,/]\\s*$")

    fun displayName(summary: String): String {
        var s = summary.trim().replace(Regex("\\s+"), " ")
        while (true) {
            val next = s.replace(trailingType, "").replace(trailingLectureNumber, "").replace(trailingSeparator, "").trim()
            if (next == s || next.isEmpty()) break
            s = next
        }
        return s.ifEmpty { summary.trim() }
    }

    /** Lookup key for the summary → subject alias table. */
    fun key(summary: String): String = displayName(summary).lowercase()
}

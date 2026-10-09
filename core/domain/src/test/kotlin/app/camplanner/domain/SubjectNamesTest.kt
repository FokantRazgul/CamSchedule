package app.camplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectNamesTest {

    @Test
    fun `strips session types and numbering`() {
        val cases = mapOf(
            "Vectors & Matrices - Lecture 5" to "Vectors & Matrices",
            "Vectors & Matrices (Lecture)" to "Vectors & Matrices",
            "Vectors & Matrices: Supervision" to "Vectors & Matrices",
            "Organic Chemistry Practical" to "Organic Chemistry",
            "Analysis I L3" to "Analysis I",
            "Probability – Examples Class 2" to "Probability",
            "Computer Science Tripos Part IA: Algorithms 1 - Lecture 12" to
                "Computer Science Tripos Part IA: Algorithms 1",
            "Physics Lab" to "Physics",
            "  Economics   Seminar #4 " to "Economics",
        )
        cases.forEach { (summary, expected) -> assertEquals(summary, expected, SubjectNames.displayName(summary)) }
    }

    @Test
    fun `leaves ordinary titles alone`() {
        listOf("Matlab", "Theory of Computation", "Class Struggle in Europe", "Laboratory Safety Rules")
            .forEach { assertEquals(it, SubjectNames.displayName(it)) }
    }

    @Test
    fun `never strips a title to nothing`() {
        assertEquals("Lecture", SubjectNames.displayName("Lecture"))
        assertEquals("Supervision 3", SubjectNames.displayName("Supervision 3"))
    }

    @Test
    fun `key is case-insensitive`() {
        assertEquals(SubjectNames.key("Vectors & Matrices - Lecture 5"), SubjectNames.key("vectors & matrices (supervision)"))
    }
}

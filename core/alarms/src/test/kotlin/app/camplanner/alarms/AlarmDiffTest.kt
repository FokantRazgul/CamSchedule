package app.camplanner.alarms

import app.camplanner.domain.AlarmKind
import app.camplanner.domain.PlannedAlarm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AlarmDiffTest {
    private val day = LocalDate.parse("2026-10-20")
    private fun planned(key: String, at: String, kind: AlarmKind = AlarmKind.REMINDER) =
        PlannedAlarm(key, kind, Instant.parse(at), day)
    private fun registered(key: String, at: String, kind: AlarmKind = AlarmKind.REMINDER) =
        Registered(key, kind.name, Instant.parse(at))

    @Test
    fun `unchanged alarms are left alone`() {
        val d = AlarmDiff.diff(listOf(planned("a", "2026-10-20T08:00:00Z")), listOf(registered("a", "2026-10-20T08:00:00Z")))
        assertEquals(emptyList<PlannedAlarm>(), d.toSet)
        assertEquals(emptyList<Registered>(), d.toCancel)
    }

    @Test
    fun `moved alarms are set again, vanished ones cancelled, new ones set`() {
        val d = AlarmDiff.diff(
            planned = listOf(planned("moved", "2026-10-20T09:00:00Z"), planned("new", "2026-10-20T10:00:00Z")),
            registered = listOf(registered("moved", "2026-10-20T08:30:00Z"), registered("gone", "2026-10-20T07:00:00Z")),
        )
        assertEquals(listOf("moved", "new"), d.toSet.map { it.key })
        assertEquals(listOf("gone"), d.toCancel.map { it.key })
    }

    @Test
    fun `kept keys survive even when not planned`() {
        val d = AlarmDiff.diff(emptyList(), listOf(registered("SNOOZE", "2026-10-20T07:39:00Z")), keep = { it == "SNOOZE" })
        assertEquals(emptyList<Registered>(), d.toCancel)
    }
}

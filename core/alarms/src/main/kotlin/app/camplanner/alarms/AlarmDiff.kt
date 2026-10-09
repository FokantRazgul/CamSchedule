package app.camplanner.alarms

import app.camplanner.domain.PlannedAlarm
import java.time.Instant

/** An alarm currently registered with AlarmManager (as remembered in the database). */
data class Registered(val key: String, val kind: String, val triggerAt: Instant)

data class AlarmChanges(val toSet: List<PlannedAlarm>, val toCancel: List<Registered>)

/**
 * Pure reconciliation of the new plan against what is registered: set what is new or has moved,
 * cancel what is no longer planned. Keys in [keep] (the snooze alarm) are never cancelled.
 */
object AlarmDiff {
    fun diff(planned: List<PlannedAlarm>, registered: List<Registered>, keep: (String) -> Boolean = { false }): AlarmChanges {
        val byKey = registered.associateBy { it.key }
        val plannedKeys = planned.map { it.key }.toSet()
        val toSet = planned.filter { p ->
            val r = byKey[p.key]
            r == null || r.triggerAt != p.triggerAt || r.kind != p.kind.name
        }
        val toCancel = registered.filter { it.key !in plannedKeys && !keep(it.key) }
        return AlarmChanges(toSet, toCancel)
    }
}

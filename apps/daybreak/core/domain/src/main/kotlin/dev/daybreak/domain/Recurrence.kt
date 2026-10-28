package dev.daybreak.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A wall-clock rule; each delivery/snooze is a separate occurrence of this rule. */
data class RepeatRule(val time: LocalTime, val days: Set<DayOfWeek>) {
    init { require(days.isNotEmpty()); require(time.second == 0 && time.nano == 0) }

    fun nextAfter(after: Instant, zone: ZoneId): Long {
        val date = after.atZone(zone).toLocalDate()
        for (offset in 0L..7L) {
            val candidateDate = date.plusDays(offset)
            if (candidateDate.dayOfWeek !in days) continue
            // atZone shifts gaps forward and chooses the earlier overlap offset.
            val candidate = candidateDate.atTime(time).atZone(zone).toInstant()
            if (candidate.isAfter(after)) return candidate.toEpochMilli()
        }
        error("A nonempty weekly rule must have a next occurrence")
    }
}

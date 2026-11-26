package com.deelabs.untisplus.workers

import com.deelabs.untisplus.data.cache.CacheRead
import java.time.LocalDateTime

/**
 * Decision produced from cached Info Center state. [Keep] leaves every
 * existing reminder and notification untouched; it is used whenever at least
 * one required cache read could not be trusted.
 */
internal sealed interface EventReminderSyncPlan {
	data object Keep : EventReminderSyncPlan

	data class Apply(
		val reminders: List<EventReminder>,
		val cancelKeys: Set<String>,
	) : EventReminderSyncPlan
}

/**
 * Pure policy shared by the sync worker and its tests. An empty but valid
 * cache replaces reminders (and cancels stale notifications) on purpose; an
 * unavailable cache never does.
 */
internal fun planEventReminderSync(
	reads: List<CacheRead<List<EventReminderCandidate>>>,
	existingDueDates: Map<String, String>,
	now: LocalDateTime,
): EventReminderSyncPlan {
	if (reads.isEmpty() || reads.any { it is CacheRead.Unavailable }) {
		return EventReminderSyncPlan.Keep
	}

	val candidates = reads.flatMap { (it as CacheRead.Value).value }
	return EventReminderSyncPlan.Apply(
		reminders = EventReminderPlan.build(candidates, now),
		cancelKeys = EventReminderPlan.keysToCancel(existingDueDates, candidates),
	)
}

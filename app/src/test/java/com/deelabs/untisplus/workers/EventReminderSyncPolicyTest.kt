package com.deelabs.untisplus.workers

import com.deelabs.untisplus.data.cache.CacheRead
import com.deelabs.untisplus.data.cache.readFreshOrCached
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EventReminderSyncPolicyTest {
	private val now = LocalDateTime.of(2026, 11, 26, 12, 0)
	private val due = LocalDate.of(2026, 11, 28)

	@Test
	fun `cold cache schedules nothing and preserves existing reminders`() {
		val existing = mapOf("eventreminder_1_2_homework_3" to due.toString())

		val plan = planEventReminderSync(listOf(CacheRead.Unavailable), existing, now)

		assertEquals(EventReminderSyncPlan.Keep, plan)
	}

	@Test
	fun `one failed element read preserves reminders for every element`() {
		val keyA = EventReminderPlan.key(1, 2, EventReminderKind.HOMEWORK, 3)
		val reads = listOf(
			CacheRead.Value(listOf(candidate(keyA))),
			CacheRead.Unavailable,
		)

		val plan = planEventReminderSync(reads, mapOf(keyA to due.toString()), now)

		assertEquals(EventReminderSyncPlan.Keep, plan)
	}

	@Test
	fun `legitimately empty cache cancels existing reminders on purpose`() {
		val existing = mapOf(
			"eventreminder_1_2_homework_3" to due.toString(),
			"eventreminder_1_2_exam_4" to due.toString(),
		)

		val plan = planEventReminderSync(listOf(CacheRead.Value(emptyList())), existing, now)

		assertTrue(plan is EventReminderSyncPlan.Apply)
		plan as EventReminderSyncPlan.Apply
		assertTrue(plan.reminders.isEmpty())
		assertEquals(existing.keys, plan.cancelKeys)
	}

	@Test
	fun `valid cached candidates still replace reminders and changed notifications`() = runBlocking {
		val kept = EventReminderPlan.key(1, 2, EventReminderKind.HOMEWORK, 3)
		val moved = EventReminderPlan.key(1, 2, EventReminderKind.HOMEWORK, 4)
		val reads = listOf(
			CacheRead.Value(listOf(candidate(kept), candidate(moved, due.plusDays(1)))),
		)
		val existing = mapOf(kept to due.toString(), moved to due.toString())

		val plan = planEventReminderSync(reads, existing, now)

		assertTrue(plan is EventReminderSyncPlan.Apply)
		plan as EventReminderSyncPlan.Apply
		assertEquals(setOf(moved), plan.cancelKeys)
		assertEquals(listOf(kept, moved), plan.reminders.map { it.key })
	}

	@Test
	fun `stale cache failure keeps reminders instead of replacing them with nothing`() = runBlocking {
		val read = readFreshOrCached<List<EventReminderCandidate>> { _, _ ->
			throw IllegalStateException("expired cache entry")
		}
		val existing = mapOf("eventreminder_1_2_homework_3" to due.toString())

		val plan = planEventReminderSync(listOf(read), existing, now)

		assertEquals(EventReminderSyncPlan.Keep, plan)
	}

	@Test
	fun `cache failure keeps reminders instead of cancelling them`() = runBlocking {
		val read = readFreshOrCached<List<EventReminderCandidate>> { _, _ ->
			throw IllegalStateException("cache read failed")
		}

		val plan = planEventReminderSync(listOf(read), mapOf("old" to due.toString()), now)

		assertEquals(EventReminderSyncPlan.Keep, plan)
	}

	@Test
	fun `per user tags isolate due dates and notification cancellation`() {
		val keyA = EventReminderPlan.key(1, 2, EventReminderKind.HOMEWORK, 3)
		val keyB = EventReminderPlan.key(2, 2, EventReminderKind.HOMEWORK, 3)
		val tags = listOf(
			EventReminderPlan.reminderTag(1),
			EventReminderPlan.keyTag(1, keyA),
			EventReminderPlan.dueTag(1, keyA, due),
			EventReminderPlan.reminderTag(2),
			EventReminderPlan.keyTag(2, keyB),
			EventReminderPlan.dueTag(2, keyB, due.plusDays(1)),
		)

		assertNotEquals(EventReminderPlan.reminderTag(1), EventReminderPlan.reminderTag(2))
		assertEquals(mapOf(keyA to due.toString()), existingDueDatesFromTags(1, tags))
		assertEquals(mapOf(keyB to due.plusDays(1).toString()), existingDueDatesFromTags(2, tags))

		val plan = planEventReminderSync(
			listOf(CacheRead.Value(emptyList())),
			existingDueDatesFromTags(1, tags),
			now,
		)

		assertTrue(plan is EventReminderSyncPlan.Apply)
		assertEquals(setOf(keyA), (plan as EventReminderSyncPlan.Apply).cancelKeys)
	}

	private fun candidate(
		key: String,
		dueDate: LocalDate = due,
	) = EventReminderCandidate(
		key = key,
		kind = EventReminderKind.HOMEWORK,
		entryId = 3,
		dueDate = dueDate,
		label = "label",
	)
}

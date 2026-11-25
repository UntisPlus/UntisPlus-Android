package com.deelabs.untisplus.notifications

import com.deelabs.untisplus.api.model.untis.MessageOfDay
import com.deelabs.untisplus.model.rest.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InfoCenterNotificationDifferTest {
	@Test
	fun `first refresh primes baseline without notifying`() {
		val entries = listOf(
			InfoCenterNotificationEntry("message:1", "sig-a", "Hello", "Body"),
			InfoCenterNotificationEntry("motd:2", "sig-b", "Notice", "Today"),
		)

		val diff = InfoCenterNotificationDiffer.evaluate(
			entries,
			InfoCenterNotificationState(enabled = true, seen = emptyMap(), initialized = false),
		)

		assertTrue(diff.newEntries.isEmpty())
		assertEquals(mapOf("message:1" to "sig-a", "motd:2" to "sig-b"), diff.seen)
	}

	@Test
	fun `updated content notifies once by stable id`() {
		val unchanged = InfoCenterNotificationEntry("message:1", "sig-a", "Hello", "Body")
		val updated = unchanged.copy(signature = "sig-b", text = "Changed body")
		val state = InfoCenterNotificationState(
			enabled = true,
			seen = mapOf("message:1" to "sig-a"),
			initialized = true,
		)

		val first = InfoCenterNotificationDiffer.evaluate(listOf(unchanged, updated), state)
		val repeated = InfoCenterNotificationDiffer.evaluate(listOf(updated), state.copy(seen = first.seen))

		assertEquals(listOf(updated), first.newEntries)
		assertTrue(repeated.newEntries.isEmpty())
		assertEquals(mapOf("message:1" to "sig-b"), repeated.seen)
	}

	@Test
	fun `keys are stable for messages and messages of day`() {
		val messages = listOf(
			Message(id = 7, subject = "A", contentPreview = "Body"),
			Message(id = 7, subject = "A", contentPreview = "Edited"),
		)
		val motd = listOf(MessageOfDay(id = 9, subject = "Notice", body = "<b>Today</b>", attachments = emptyList()))

		val messageKeys = messages.flatMap { listOf(it).toMessageNotificationEntries().map { entry -> entry.key } }.distinct()
		val motdEntry = motd.toMessageOfDayNotificationEntries().single()

		assertEquals(listOf("message:7"), messageKeys)
		assertEquals("motd:9", motdEntry.key)
		assertEquals("Today", motdEntry.text)
	}

	@Test
	fun `entries missing from a refresh are pruned`() {
		val state = InfoCenterNotificationState(
			enabled = true,
			seen = mapOf("message:1" to "old", "message:2" to "gone"),
			initialized = true,
		)

		val diff = InfoCenterNotificationDiffer.evaluate(
			listOf(InfoCenterNotificationEntry("message:1", "old", "A", "B")),
			state,
		)

		assertTrue(diff.newEntries.isEmpty())
		assertEquals(mapOf("message:1" to "old"), diff.seen)
	}
}

package com.deelabs.untisplus.widgets

import com.deelabs.untisplus.api.model.untis.MessageOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDailyMessagesFormatterTest {
	private fun message(
		id: Long = idSequence++,
		subject: String = "",
		body: String = "",
	) = MessageOfDay(id = id, subject = subject, body = body, attachments = emptyList())

	companion object {
		private var idSequence = 1L
	}

	@Test
	fun emptyCache_returnsEmptyStateMessage() {
		val result = formatWidgetDailyMessages(
			messages = emptyList(),
			header = "Class 5a",
			emptyMessage = "No messages today",
		)

		assertEquals("Class 5a", result.header)
		assertEquals("No messages today", result.message)
		assertEquals(emptyList<WidgetDailyMessage>(), result.messages)
	}

	@Test
	fun localMessages_useFallbackSubjectAndKeepBody() {
		val result = formatWidgetDailyMessages(
			messages = listOf(
				message(subject = "  Materials ", body = " Bring a book "),
				message(subject = " ", body = "   "),
			),
			header = "Class 5a",
			unknownSubject = "Message",
		)

		assertNull(result.message)
		assertEquals(2, result.messages.size)
		assertEquals("Materials", result.messages[0].subject)
		assertEquals("Bring a book", result.messages[0].body)
		assertEquals("Message", result.messages[1].subject)
		assertEquals("", result.messages[1].body)
	}

	@Test
	fun tooManyMessages_areCappedAndBodyIsTruncated() {
		val longBody = "word ".repeat(100)
		val result = formatWidgetDailyMessages(
			messages = listOf(
				message(subject = "One", body = longBody),
				message(subject = "Two"),
				message(subject = "Three"),
				message(subject = "Four"),
			),
			maxMessages = 2,
			maxBodyLength = 20,
		)

		assertEquals(2, result.messages.size)
		assertEquals(2, result.hiddenCount)
		assertEquals("word word word word…", result.messages[0].body)
	}
}

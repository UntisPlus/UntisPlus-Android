package com.deelabs.untisplus.widgets

import com.deelabs.untisplus.api.model.untis.MessageOfDay
import com.deelabs.untisplus.data.cache.CacheRead
import com.deelabs.untisplus.data.cache.readFreshOrCached
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetDailyMessagesCacheTest {
	private val message = MessageOfDay(id = 1, subject = "Notice", body = "Read <b>this</b>", attachments = emptyList())

	@Test
	fun `cold cache gives an explicit error result instead of a blank widget`() = runBlocking {
		val result = buildWidgetDailyMessages(
			readCache = { CacheRead.Unavailable },
			header = "Class 5a",
			emptyMessage = "No messages today",
			unavailableMessage = "Daily messages could not be loaded",
			unknownSubject = "Message",
		)

		assertEquals("Class 5a", result.header)
		assertEquals("Daily messages could not be loaded", result.message)
		assertTrue(result.messages.isEmpty())
	}

	@Test
	fun `corrupt cache gives an explicit error result`() = runBlocking {
		val read = readFreshOrCached<List<MessageOfDay>> { _, _ ->
			throw IllegalArgumentException("corrupt cache entry")
		}

		val result = buildWidgetDailyMessages(
			readCache = { read },
			header = "Class 5a",
			emptyMessage = "No messages today",
			unavailableMessage = "Daily messages could not be loaded",
			unknownSubject = "Message",
		)

		assertEquals("Daily messages could not be loaded", result.message)
	}

	@Test
	fun `stale cache is refreshed and rejected when refresh fails`() = runBlocking {
		val calls = mutableListOf<FromCache>()
		val read = readFreshOrCached<List<MessageOfDay>> { fromCache, _ ->
			calls += fromCache
			throw IllegalStateException("expired cache entry")
		}

		val result = buildWidgetDailyMessages(
			readCache = { read },
			header = "Class 5a",
			emptyMessage = "No messages today",
			unavailableMessage = "Daily messages could not be loaded",
			unknownSubject = "Message",
		)

		assertEquals("Daily messages could not be loaded", result.message)
		assertEquals(listOf(FromCache.CACHED_THEN_LOAD, FromCache.ONLY), calls)
	}

	@Test
	fun `legitimately empty cache shows the empty state`() = runBlocking {
		val result = buildWidgetDailyMessages(
			readCache = { CacheRead.Value(emptyList()) },
			header = "Class 5a",
			emptyMessage = "No messages today",
			unavailableMessage = "Daily messages could not be loaded",
			unknownSubject = "Message",
		)

		assertEquals("No messages today", result.message)
	}

	@Test
	fun `available messages are rendered and stripped of html`() = runBlocking {
		val result = buildWidgetDailyMessages(
			readCache = { CacheRead.Value(listOf(message)) },
			header = "Class 5a",
			emptyMessage = "No messages today",
			unavailableMessage = "Daily messages could not be loaded",
			unknownSubject = "Message",
			plainText = { it.replace(Regex("<[^>]+>"), "") },
		)

		assertNull(result.message)
		assertEquals("Notice", result.messages.single().subject)
		assertEquals("Read this", result.messages.single().body)
	}
}

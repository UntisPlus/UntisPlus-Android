package com.deelabs.untisplus.notifications

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InfoCenterNotificationControllerTest {
	private val entry = InfoCenterNotificationEntry("message:42", "sig-1", "Hello", "Body")

	@Test
	fun `opt-out does not post or advance dedupe state`() = runBlocking {
		val store = FakeStore(InfoCenterNotificationState(enabled = false, seen = emptyMap(), initialized = true))
		val notifier = FakeNotifier()

		DefaultInfoCenterNotificationController(notifier, store).onMessagesRefreshed(1L, listOf(entry))

		assertTrue(notifier.posts.isEmpty())
		assertNull(store.saved)
	}

	@Test
	fun `first refresh primes dedupe state`() = runBlocking {
		val store = FakeStore(InfoCenterNotificationState(enabled = true, seen = emptyMap(), initialized = false))
		val notifier = FakeNotifier()

		DefaultInfoCenterNotificationController(notifier, store).onMessagesRefreshed(1L, listOf(entry))

		assertTrue(notifier.posts.isEmpty())
		assertEquals(mapOf("message:42" to "sig-1"), store.saved?.seen)
		assertEquals(true, store.saved?.initialized)
	}

	@Test
	fun `changed entry posts once and repeated refresh is silent`() = runBlocking {
		val store = FakeStore(
			InfoCenterNotificationState(
				enabled = true,
				seen = mapOf("message:42" to "sig-0"),
				initialized = true,
			)
		)
		val notifier = FakeNotifier()
		val controller = DefaultInfoCenterNotificationController(notifier, store)

		controller.onMessagesRefreshed(1L, listOf(entry))
		controller.onMessagesRefreshed(1L, listOf(entry))

		assertEquals(listOf(listOf(entry)), notifier.posts)
		assertEquals(mapOf("message:42" to "sig-1"), store.saved?.seen)
	}

	@Test
	fun `failed post does not mark message as seen`() = runBlocking {
		val store = FakeStore(
			InfoCenterNotificationState(
				enabled = true,
				seen = mapOf("message:42" to "sig-0"),
				initialized = true,
			)
		)
		val notifier = FakeNotifier(postSucceeds = false)

		DefaultInfoCenterNotificationController(notifier, store).onMessagesRefreshed(1L, listOf(entry))

		assertEquals(1, notifier.posts.size)
		assertNull(store.saved)
	}

	@Test
	fun `cancel delegates to notifier`() {
		val notifier = FakeNotifier()

		DefaultInfoCenterNotificationController(notifier, FakeStore()).cancel()

		assertEquals(1, notifier.cancels)
	}

	private class FakeStore(
		initial: InfoCenterNotificationState = InfoCenterNotificationState(false, emptyMap(), false),
	) : InfoCenterNotificationStore {
		var state: InfoCenterNotificationState = initial
			private set

		var saved: SavedState? = null
			private set

		var clearedUserId: Long? = null
			private set

		override suspend fun read(userId: Long): InfoCenterNotificationState = state

		override suspend fun saveSeen(userId: Long, seen: Map<String, String>, initialized: Boolean) {
			saved = SavedState(userId, seen, initialized)
			state = state.copy(seen = seen, initialized = initialized)
		}

		override suspend fun clear(userId: Long) {
			clearedUserId = userId
			state = state.copy(enabled = false, seen = emptyMap(), initialized = false)
		}
	}

	private data class SavedState(
		val userId: Long,
		val seen: Map<String, String>,
		val initialized: Boolean,
	)

	private class FakeNotifier(
		private val canPostValue: Boolean = true,
		private val postSucceeds: Boolean = true,
	) : InfoCenterNotifier {
		val posts = mutableListOf<List<InfoCenterNotificationEntry>>()
		var cancels = 0
			private set

		override fun canPost(): Boolean = canPostValue

		override fun post(entries: List<InfoCenterNotificationEntry>): Boolean {
			posts += entries
			return postSucceeds
		}

		override fun cancel() {
			cancels++
		}
	}
}

package com.deelabs.untisplus.data.cache

import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.FromCache
import crocodile8.universal_cache.keep.MemoryCache
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Confirms the freshness assumptions behind [readFreshOrCached] with the real
 * cache library and an in-memory cache. The source is a local fake: no
 * accounts, credentials or network are involved.
 */
class CachedSourceFreshnessTest {
	private class FakeClock : TimeProvider {
		var now = 1_000_000L
		override fun get(): Long = now
	}

	@Test
	fun `cold bounded cache read fails instead of returning an empty list`() = runBlocking {
		val source = source(failSource = false)

		val result = runCatching { source.get("p", FromCache.ONLY, maxAge = 1_000).last() }

		assertTrue(result.exceptionOrNull() is NullPointerException)
	}

	@Test
	fun `stale cache with failed refresh fails both freshness-aware reads`() = runBlocking {
		val clock = FakeClock()
		var failSource = false
		val source = CachedSource<String, List<String>>(
			source = { if (failSource) error("offline") else listOf("cached") },
			cache = MemoryCache(10),
			timeProvider = clock,
		)
		source.get("p", FromCache.CACHED_THEN_LOAD, maxAge = 1_000).last()
		clock.now += 5_000
		failSource = true

		val refreshed = runCatching { source.get("p", FromCache.CACHED_THEN_LOAD, maxAge = 1_000).last() }
		val boundedOnly = runCatching { source.get("p", FromCache.ONLY, maxAge = 1_000).last() }

		assertTrue(refreshed.exceptionOrNull() is IllegalStateException)
		assertTrue(boundedOnly.exceptionOrNull() is NullPointerException)
	}

	private fun source(failSource: Boolean) = CachedSource<String, List<String>>(
		source = { if (failSource) error("offline") else listOf("cached") },
		cache = MemoryCache(10),
		timeProvider = FakeClock(),
	)
}

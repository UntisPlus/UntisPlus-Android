package com.deelabs.untisplus.data.cache

import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CacheReadTest {
	@Test
	fun `fresh cache read is a value without fallback`() = runBlocking {
		val calls = mutableListOf<Pair<FromCache, Long?>>()

		val result = readFreshOrCached<List<String>> { fromCache, maxAge ->
			calls += fromCache to maxAge
			listOf("fresh")
		}

		assertEquals(CacheRead.Value(listOf("fresh")), result)
		assertEquals(listOf(FromCache.CACHED_THEN_LOAD to INFO_CENTER_CACHE_MAX_AGE_MILLIS), calls)
	}

	@Test
	fun `legitimately empty cache is available, not unavailable`() = runBlocking {
		val result = readFreshOrCached<List<String>> { _, _ -> emptyList() }

		assertEquals(CacheRead.Value(emptyList<String>()), result)
	}

	@Test
	fun `cold cache reports unavailable after both bounded reads`() = runBlocking {
		val calls = mutableListOf<Pair<FromCache, Long?>>()

		val result = readFreshOrCached<List<String>> { fromCache, maxAge ->
			calls += fromCache to maxAge
			throw IllegalStateException("Cache is empty")
		}

		assertEquals(CacheRead.Unavailable, result)
		assertEquals(
			listOf(
				FromCache.CACHED_THEN_LOAD to INFO_CENTER_CACHE_MAX_AGE_MILLIS,
				FromCache.ONLY to INFO_CENTER_CACHE_MAX_AGE_MILLIS,
			),
			calls
		)
	}

	@Test
	fun `corrupt cache reports unavailable`() = runBlocking {
		val result = readFreshOrCached<List<String>> { _, _ ->
			throw IllegalArgumentException("corrupt cache entry")
		}

		assertEquals(CacheRead.Unavailable, result)
	}

	@Test
	fun `stale cache is refreshed first and rejected when refresh fails`() = runBlocking {
		val calls = mutableListOf<Pair<FromCache, Long?>>()

		val result = readFreshOrCached<List<String>> { fromCache, maxAge ->
			calls += fromCache to maxAge
			// CACHED_THEN_LOAD tries the source, ONLY enforces the same maxAge.
			throw IllegalStateException("stale entry rejected")
		}

		assertEquals(CacheRead.Unavailable, result)
		assertTrue(calls.all { it.second == INFO_CENTER_CACHE_MAX_AGE_MILLIS })
		assertEquals(2, calls.size)
	}

	@Test
	fun `valid cached value survives a failed refresh`() = runBlocking {
		val result = readFreshOrCached<List<String>> { fromCache, _ ->
			if (fromCache == FromCache.CACHED_THEN_LOAD) throw IllegalStateException("offline")
			listOf("cached")
		}

		assertEquals(CacheRead.Value(listOf("cached")), result)
	}
}

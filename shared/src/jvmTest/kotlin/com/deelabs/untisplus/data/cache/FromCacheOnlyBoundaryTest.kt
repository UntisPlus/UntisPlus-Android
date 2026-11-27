package com.deelabs.untisplus.data.cache

import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.FromCache
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FromCacheOnlyBoundaryTest {
	private val now = 2_000_000_000_000L

	@Test
	fun `ONLY without maxAge returns arbitrarily old cached data`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cachedAt = now - 10L * 365 * 24 * 60 * 60 * 1000
			val cache = DiskCache<String, String>(dir, serializer<String>())
			cache.put("old value", "key", null, cachedAt)
			val source = sourceWith(cache)

			val result = source.getRaw("key", FromCache.ONLY, false, null, null).first()

			assertEquals("old value", result.value)
			assertTrue(result.fromCache)
			assertEquals(cachedAt, result.originTimeStamp)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `ONLY with maxAge uses a strict age boundary`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cachedAt = now - 1_000L
			val cache = DiskCache<String, String>(dir, serializer<String>())
			cache.put("cached value", "key", null, cachedAt)
			val source = sourceWith(cache)

			val result = source.getRaw("key", FromCache.ONLY, false, 1_001L, null).first()
			assertEquals("cached value", result.value)
			assertTrue(result.fromCache)
			assertEquals(cachedAt, result.originTimeStamp)

			val error = assertFailsWith<NullPointerException> {
				source.getRaw("key", FromCache.ONLY, false, 1_000L, null).first()
			}
			assertEquals("Cache is empty", error.message)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `ONLY on an empty cache throws and never calls the source`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val source = sourceWith(DiskCache<String, String>(dir, serializer<String>()))

			val error = assertFailsWith<NullPointerException> {
				source.getRaw("key", FromCache.ONLY, false, null, null).first()
			}

			assertEquals("Cache is empty", error.message)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `ONLY treats a corrupt cache entry as an empty cache`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())
			cache.put("value", "key", null, now)
			val cacheFile = testFileSystem.list(dir).single()
			testFileSystem.write(cacheFile) { writeUtf8("corrupt") }

			val source = sourceWith(cache)
			val error = assertFailsWith<NullPointerException> {
				source.getRaw("key", FromCache.ONLY, false, null, null).first()
			}

			assertEquals("Cache is empty", error.message)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	private fun sourceWith(cache: DiskCache<String, String>): CachedSource<String, String> =
		CachedSource(
			{ _: String -> throw AssertionError("Source must not be called") },
			cache,
			object : TimeProvider {
				override fun get(): Long = now
			},
			Dispatchers.Default
		)
}

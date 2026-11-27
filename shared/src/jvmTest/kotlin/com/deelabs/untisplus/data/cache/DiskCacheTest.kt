package com.deelabs.untisplus.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskCacheTest {
	private val fileSystem = testFileSystem

	@Test
	fun `get on empty cache returns null without creating entries`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())

			assertNull(cache.get("missing", null))
			assertTrue(fileSystem.list(dir).isEmpty())
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `constructor creates missing cache directory and rejects a file`() {
		val dir = newTempCacheDir()
		try {
			val nested = dir.resolve("nested")
			DiskCache<String, String>(nested, serializer<String>())

			assertTrue(fileSystem.exists(nested))
			assertTrue(fileSystem.metadata(nested).isDirectory)

			val regularFile = dir.resolve("not-a-directory")
			fileSystem.write(regularFile) { writeUtf8("content") }

			assertFailsWith<IllegalArgumentException> { DiskCache<String, String>(regularFile, serializer<String>()) }
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `put stores and get reads value with supplied time`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())
			val cachedAt = 1_700_000_000_000L

			cache.put("cached value", "key", null, cachedAt)

			val cached = cache.get("key", null)
			assertEquals("cached value", cached?.value)
			assertEquals(cachedAt, cached?.time)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `put replaces both value and time for the same key`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())

			cache.put("first", "key", null, 100L)
			cache.put("second", "key", null, 900L)

			val cached = cache.get("key", null)
			assertEquals("second", cached?.value)
			assertEquals(900L, cached?.time)
			assertEquals(1, fileSystem.list(dir).size)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `cached time is independent of filesystem mtime`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())
			cache.put("value", "key", null, 123_456L)
			val cacheFile = fileSystem.list(dir).single()
			cacheFile.setTestLastModified(987_654_321L)

			val cached = cache.get("key", null)
			assertEquals("value", cached?.value)
			assertEquals(123_456L, cached?.time)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `params and additional key select distinct entries`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())

			cache.put("same params", "key", false, 1L)
			cache.put("other additional key", "key", true, 2L)
			cache.put("other params", "other", false, 3L)

			assertEquals("same params", cache.get("key", false)?.value)
			assertEquals("other additional key", cache.get("key", true)?.value)
			assertEquals("other params", cache.get("other", false)?.value)
			assertEquals(3, fileSystem.list(dir).size)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `corrupt cbor payload is treated as a cache miss`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())
			cache.put("value", "key", null, 1L)
			val cacheFile = fileSystem.list(dir).single()
			fileSystem.write(cacheFile) {
				writeLong(1L)
				writeUtf8("not valid cbor")
			}

			assertNull(cache.get("key", null))
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `truncated cache files are treated as cache misses`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())
			cache.put("value", "key", null, 1L)
			val cacheFile = fileSystem.list(dir).single()

			fileSystem.write(cacheFile) { write(byteArrayOf(1, 2, 3)) }
			assertNull(cache.get("key", null))

			fileSystem.write(cacheFile) { writeLong(1L) }
			assertNull(cache.get("key", null))
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `payload that cannot be decoded with configured serializer is a miss`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			DiskCache<String, Int>(dir, serializer<Int>()).put(42, "key", null, 1L)
			val booleanCache = DiskCache<String, Boolean>(dir, serializer<Boolean>())

			assertNull(booleanCache.get("key", null))
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `clear removes entries and leaves a usable empty cache`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())
			cache.put("one", "key-1", null, 1L)
			cache.put("two", "key-2", null, 2L)

			cache.clear()

			assertTrue(fileSystem.exists(dir))
			assertTrue(fileSystem.list(dir).isEmpty())
			assertNull(cache.get("key-1", null))
			assertNull(cache.get("key-2", null))

			cache.put("again", "key-1", null, 3L)
			assertEquals("again", cache.get("key-1", null)?.value)
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `clear on empty cache is a no-op`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())

			cache.clear()

			assertTrue(fileSystem.exists(dir))
			assertTrue(fileSystem.list(dir).isEmpty())
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `concurrent puts for distinct keys remain readable`() = runBlocking {
		val dir = newTempCacheDir()
		try {
			val cache = DiskCache<String, String>(dir, serializer<String>())

			withContext(Dispatchers.Default) {
				(1..12).map { index ->
					async { cache.put("value-$index", "key-$index", null, index.toLong() * 100) }
				}.awaitAll()
			}

			(1..12).forEach { index ->
				val cached = cache.get("key-$index", null)
				assertEquals("value-$index", cached?.value)
				assertEquals(index.toLong() * 100, cached?.time)
			}
		} finally {
			dir.deleteTempCacheDir()
		}
	}
}

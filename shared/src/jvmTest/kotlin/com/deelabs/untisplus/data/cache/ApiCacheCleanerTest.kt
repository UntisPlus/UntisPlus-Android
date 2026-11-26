package com.deelabs.untisplus.data.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiCacheCleanerTest {
	private val fileSystem = testFileSystem
	private val apiCacheDirectories = listOf("timetable", "periodData", "infocenter", "messenger")

	@Test
	fun `clears every api cache directory including nested files`() {
		val dir = newTempCacheDir()
		try {
			apiCacheDirectories.forEach { name ->
				val nested = dir.resolve(name).resolve("nested")
				fileSystem.createDirectories(nested)
				fileSystem.write(nested.resolve("response.cbor")) { writeUtf8("cached response") }
			}
			val unrelated = dir.resolve("other-cache")
			fileSystem.createDirectories(unrelated)
			fileSystem.write(unrelated.resolve("keep.txt")) { writeUtf8("keep") }
			fileSystem.write(dir.resolve("root-keep.txt")) { writeUtf8("keep") }

			ApiCacheCleaner.clearApiCaches(dir, fileSystem)

			apiCacheDirectories.forEach { name ->
				assertFalse(fileSystem.exists(dir.resolve(name)), "$name cache should be gone")
			}
			assertTrue(fileSystem.exists(unrelated))
			assertTrue(fileSystem.exists(unrelated.resolve("keep.txt")))
			assertTrue(fileSystem.exists(dir.resolve("root-keep.txt")))
			assertTrue(fileSystem.exists(dir))
		} finally {
			dir.deleteTempCacheDir()
		}
	}

	@Test
	fun `clearing an empty cache directory is a no-op and is idempotent`() {
		val dir = newTempCacheDir()
		try {
			ApiCacheCleaner.clearApiCaches(dir, fileSystem)
			assertTrue(fileSystem.exists(dir))
			assertEquals(emptyList<String>(), fileSystem.list(dir).map { it.name })

			fileSystem.createDirectories(dir.resolve("timetable"))
			fileSystem.write(dir.resolve("timetable").resolve("entry")) { writeUtf8("entry") }

			ApiCacheCleaner.clearApiCaches(dir, fileSystem)
			ApiCacheCleaner.clearApiCaches(dir, fileSystem)

			assertTrue(fileSystem.exists(dir))
			assertEquals(emptyList<String>(), fileSystem.list(dir).map { it.name })
		} finally {
			dir.deleteTempCacheDir()
		}
	}
}

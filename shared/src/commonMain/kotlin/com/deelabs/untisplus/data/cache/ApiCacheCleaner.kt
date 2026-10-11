package com.deelabs.untisplus.data.cache

import okio.FileSystem
import okio.Path

/**
 * Clears all JSON-RPC response caches that are tied to a specific API endpoint.
 * Must be called whenever a profile's effective API endpoint changes,
 * so cached responses from the previous endpoint are not served.
 */
object ApiCacheCleaner {
	private val CACHE_DIRECTORIES = listOf("timetable", "periodData", "infocenter", "messenger")

	fun clearApiCaches(cacheDir: Path, fileSystem: FileSystem = FileSystem.SYSTEM) {
		CACHE_DIRECTORIES.asSequence()
			.map { cacheDir.resolve(it) }
			.filter { fileSystem.exists(it) }
			.forEach { fileSystem.deleteRecursively(it) }
	}
}

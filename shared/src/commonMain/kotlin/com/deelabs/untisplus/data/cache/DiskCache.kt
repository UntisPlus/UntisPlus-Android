package com.deelabs.untisplus.data.cache

import crocodile8.universal_cache.keep.Cache
import crocodile8.universal_cache.keep.CacheKey
import crocodile8.universal_cache.keep.CachedData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import okio.Buffer
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.buffer

class DiskCache<KeyType : Any, ValueType : Any>(
	private val cacheDir: Path,
	private val valueSerializer: KSerializer<ValueType>,
	private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : Cache<KeyType, ValueType> {
	init {
		if (!fileSystem.exists(cacheDir)) {
			fileSystem.createDirectories(cacheDir)
		}

		if (!fileSystem.metadata(cacheDir).isDirectory) {
			throw IllegalArgumentException("cacheDir must be a directory")
		}
	}

	private val cacheLock = Mutex()

	@OptIn(ExperimentalSerializationApi::class)
	override suspend fun get(params: KeyType, additionalKey: Any?): CachedData<ValueType>? {
		val key = CacheKey(params, additionalKey)
		cacheLock.withLock {
			val cacheFile = getCacheFile(key)
			if (!fileSystem.exists(cacheFile)) return null

			val decoded = fileSystem.read(cacheFile) { readByteArray() }
				.let { Cbor.decodeFromByteArray(valueSerializer, it) }
			val lastModified = fileSystem.metadata(cacheFile).lastModifiedAtMillis ?: 0L
			return CachedData(decoded, lastModified)
		}
	}

	@OptIn(ExperimentalSerializationApi::class)
	override suspend fun put(value: ValueType, params: KeyType, additionalKey: Any?, time: Long) {
		val key = CacheKey(params, additionalKey)
		cacheLock.withLock {
			val encoded = Cbor.encodeToByteArray(valueSerializer, value)
			fileSystem.write(getCacheFile(key)) { write(encoded) }
		}
	}

	override suspend fun clear() {
		cacheLock.withLock {
			if (fileSystem.exists(cacheDir)) {
				fileSystem.list(cacheDir).forEach { fileSystem.delete(it) }
			}
		}
	}

	private fun getCacheFile(key: CacheKey<KeyType>): Path {
		val hashingSink = HashingSink.md5(Buffer())
		hashingSink.buffer().use { it.writeUtf8(key.toString()) }
		return cacheDir.resolve(hashingSink.hash.hex())
	}
}

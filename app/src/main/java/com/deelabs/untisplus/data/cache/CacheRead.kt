package com.deelabs.untisplus.data.cache

import crocodile8.universal_cache.FromCache

/** Freshness window shared by the account-free Info Center cache readers. */
internal const val INFO_CENTER_CACHE_MAX_AGE_MILLIS: Long = 60L * 60 * 1000

/**
 * A cache read that succeeded is [Value] even when the stored list is empty.
 * [Unavailable] means the read failed: no entry, corrupt entry, or an entry
 * older than [INFO_CENTER_CACHE_MAX_AGE_MILLIS].
 */
internal sealed interface CacheRead<out T> {
	data class Value<out T>(val value: T) : CacheRead<T>
	data object Unavailable : CacheRead<Nothing>
}

/**
 * Freshness-aware read using only the existing [CachedSource.get] API.
 *
 * A stale entry first tries a refresh. If that fails, the bounded `ONLY` read
 * rejects the stale entry as well, so callers never mistake an unavailable
 * cache for an empty result. Nothing is inferred from an exception other than
 * "this read cannot be trusted right now".
 */
internal suspend fun <T> readFreshOrCached(
	read: suspend (fromCache: FromCache, maxAge: Long?) -> T,
): CacheRead<T> = try {
	CacheRead.Value(read(FromCache.CACHED_THEN_LOAD, INFO_CENTER_CACHE_MAX_AGE_MILLIS))
} catch (primary: Exception) {
	try {
		CacheRead.Value(read(FromCache.ONLY, INFO_CENTER_CACHE_MAX_AGE_MILLIS))
	} catch (fallback: Exception) {
		CacheRead.Unavailable
	}
}

package com.deelabs.untisplus.cache

import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.data.database.entities.User
import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.keep.Cache
import crocodile8.universal_cache.keep.CacheKey
import crocodile8.universal_cache.keep.CachedData
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

val testToday = Clock.System.todayIn(TimeZone.currentSystemDefault())

const val CACHE_FIXED_TIME = 1_000_000L

object FixedTimeProvider : TimeProvider {
	override fun get(): Long = CACHE_FIXED_TIME
}

/** A cache that exposes the exact keys use cases pass to [CachedSource]. */
class RecordingCache<Key : Any, Value : Any> : Cache<Key, Value> {
	val entries = linkedMapOf<CacheKey<Key>, CachedData<Value>>()
	val puts = mutableListOf<CacheKey<Key>>()
	val gets = mutableListOf<CacheKey<Key>>()

	override suspend fun get(params: Key, additionalKey: Any?): CachedData<Value>? {
		val key = CacheKey(params, additionalKey)
		gets += key
		return entries[key]
	}

	override suspend fun put(value: Value, params: Key, additionalKey: Any?, time: Long) {
		val key = CacheKey(params, additionalKey)
		puts += key
		entries[key] = CachedData(value, time)
	}

	override suspend fun clear() = entries.clear()

	fun seed(params: Key, userId: Long, value: Value) {
		entries[CacheKey(params, userId)] = CachedData(value, CACHE_FIXED_TIME)
	}
}

/** Builds a real [CachedSource] whose loader produces per-profile values without any API. */
class SourceHarness<Key : Any, Value : Any>(
	private val cache: RecordingCache<Key, Value>,
	private val currentUserId: () -> Long,
	private val valueForUser: (Long) -> Value,
) {
	var loads = 0
		private set

	val loadedUserIds = mutableListOf<Long>()

	fun cachedSource(): CachedSource<Key, Value> = CachedSource(
		source = { _: Key ->
			val userId = currentUserId()
			loads++
			loadedUserIds += userId
			valueForUser(userId)
		},
		cache = cache,
		timeProvider = FixedTimeProvider,
	)
}

class MutableCurrentUserProvider(initialUser: User) : com.deelabs.untisplus.data.repository.CurrentUserProvider {
	override var currentUser: User? = initialUser
}

fun testUser(id: Long): User = User(
	id = id,
	apiHost = "example.invalid",
	user = "profile-$id",
	key = null,
	timeGrid = TimeGrid.generateDefault(),
	masterDataTimestamp = 0L,
	userData = UserData(
		elemId = 100L,
		elemType = ElementType.CLASS,
		displayName = "Profile $id",
		schoolName = "Test school",
		departmentId = 0L,
	),
)

/** Type-erased view so table-driven assertions can cover use cases with different result types. */
class AnyProfileCacheScenario(
	val name: String,
	val cachePuts: () -> List<CacheKey<*>>,
	val loadedUserIds: () -> List<Long>,
	val loads: () -> Int,
	val valueForUser: (Long) -> Any,
	val load: suspend () -> Any,
	val seed: (Long, Any) -> Unit,
)

/** One table row: all state a cache-isolation assertion needs for a single use case. */
class ProfileCacheScenario<Key : Any, Cached : Any, Out : Any>(
	val name: String,
	val cache: RecordingCache<Key, Cached>,
	val loadedUserIds: () -> List<Long>,
	val loads: () -> Int,
	val valueForUser: (Long) -> Out,
	val invoke: () -> Flow<Result<Out>>,
	val seed: (Long, Out) -> Unit,
) {
	fun erase(): AnyProfileCacheScenario = AnyProfileCacheScenario(
		name = name,
		cachePuts = { cache.puts.toList() },
		loadedUserIds = { loadedUserIds().toList() },
		loads = loads,
		valueForUser = { valueForUser(it) },
		// CACHED_THEN_LOAD emits the cached value before refreshing. Taking the first
		// emission exercises the cache lookup itself and would expose a cross-user hit.
		load = { invoke().first().getOrThrow() },
		seed = { userId, value -> seed(userId, @Suppress("UNCHECKED_CAST") (value as Out)) },
	)
}

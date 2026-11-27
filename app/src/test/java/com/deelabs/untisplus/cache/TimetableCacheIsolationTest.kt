package com.deelabs.untisplus.cache

import com.deelabs.untisplus.api.client.TimetableApi
import com.deelabs.untisplus.api.model.response.TimetableResult
import com.deelabs.untisplus.api.model.untis.MasterData
import com.deelabs.untisplus.api.model.untis.Timetable
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.model.untis.timetable.PeriodText
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UntisTimetableRepository
import crocodile8.universal_cache.FromCache
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Path

private class FakeTimetableApi : TimetableApi(
	Mockito.mock(HttpClientEngineFactory::class.java) as HttpClientEngineFactory<*>
) {
	val requestedUsers = mutableListOf<String?>()

	override suspend fun getTimetable(
		apiUrl: String,
		id: Long,
		type: ElementType,
		startDate: LocalDate,
		endDate: LocalDate,
		masterDataTimestamp: Long,
		timetableTimestamp: Long,
		timetableTimestamps: List<Long>,
		user: String?,
		key: String?
	): TimetableResult {
		requestedUsers += user
		val number = user?.removePrefix("profile-")?.toLongOrNull() ?: 0L
		return TimetableResult(
			timetable = Timetable(
				displayableStartDate = testToday,
				displayableEndDate = testToday,
				periods = listOf(
					Period(
						id = number,
						lessonId = number,
						startDateTime = LocalDateTime(2026, 1, 1, 10, 0),
						endDateTime = LocalDateTime(2026, 1, 1, 11, 0),
						foreColor = "",
						backColor = "",
						innerForeColor = "",
						innerBackColor = "",
						text = PeriodText(lesson = user ?: "", substitution = "", info = ""),
						homeWorks = emptyList(),
					)
				),
			),
			masterData = MasterData(
				absenceReasons = emptyList(),
				departments = emptyList(),
				duties = emptyList(),
				eventReasons = emptyList(),
				eventReasonGroups = emptyList(),
				excuseStatuses = emptyList(),
				holidays = emptyList(),
				klassen = emptyList(),
				rooms = emptyList(),
				subjects = emptyList(),
				teachers = emptyList(),
				teachingMethods = emptyList(),
				schoolyears = emptyList(),
				timeGrid = TimeGrid.generateDefault(),
			),
		)
	}
}

class TimetableCacheIsolationTest {
	@TempDir
	lateinit var tempDir: Path

	private fun repository(api: FakeTimetableApi, activeUserId: Long): Pair<UntisTimetableRepository, MutableCurrentUserProvider> {
		val provider = MutableCurrentUserProvider(testUser(activeUserId))
		return UntisTimetableRepository(
			currentUserProvider = provider,
			api = api,
			cacheDir = tempDir.toOkioPath(),
			timeProvider = FixedTimeProvider,
			userDao = Mockito.mock(UserDao::class.java),
		) to provider
	}

	private fun params() = TimetableRepository.TimetableParams(1L, ElementType.CLASS, testToday, testToday)

	private suspend fun load(
		repository: TimetableRepository,
		user: com.deelabs.untisplus.data.database.entities.User,
		userId: Long,
		fromCache: FromCache = FromCache.IF_HAVE,
	) = repository.timetableSource(user)
		.get(params(), fromCache, maxAge = 1_000_000L, additionalKey = userId)
		.first()

	@Test
	fun `explicit timetable users use separate cache entries`() = runBlocking {
		val api = FakeTimetableApi()
		val (repository) = repository(api, activeUserId = 1L)
		val userA = testUser(1L)
		val userB = testUser(2L)

		val forA = load(repository, userA, 1L)
		val forB = load(repository, userB, 2L)

		assertEquals(listOf("profile-1"), forA.map { it.text.lesson })
		assertEquals(listOf("profile-2"), forB.map { it.text.lesson })
		assertNotEquals(forA, forB)
		assertEquals(listOf("profile-1", "profile-2"), api.requestedUsers)
	}

	@Test
	fun `switching active user does not change explicit timetable caches`() = runBlocking {
		val api = FakeTimetableApi()
		val (repository, provider) = repository(api, activeUserId = 1L)
		val userA = testUser(1L)

		// user 2 is loaded for a background widget while profile 1 is active.
		val forB = load(repository, testUser(2L), 2L)
		provider.currentUser = testUser(1L)
		val forA = load(repository, userA, 1L)

		assertEquals(listOf("profile-2"), forB.map { it.text.lesson })
		assertEquals(listOf("profile-1"), forA.map { it.text.lesson })
		assertEquals(listOf("profile-2", "profile-1"), api.requestedUsers)

		// Switching back must still serve each explicit user from its own cache.
		provider.currentUser = testUser(2L)
		assertEquals(forA, load(repository, userA, 1L, FromCache.ONLY))
		assertEquals(forB, load(repository, testUser(2L), 2L, FromCache.ONLY))
		assertEquals(2, api.requestedUsers.size, "cached explicit users should not hit the API again")
	}
}

package com.deelabs.untisplus.cache

import com.deelabs.untisplus.api.model.response.PeriodDataResult
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.enumeration.PeriodState
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.model.untis.timetable.PeriodText
import com.deelabs.untisplus.data.database.entities.RoomEntity
import com.deelabs.untisplus.data.database.entities.RoomFinderDao
import com.deelabs.untisplus.data.database.entities.RoomFinderEntity
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserWithData
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.domain.GetRoomFinderItemsUseCaseImpl
import com.deelabs.untisplus.services.WeekLogicService
import crocodile8.universal_cache.CachedSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

private object FixedWeekLogicService : WeekLogicService {
	override val weekLength = 5
	override fun currentWeekStartDate() = java.time.LocalDate.of(2026, 1, 5)
}

private fun roomFinderPeriod(userId: Long) = Period(
	id = userId,
	lessonId = userId,
	startDateTime = LocalDateTime(2026, 1, 1, 10, 0),
	endDateTime = LocalDateTime(2026, 1, 1, 11, 0),
	foreColor = "",
	backColor = "",
	innerForeColor = "",
	innerBackColor = "",
	text = PeriodText(lesson = "lesson-$userId", substitution = "", info = ""),
	`is` = if (userId == 2L) listOf(PeriodState.CANCELLED) else emptyList(),
	homeWorks = emptyList(),
)

private class RoomFinderTimetableRepository(
	private val source: CachedSource<TimetableRepository.TimetableParams, List<Period>>,
) : TimetableRepository {
	override fun timetableSource(user: User?): CachedSource<TimetableRepository.TimetableParams, List<Period>> = source
	override fun periodDataSource(): CachedSource<Set<Period>, PeriodDataResult> = throw UnsupportedOperationException()
	override suspend fun postLessonTopic(periodId: Long, lessonTopic: String): Result<Boolean> = UnsupportedOperationException().let { Result.failure(it) }
	override suspend fun postAbsence(
		periodId: Long, studentId: Long, startTime: kotlinx.datetime.LocalTime, endTime: kotlinx.datetime.LocalTime
	): Result<List<com.deelabs.untisplus.api.model.untis.absence.StudentAbsence>> = UnsupportedOperationException().let { Result.failure(it) }
	override suspend fun deleteAbsence(absenceId: Long): Result<Boolean> = UnsupportedOperationException().let { Result.failure(it) }
	override suspend fun postAbsencesChecked(periodIds: Set<Long>): Result<Unit> = UnsupportedOperationException().let { Result.failure(it) }
}

private class RoomFinderFakes(provider: MutableCurrentUserProvider) {
	val cache = RecordingCache<TimetableRepository.TimetableParams, List<Period>>()
	val source = SourceHarness(cache, { provider.currentUser!!.id }) { listOf(roomFinderPeriod(it)) }

	val userRepository = Mockito.mock(UserRepository::class.java).apply {
		Mockito.`when`(currentUser).thenAnswer { provider.currentUser }
	}

	val dao = Mockito.mock(RoomFinderDao::class.java).apply {
		Mockito.`when`(getAllByUserId(Mockito.anyLong())).thenAnswer {
			flowOf(listOf(RoomFinderEntity(id = 1L, userId = it.arguments[0] as Long)))
		}
	}

	val masterDataRepository = Mockito.mock(MasterDataRepository::class.java).apply {
		val userWithData = Mockito.mock(UserWithData::class.java)
		Mockito.`when`(userWithData.rooms).thenReturn(listOf(RoomEntity(id = 1L, name = "Room 1")))
		Mockito.`when`(userData).thenReturn(userWithData)
	}

	fun useCase() = GetRoomFinderItemsUseCaseImpl(
		userRepository = userRepository,
		roomFinderDao = dao,
		timetableRepository = RoomFinderTimetableRepository(source.cachedSource()),
		weekLogicService = FixedWeekLogicService,
		masterDataRepository = masterDataRepository,
	)
}

class RoomFinderCacheIsolationTest {
	@Test
	fun `room finder cache keys differ and final states are not shared`() = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val fakes = RoomFinderFakes(provider)
		val useCase = fakes.useCase()

		val forA = useCase().last().single().states
		provider.currentUser = testUser(2L)
		val forB = useCase().last().single().states

		assertNotEquals(forA, forB)
		assertEquals(listOf(1L, 2L), fakes.source.loadedUserIds)
		assertEquals(1L, fakes.cache.puts.first().additionalKey)
		assertEquals(2L, fakes.cache.puts.last().additionalKey)
	}

	@Test
	fun `switching active user keeps the background room finder cache`() = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val fakes = RoomFinderFakes(provider)
		val useCase = fakes.useCase()

		val forA = useCase().last().single().states
		provider.currentUser = testUser(2L)
		val forB = useCase().last().single().states
		provider.currentUser = testUser(1L)
		val againA = useCase().last().single().states

		assertEquals(forA, againA)
		assertEquals(listOf(1L, 2L), fakes.source.loadedUserIds)
		assertEquals(2, fakes.cache.puts.size)
		assertEquals(1L, fakes.cache.puts.first().additionalKey)
		assertEquals(2L, fakes.cache.puts.last().additionalKey)
	}
}

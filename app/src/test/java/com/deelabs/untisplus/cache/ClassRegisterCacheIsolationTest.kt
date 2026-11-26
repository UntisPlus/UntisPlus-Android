package com.deelabs.untisplus.cache

import com.deelabs.untisplus.api.model.response.PeriodDataResult
import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.model.untis.timetable.PeriodData
import com.deelabs.untisplus.api.model.untis.timetable.PeriodText
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.repository.ClassRegisterRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.RestClassRegisterRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserRepository
import crocodile8.universal_cache.CachedSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

private class ClassRegisterCacheSource<Key : Any, Value : Any>(
	val cache: RecordingCache<Key, Value>,
	val source: SourceHarness<Key, Value>,
)

private fun <Key : Any, Value : Any> classRegisterHarness(
	provider: MutableCurrentUserProvider,
	valueForUser: (Long) -> Value,
): ClassRegisterCacheSource<Key, Value> {
	val cache = RecordingCache<Key, Value>()
	return ClassRegisterCacheSource(cache, SourceHarness(cache, { provider.currentUser!!.id }, valueForUser))
}

private fun classRegisterPeriod(userId: Long) = Period(
	id = userId,
	lessonId = userId,
	startDateTime = LocalDateTime(2026, 1, 1, 10, 0),
	endDateTime = LocalDateTime(2026, 1, 1, 11, 0),
	foreColor = "",
	backColor = "",
	innerForeColor = "",
	innerBackColor = "",
	text = PeriodText(lesson = "lesson-$userId", substitution = "", info = ""),
	homeWorks = emptyList(),
)

private fun classRegisterAbsence(userId: Long) = StudentAbsence(
	id = userId,
	studentId = userId,
	klasseId = 1L,
	startDateTime = LocalDateTime(2026, 1, 1, 10, 0),
	endDateTime = LocalDateTime(2026, 1, 1, 11, 0),
	owner = false,
	excused = false,
	excuse = null,
	absenceReasonId = 1L,
	absenceReason = "",
	text = "absence-$userId",
)

private fun classRegisterPeriodData(userId: Long) = PeriodDataResult(
	dataByTTId = mapOf(
		userId to PeriodData(
			ttId = userId,
			absenceChecked = false,
			studentIds = emptyList(),
			absences = listOf(classRegisterAbsence(userId)),
			classRegEvents = emptyList(),
			exemptions = emptyList(),
			prioritizedAttendances = emptyList(),
			text = null,
			topic = null,
			homeWorks = emptyList(),
			seatingPlan = null,
			classRoles = emptyList(),
			can = emptyList(),
		)
	),
	referencedStudents = emptyList(),
)

private class FakeTimetableRepository(
	private val periods: CachedSource<TimetableRepository.TimetableParams, List<Period>>,
	private val periodData: CachedSource<Set<Period>, PeriodDataResult>,
) : TimetableRepository {
	override fun timetableSource(user: User?): CachedSource<TimetableRepository.TimetableParams, List<Period>> = periods
	override fun periodDataSource(): CachedSource<Set<Period>, PeriodDataResult> = periodData
	override suspend fun postLessonTopic(periodId: Long, lessonTopic: String): Result<Boolean> = UnsupportedOperationException().let { Result.failure(it) }
	override suspend fun postAbsence(
		periodId: Long, studentId: Long, startTime: kotlinx.datetime.LocalTime, endTime: kotlinx.datetime.LocalTime
	): Result<List<StudentAbsence>> = UnsupportedOperationException().let { Result.failure(it) }
	override suspend fun deleteAbsence(absenceId: Long): Result<Boolean> = UnsupportedOperationException().let { Result.failure(it) }
	override suspend fun postAbsencesChecked(periodIds: Set<Long>): Result<Unit> = UnsupportedOperationException().let { Result.failure(it) }
}

private class ClassRegisterFakes(provider: MutableCurrentUserProvider) {
	val periods = classRegisterHarness<TimetableRepository.TimetableParams, List<Period>>(provider) { listOf(classRegisterPeriod(it)) }
	val periodData = classRegisterHarness<Set<Period>, PeriodDataResult>(provider) { classRegisterPeriodData(it) }

	fun repository() = FakeTimetableRepository(periods.source.cachedSource(), periodData.source.cachedSource())
}

private fun classRegisterRepository(provider: MutableCurrentUserProvider, fakes: ClassRegisterFakes): ClassRegisterRepository {
	val userRepository = Mockito.mock(UserRepository::class.java)
	Mockito.`when`(userRepository.currentUser).thenAnswer { provider.currentUser }
	return RestClassRegisterRepository(
		userRepository = userRepository,
		timetableRepository = fakes.repository(),
		masterDataRepository = Mockito.mock(MasterDataRepository::class.java),
	)
}

class ClassRegisterCacheIsolationTest {
	@Test
	fun `class register caches are keyed by user and do not leak absences`() = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val fakes = ClassRegisterFakes(provider)
		val repository = classRegisterRepository(provider, fakes)

		val forA = repository.loadAbsences(testToday, testToday).getOrThrow()
		provider.currentUser = testUser(2L)
		val forB = repository.loadAbsences(testToday, testToday).getOrThrow()

		assertEquals(listOf("absence-1"), forA.map { it.text })
		assertEquals(listOf("absence-2"), forB.map { it.text })
		assertNotEquals(forA, forB)
		assertEquals(1L, fakes.periods.cache.puts.first().additionalKey)
		assertEquals(2L, fakes.periods.cache.puts.last().additionalKey)
		assertEquals(1L, fakes.periodData.cache.puts.first().additionalKey)
		assertEquals(2L, fakes.periodData.cache.puts.last().additionalKey)
	}

	@Test
	fun `switching active user keeps the background class register cache`() = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val fakes = ClassRegisterFakes(provider)
		val repository = classRegisterRepository(provider, fakes)

		val forA = repository.loadAbsences(testToday, testToday).getOrThrow()
		val putsAfterA = fakes.periods.cache.puts.toList() to fakes.periodData.cache.puts.toList()
		provider.currentUser = testUser(2L)
		val forB = repository.loadAbsences(testToday, testToday).getOrThrow()

		assertEquals(listOf("absence-2"), forB.map { it.text })
		assertTrue(fakes.periods.cache.puts.drop(putsAfterA.first.size).all { it.additionalKey == 2L })
		assertTrue(fakes.periodData.cache.puts.drop(putsAfterA.second.size).all { it.additionalKey == 2L })

		provider.currentUser = testUser(1L)
		val periodsBeforeReturn = fakes.periods.cache.puts.toList()
		val periodDataBeforeReturn = fakes.periodData.cache.puts.toList()
		assertEquals(forA, repository.loadAbsences(testToday, testToday).getOrThrow())
		assertTrue(fakes.periods.cache.puts.drop(periodsBeforeReturn.size).all { it.additionalKey == 1L })
		assertTrue(fakes.periodData.cache.puts.drop(periodDataBeforeReturn.size).all { it.additionalKey == 1L })
	}
}

package com.deelabs.untisplus.cache

import com.deelabs.untisplus.api.model.untis.MessageOfDay
import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.api.model.untis.timetable.OfficeHour
import com.deelabs.untisplus.api.model.response.OfficeHourRegistrationsResult
import com.deelabs.untisplus.data.repository.DefaultMasterDataRepository
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import com.deelabs.untisplus.data.repository.MessagesRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.data.settings.model.UserSettings
import com.deelabs.untisplus.domain.GetAbsencesUseCase
import com.deelabs.untisplus.domain.GetCurrentSchoolYearUseCase
import com.deelabs.untisplus.domain.GetExamsUseCase
import com.deelabs.untisplus.domain.GetHomeworkUseCase
import com.deelabs.untisplus.domain.GetMessagesOfDayUseCaseImpl
import com.deelabs.untisplus.domain.GetMessagesUseCaseImpl
import com.deelabs.untisplus.domain.GetOfficeHourRegistrationsUseCase
import com.deelabs.untisplus.domain.GetOfficeHoursUseCase
import com.deelabs.untisplus.model.rest.Message
import com.deelabs.untisplus.model.rest.MessagesResponse
import crocodile8.universal_cache.CachedSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import java.util.stream.Stream

private data class CacheSource<Key : Any, Value : Any>(
	val cache: RecordingCache<Key, Value>,
	val source: SourceHarness<Key, Value>,
)

private fun <Key : Any, Value : Any> harness(
	provider: MutableCurrentUserProvider,
	valueForUser: (Long) -> Value,
): CacheSource<Key, Value> {
	val cache = RecordingCache<Key, Value>()
	return CacheSource(cache, SourceHarness(cache, { provider.currentUser!!.id }, valueForUser))
}

private class FakeInfoCenterRepository(
	private val messagesOfDay: CachedSource<LocalDate, List<MessageOfDay>>,
	private val exams: CachedSource<InfoCenterRepository.EventsParams, List<Exam>>,
	private val homework: CachedSource<InfoCenterRepository.EventsParams, List<HomeWork>>,
	private val absences: CachedSource<InfoCenterRepository.AbsencesParams, List<StudentAbsence>>,
	private val officeHours: CachedSource<InfoCenterRepository.OfficeHoursParams, List<OfficeHour>>,
	private val officeHourRegistrations: CachedSource<InfoCenterRepository.OfficeHourRegistrationParams, OfficeHourRegistrationsResult>,
) : InfoCenterRepository {
	override fun messagesOfDaySource() = messagesOfDay
	override fun examsSource() = exams
	override fun homeworkSource() = homework
	override fun absencesSource() = absences
	override fun officeHoursSource() = officeHours
	override fun officeHourRegistrationsSource() = officeHourRegistrations
}

private class InfoCenterFakes(provider: MutableCurrentUserProvider) {
	val messagesOfDay = harness<LocalDate, List<MessageOfDay>>(provider) { listOf(messageOfDay(it)) }
	val exams = harness<InfoCenterRepository.EventsParams, List<Exam>>(provider) { listOf(exam(it)) }
	val homework = harness<InfoCenterRepository.EventsParams, List<HomeWork>>(provider) { listOf(homework(it)) }
	val absences = harness<InfoCenterRepository.AbsencesParams, List<StudentAbsence>>(provider) { listOf(absence(it)) }
	val officeHours = harness<InfoCenterRepository.OfficeHoursParams, List<OfficeHour>>(provider) { listOf(officeHour(it)) }
	val officeHourRegistrations = harness<InfoCenterRepository.OfficeHourRegistrationParams, OfficeHourRegistrationsResult>(
		provider
	) { OfficeHourRegistrationsResult(teacherText = "registrations-${it}") }

	fun repository() = FakeInfoCenterRepository(
		messagesOfDay = messagesOfDay.source.cachedSource(),
		exams = exams.source.cachedSource(),
		homework = homework.source.cachedSource(),
		absences = absences.source.cachedSource(),
		officeHours = officeHours.source.cachedSource(),
		officeHourRegistrations = officeHourRegistrations.source.cachedSource(),
	)
}

private class FakeMessagesRepository(
	val messages: CacheSource<Unit, MessagesResponse>,
	val message: CacheSource<Long, Message>,
) : MessagesRepository {
	override fun messageSource(): CachedSource<Long, Message> = message.source.cachedSource()
	override fun messagesSource(): CachedSource<Unit, MessagesResponse> = messages.source.cachedSource()
	override fun messagesSentSource(): CachedSource<Unit, List<Message>> = throw UnsupportedOperationException()
	override fun messagesDraftsSource(): CachedSource<Unit, List<Message>> = throw UnsupportedOperationException()
}

private fun userRepository(provider: MutableCurrentUserProvider): UserRepository =
	Mockito.mock(UserRepository::class.java).apply {
		Mockito.`when`(currentUser).thenAnswer { provider.currentUser }
	}

private fun settingsRepository(): UserSettingsRepository =
	Mockito.mock(UserSettingsRepository::class.java).apply {
		Mockito.`when`(getSettings()).thenReturn(flowOf(UserSettings.getDefaultInstance()))
	}

private fun messageOfDay(userId: Long) = MessageOfDay(
	id = userId,
	subject = "message-of-day-$userId",
	body = "body-$userId",
	attachments = emptyList(),
)

private fun homework(userId: Long) = HomeWork(
	id = userId,
	lessonId = 1L,
	startDate = testToday,
	endDate = testToday,
	text = "homework-$userId",
	completed = false,
	attachments = emptyList(),
)

private fun exam(userId: Long) = Exam(
	id = userId,
	examType = null,
	startDateTime = LocalDateTime(2026, 1, 1, 10, 0),
	endDateTime = LocalDateTime(2026, 1, 1, 11, 0),
	departmentId = 1L,
	subjectId = 1L,
	name = "exam-$userId",
	text = "",
)

private fun absence(userId: Long) = StudentAbsence(
	id = userId,
	studentId = 1L,
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

private fun officeHour(userId: Long) = OfficeHour(
	id = userId,
	startDateTime = LocalDateTime(2026, 1, 1, 10, 0),
	endDateTime = LocalDateTime(2026, 1, 1, 11, 0),
	teacherId = 1L,
	imageId = 0L,
	email = null,
	phone = null,
	displayNameRooms = "",
	displayNameTeacher = "",
	registrationPossible = false,
	registered = false,
)

private fun message(userId: Long) = Message(
	id = userId,
	subject = "message-$userId",
	content = "body-$userId",
)

fun scenarios(): Stream<Arguments> = Stream.of(
	Arguments.of("GetHomeworkUseCase", { provider: MutableCurrentUserProvider ->
		val fakes = InfoCenterFakes(provider)
		val params = InfoCenterRepository.EventsParams(1L, ElementType.CLASS, testToday, testToday)
		val useCase = GetHomeworkUseCase(provider, fakes.repository(), GetCurrentSchoolYearUseCase(DefaultMasterDataRepository()))
		ProfileCacheScenario(
			name = "GetHomeworkUseCase",
			cache = fakes.homework.cache,
			loadedUserIds = { fakes.homework.source.loadedUserIds },
			loads = { fakes.homework.source.loads },
			valueForUser = { listOf(homework(it)) },
			invoke = { useCase(1L, ElementType.CLASS) },
			seed = { userId, value -> fakes.homework.cache.seed(params, userId, value) },
		).erase()
	}),
	Arguments.of("GetExamsUseCase", { provider: MutableCurrentUserProvider ->
		val fakes = InfoCenterFakes(provider)
		val params = InfoCenterRepository.EventsParams(1L, ElementType.CLASS, testToday, testToday)
		val useCase = GetExamsUseCase(provider, fakes.repository(), GetCurrentSchoolYearUseCase(DefaultMasterDataRepository()))
		ProfileCacheScenario(
			name = "GetExamsUseCase",
			cache = fakes.exams.cache,
			loadedUserIds = { fakes.exams.source.loadedUserIds },
			loads = { fakes.exams.source.loads },
			valueForUser = { listOf(exam(it)) },
			invoke = { useCase(1L, ElementType.CLASS) },
			seed = { userId, value -> fakes.exams.cache.seed(params, userId, value) },
		).erase()
	}),
	Arguments.of("GetMessagesOfDayUseCase", { provider: MutableCurrentUserProvider ->
		val fakes = InfoCenterFakes(provider)
		val useCase = GetMessagesOfDayUseCaseImpl(provider, fakes.repository())
		ProfileCacheScenario(
			name = "GetMessagesOfDayUseCase",
			cache = fakes.messagesOfDay.cache,
			loadedUserIds = { fakes.messagesOfDay.source.loadedUserIds },
			loads = { fakes.messagesOfDay.source.loads },
			valueForUser = { listOf(messageOfDay(it)) },
			invoke = { useCase() },
			seed = { userId, value -> fakes.messagesOfDay.cache.seed(testToday, userId, value) },
		).erase()
	}),
	Arguments.of("GetOfficeHoursUseCase", { provider: MutableCurrentUserProvider ->
		val fakes = InfoCenterFakes(provider)
		val params = InfoCenterRepository.OfficeHoursParams(-1L, testToday)
		val useCase = GetOfficeHoursUseCase(provider, fakes.repository())
		ProfileCacheScenario(
			name = "GetOfficeHoursUseCase",
			cache = fakes.officeHours.cache,
			loadedUserIds = { fakes.officeHours.source.loadedUserIds },
			loads = { fakes.officeHours.source.loads },
			valueForUser = { listOf(officeHour(it)) },
			invoke = { useCase() },
			seed = { userId, value -> fakes.officeHours.cache.seed(params, userId, value) },
		).erase()
	}),
	Arguments.of("GetOfficeHourRegistrationsUseCase", { provider: MutableCurrentUserProvider ->
		val fakes = InfoCenterFakes(provider)
		val target = officeHour(99L)
		val params = InfoCenterRepository.OfficeHourRegistrationParams(target.id, target.teacherId)
		val useCase = GetOfficeHourRegistrationsUseCase(provider, fakes.repository())
		ProfileCacheScenario(
			name = "GetOfficeHourRegistrationsUseCase",
			cache = fakes.officeHourRegistrations.cache,
			loadedUserIds = { fakes.officeHourRegistrations.source.loadedUserIds },
			loads = { fakes.officeHourRegistrations.source.loads },
			valueForUser = { OfficeHourRegistrationsResult(teacherText = "registrations-$it") },
			invoke = { useCase(target) },
			seed = { userId, value -> fakes.officeHourRegistrations.cache.seed(params, userId, value) },
		).erase()
	}),
	Arguments.of("GetAbsencesUseCase", { provider: MutableCurrentUserProvider ->
		val fakes = InfoCenterFakes(provider)
		val params = InfoCenterRepository.AbsencesParams(testToday, testToday, includeExcused = true)
		val useCase = GetAbsencesUseCase(
			userRepository(provider),
			fakes.repository(),
			settingsRepository(),
			GetCurrentSchoolYearUseCase(DefaultMasterDataRepository()),
		)
		ProfileCacheScenario(
			name = "GetAbsencesUseCase",
			cache = fakes.absences.cache,
			loadedUserIds = { fakes.absences.source.loadedUserIds },
			loads = { fakes.absences.source.loads },
			valueForUser = { listOf(absence(it)) },
			invoke = { useCase() },
			seed = { userId, value -> fakes.absences.cache.seed(params, userId, value) },
		).erase()
	}),
	Arguments.of("GetMessagesUseCase.list", { provider: MutableCurrentUserProvider ->
		val messages = harness<Unit, MessagesResponse>(provider) {
			MessagesResponse(incomingMessages = listOf(message(it)))
		}
		val messageDetail = harness<Long, Message>(provider) { message(it) }
		val useCase = GetMessagesUseCaseImpl(userRepository(provider), FakeMessagesRepository(messages, messageDetail))
		ProfileCacheScenario(
			name = "GetMessagesUseCase.list",
			cache = messages.cache,
			loadedUserIds = { messages.source.loadedUserIds },
			loads = { messages.source.loads },
			valueForUser = { listOf(message(it)) },
			invoke = { useCase() },
			seed = { userId, value -> messages.cache.seed(Unit, userId, MessagesResponse(incomingMessages = value)) },
		).erase()
		}),
	Arguments.of("GetMessagesUseCase.detail", { provider: MutableCurrentUserProvider ->
		val messages = harness<Unit, MessagesResponse>(provider) {
			MessagesResponse(incomingMessages = listOf(message(it)))
		}
		val detail = harness<Long, Message>(provider) { message(it) }
		val useCase = GetMessagesUseCaseImpl(userRepository(provider), FakeMessagesRepository(messages, detail))
		val messageId = 99L
		ProfileCacheScenario(
			name = "GetMessagesUseCase.detail",
			cache = detail.cache,
			loadedUserIds = { detail.source.loadedUserIds },
			loads = { detail.source.loads },
			valueForUser = { message(it) },
			invoke = { useCase(messageId) },
			seed = { userId, value -> detail.cache.seed(messageId, userId, value as Message) },
		).erase()
		})
	)

class InfoCenterCacheIsolationTest {
	@ParameterizedTest(name = "{0}: cache keys differ for two user ids")
	@MethodSource("com.deelabs.untisplus.cache.InfoCenterCacheIsolationTestKt#scenarios")
	fun `cache keys differ for two user ids`(name: String, factory: (MutableCurrentUserProvider) -> AnyProfileCacheScenario) = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val scenario = factory(provider)

		scenario.load()
		provider.currentUser = testUser(2L)
		scenario.load()

		val keys = scenario.cachePuts()
		assertEquals(2, keys.size, "$name should write one cache entry per profile")
		assertEquals(1L, keys[0].additionalKey, "$name should key the first load by user 1")
		assertEquals(2L, keys[1].additionalKey, "$name should key the second load by user 2")
		assertNotEquals(keys[0], keys[1])
	}

	@ParameterizedTest(name = "{0}: data loaded for A is not returned for B")
	@MethodSource("com.deelabs.untisplus.cache.InfoCenterCacheIsolationTestKt#scenarios")
	fun `data loaded for user A is not returned for user B`(name: String, factory: (MutableCurrentUserProvider) -> AnyProfileCacheScenario) = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val scenario = factory(provider)

		val forA = scenario.load()
		provider.currentUser = testUser(2L)
		val forB = scenario.load()

		assertEquals(scenario.valueForUser(1L), forA)
		assertEquals(scenario.valueForUser(2L), forB, "$name must not return profile 1's cached data to profile 2")
		assertNotEquals(forA, forB)
	}

	@ParameterizedTest(name = "{0}: switching active user keeps background cache")
	@MethodSource("com.deelabs.untisplus.cache.InfoCenterCacheIsolationTestKt#scenarios")
	fun `switching active user does not change a background user's cached result`(
		name: String,
		factory: (MutableCurrentUserProvider) -> AnyProfileCacheScenario,
	) = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val scenario = factory(provider)

		scenario.load()
		val putsBeforeSwitch = scenario.cachePuts()
		val idsBeforeSwitch = scenario.loadedUserIds()
		provider.currentUser = testUser(2L)
		scenario.load()
		assertEquals(putsBeforeSwitch.size + 1, scenario.cachePuts().size, "$name should only write profile 2's cache after switching")
		assertEquals(2L, scenario.cachePuts().last().additionalKey, "$name wrote a cache entry for the wrong user after switching")
		assertTrue(scenario.loadedUserIds().drop(idsBeforeSwitch.size).all { it == 2L }, "$name loaded a background user's data while profile 2 was active")
		val putsAfterSwitch = scenario.cachePuts()
		val idsBeforeReturn = scenario.loadedUserIds()
		provider.currentUser = testUser(1L)
		val returnedForA = scenario.load()

		assertEquals(scenario.valueForUser(1L), returnedForA, "$name lost profile 1's cached result after switching away")
		assertTrue(scenario.loadedUserIds().drop(idsBeforeReturn.size).all { it == 1L }, "$name loaded profile 2's data after switching back to profile 1")
		assertTrue(scenario.cachePuts().drop(putsAfterSwitch.size).all { it.additionalKey == 1L }, "$name rewrote profile 2's cache after switching back")
	}

	@ParameterizedTest(name = "{0}: missing cache for A does not hide B's cached data")
	@MethodSource("com.deelabs.untisplus.cache.InfoCenterCacheIsolationTestKt#scenarios")
	fun `empty cache for one user does not suppress another user's cached data`(
		name: String,
		factory: (MutableCurrentUserProvider) -> AnyProfileCacheScenario,
	) = runBlocking {
		val provider = MutableCurrentUserProvider(testUser(1L))
		val scenario = factory(provider)

		// Seed profile 2 only. Profile 1 has no cache entry at all.
		scenario.seed(2L, scenario.valueForUser(2L))
		val forA = scenario.load()
		provider.currentUser = testUser(2L)
		val idsBeforeB = scenario.loadedUserIds()
		val putsAfterA = scenario.cachePuts()
		val forB = scenario.load()

		assertEquals(scenario.valueForUser(1L), forA, "$name failed to load data when this profile had no cache")
		assertEquals(scenario.valueForUser(2L), forB, "$name lost profile 2's seeded cache while loading profile 1")
		assertTrue(scenario.loadedUserIds().drop(idsBeforeB.size).all { it == 2L }, "$name loaded profile 1's data after switching to profile 2")
		assertTrue(scenario.cachePuts().drop(putsAfterA.size).all { it.additionalKey == 2L }, "$name overwrote profile 1's cache while profile 2 was active")
	}
}

package com.deelabs.untisplus.workers

import android.content.Context
import androidx.work.WorkerParameters
import com.deelabs.untisplus.api.client.TimetableApi
import com.deelabs.untisplus.api.model.response.TimetableResult
import com.deelabs.untisplus.api.model.untis.MasterData
import com.deelabs.untisplus.api.model.untis.Timetable
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.api.model.untis.timetable.PeriodText
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.database.entities.jsonRpcApiUrlString
import com.deelabs.untisplus.data.repository.CurrentUserProvider
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UntisTimetableRepository
import crocodile8.universal_cache.FromCache
import crocodile8.universal_cache.time.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import okio.FileSystem
import okio.Path
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Account-free checks that background timetable loading stays in the target profile's scope. */
class TimetableDependantWorkerUserScopeTest {
	private val activeUser = fixtureUser(id = 1, name = "active-profile", key = "active-fixture-key")
	private val otherUser = fixtureUser(id = 2, name = "other-profile", key = "other-fixture-key")
	private val thirdUser = fixtureUser(id = 3, name = "third-profile", key = "third-fixture-key")
	private val element = PeriodElement(ElementType.CLASS, 42L)
	private val cacheDirs = mutableListOf<Path>()

	@AfterEach
	fun cleanUp() {
		cacheDirs.forEach { runCatching { FileSystem.SYSTEM.deleteRecursively(it) } }
		cacheDirs.clear()
	}

	@Test
	fun `loadTimetable passes target user not active user to timetableSource`() = runBlocking {
		val fixture = fixture(activeUser, otherUser)

		val periods = fixture.worker().load(otherUser)

		assertEquals(listOf(period(otherUser.id)), periods)
		val call = fixture.apiCalls.single()
		assertEquals(otherUser.jsonRpcApiUrlString, call.apiUrl)
		assertEquals(otherUser.user, call.user)
		assertEquals(otherUser.key, call.key)
		assertNotEquals(activeUser.user, call.user)
		assertNotEquals(activeUser.key, call.key)
	}

	@Test
	fun `cached periods are namespaced by target user id`() = runBlocking {
		val fixture = fixture(activeUser, otherUser, thirdUser)
		val worker = fixture.worker()

		worker.load(otherUser)
		worker.load(thirdUser)

		assertEquals(2, fixture.apiCalls.size)
		assertEquals(listOf(period(otherUser.id)), fixture.cached(otherUser))
		assertEquals(listOf(period(thirdUser.id)), fixture.cached(thirdUser))
	}

	@Test
	fun `master data is stored under target user id not active user id`() = runBlocking {
		val fixture = fixture(activeUser, otherUser)

		fixture.worker().load(otherUser)

		coVerify(exactly = 1) {
			fixture.userDao.upsertMasterData(otherUser.id, withArg {
				assertEquals(otherUser.id, it.timeStamp)
			})
		}
		coVerify(exactly = 0) { fixture.userDao.upsertMasterData(activeUser.id, any()) }
	}

	@Test
	fun `daily worker style iteration over users keeps each profile in its own scope`() = runBlocking {
		val users = listOf(otherUser, activeUser, thirdUser)
		val fixture = fixture(activeUser, *users.toTypedArray())
		val worker = fixture.worker()

		val loaded = users.map { it to worker.load(it) }.toMap()

		assertEquals(users.size, fixture.apiCalls.size)
		assertEquals(users.map { it.user }, fixture.apiCalls.map { it.user })
		users.forEach { user ->
			assertEquals(listOf(period(user.id)), loaded[user])
			assertEquals(listOf(period(user.id)), fixture.cached(user))
			assertEquals(user.jsonRpcApiUrlString, fixture.apiCalls.first { it.user == user.user }.apiUrl)
			coVerify(exactly = 1) {
				fixture.userDao.upsertMasterData(user.id, withArg {
					assertEquals(user.id, it.timeStamp)
				})
			}
		}
	}

	private fun fixture(active: User, vararg users: User): Fixture {
		val fixture = Fixture(active, users.toList())
		cacheDirs += fixture.cacheDir
		return fixture
	}

	private fun fixtureUser(id: Long, name: String, key: String) = User(
		id = id,
		apiHost = "fixture-$id.invalid",
		user = name,
		key = key,
		timeGrid = TimeGrid.generateDefault(),
		masterDataTimestamp = 0L,
		userData = UserData(
			elemId = 100 + id,
			elemType = ElementType.CLASS,
			displayName = name,
			schoolName = "Fixture school",
			departmentId = 0L,
		),
	)

	private fun period(id: Long) = Period(
		id = id,
		lessonId = id,
		startDateTime = LocalDateTime(2026, 11, 30, 8, 0),
		endDateTime = LocalDateTime(2026, 11, 30, 8, 45),
		foreColor = "000000",
		backColor = "ffffff",
		innerForeColor = "000000",
		innerBackColor = "ffffff",
		text = PeriodText(lesson = "Lesson $id", substitution = "", info = ""),
		homeWorks = null,
	)

	private fun params() = TimetableRepository.TimetableParams(
		elementId = element.id,
		elementType = element.type,
		startDate = java.time.LocalDate.now().toKotlinLocalDate(),
		endDate = java.time.LocalDate.now().toKotlinLocalDate(),
	)

	private data class ApiCall(val apiUrl: String, val user: String?, val key: String?)

	private class TestWorker(
		repository: TimetableRepository,
		private val testElement: PeriodElement,
	) : TimetableDependantWorker(
		mockk<Context>(relaxed = true),
		mockk<WorkerParameters>(relaxed = true),
		repository,
	) {
		suspend fun load(user: User): List<Period> = loadTimetable(user, testElement, FromCache.NEVER)

		override suspend fun doWork(): Result = Result.success()
	}

	private inner class Fixture(
		activeUser: User,
		users: List<User>,
	) {
		val cacheDir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
			.resolve("untisplus-worker-scope-test-${UUID.randomUUID()}")
		val apiCalls = mutableListOf<ApiCall>()
		val api = mockk<TimetableApi>(relaxed = true)
		val userDao = mockk<UserDao>(relaxed = true)

		private val currentUserProvider = object : CurrentUserProvider {
			override val currentUser: User = activeUser
		}

		private val repository = UntisTimetableRepository(
			currentUserProvider,
			api,
			cacheDir,
			object : TimeProvider {
				override fun get(): Long = 1_700_000_000_000L
			},
			userDao,
		)

		init {
			coEvery {
				api.getTimetable(
					apiUrl = any(),
					id = any(),
					type = any(),
					startDate = any(),
					endDate = any(),
					masterDataTimestamp = any(),
					timetableTimestamp = any(),
					timetableTimestamps = any(),
					user = anyNullable<String>(),
					key = anyNullable<String>(),
				)
			} answers {
				val user = args[8] as? String
				val key = args[9] as? String
				apiCalls += ApiCall(apiUrl = args[0] as String, user = user, key = key)
				val target = users.firstOrNull { it.user == user }
					?: throw AssertionError("Unexpected fixture user: $user")
				timetableResult(target.id)
			}
		}

		fun worker(): TestWorker = TestWorker(repository, element)

		suspend fun cached(user: User): List<Period> = repository.timetableSource(user).get(
			params = params(),
			fromCache = FromCache.ONLY,
			additionalKey = user.id,
		).last()

		private fun timetableResult(id: Long) = TimetableResult(
			timetable = Timetable(
				displayableStartDate = LocalDate(2026, 11, 30),
				displayableEndDate = LocalDate(2026, 11, 30),
				periods = listOf(period(id)),
			),
			masterData = MasterData(
				timeStamp = id,
				absenceReasons = null,
				departments = null,
				duties = null,
				eventReasons = null,
				eventReasonGroups = null,
				excuseStatuses = null,
				holidays = null,
				klassen = emptyList(),
				rooms = emptyList(),
				subjects = emptyList(),
				teachers = emptyList(),
				teachingMethods = null,
				schoolyears = null,
				timeGrid = null,
			),
		)
	}
}

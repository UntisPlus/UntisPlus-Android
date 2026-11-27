package com.deelabs.untisplus.workers

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.data.settings.model.UserSettings
import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.FromCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/** Exercises DailyWorker with local fakes only: no accounts, network or live WorkManager. */
class DailyWorkerSchedulingTest {
	private val context: Context = mockk(relaxed = true)
	private val workManager: WorkManager = mockk(relaxed = true)
	private val enqueued = mutableListOf<WorkRequest>()

	@BeforeEach
	fun setUp() {
		mockkObject(WorkManager.Companion)
		every { WorkManager.getInstance(any<Context>()) } returns workManager
		every { workManager.enqueue(capture(enqueued)) } returns mockk(relaxed = true)

		mockkStatic(Log::class)
		every { Log.d(any<String>(), any<String>()) } returns 0
		every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
	}

	@AfterEach
	fun tearDown() {
		unmockkStatic(Log::class)
		unmockkObject(WorkManager.Companion)
	}

	@Test
	fun `enqueues one setup per eligible user and no duplicates`() = runBlocking {
		val loads = mutableListOf<Pair<Long, FromCache>>()
		val source = recordingSource(loads)
		val worker = worker(
			users = listOf(testUser(1), testUser(2)),
			settings = mapOf(
				1L to testUserSettings(notifications = true, automute = true),
				2L to testUserSettings(notifications = true, automute = true),
			),
			source = source,
		)

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)

		assertEquals(2, count(NotificationSetupWorker::class))
		assertEquals(2, count(AutoMuteSetupWorker::class))
		assertEquals(1, count(WidgetUpdateWorker::class))
		assertEquals(1, count(DailyWorker::class))
		assertEquals(setOf(1L, 2L), userIds(NotificationSetupWorker::class))
		assertEquals(setOf(1L, 2L), userIds(AutoMuteSetupWorker::class))
		assertEquals(listOf(1L to FromCache.NEVER, 2L to FromCache.NEVER), loads)
	}

	@Test
	fun `honours each user's notification and auto mute opt-outs`() = runBlocking {
		val worker = worker(
			users = listOf(testUser(1), testUser(2)),
			settings = mapOf(
				1L to testUserSettings(notifications = true, automute = false),
				2L to testUserSettings(notifications = false, automute = true),
			),
			source = periodsSource(emptyList()),
		)

		worker.doWork()

		assertEquals(setOf(1L), userIds(NotificationSetupWorker::class))
		assertEquals(setOf(2L), userIds(AutoMuteSetupWorker::class))
	}

	@Test
	fun `skips users without a personal timetable target`() = runBlocking {
		val loads = mutableListOf<Pair<Long, FromCache>>()
		val userWithoutTarget = testUser(1).copy(
			userData = testUser(1).userData.copy(elemId = 0, elemType = null),
		)
		val worker = worker(
			users = listOf(userWithoutTarget, testUser(2)),
			settings = mapOf(
				1L to testUserSettings(personalTimetable = false, notifications = true, automute = true),
				2L to testUserSettings(notifications = true, automute = true),
			),
			source = recordingSource(loads),
		)

		worker.doWork()

		assertEquals(listOf(2L to FromCache.NEVER), loads)
		assertEquals(setOf(2L), userIds(NotificationSetupWorker::class))
		assertEquals(setOf(2L), userIds(AutoMuteSetupWorker::class))
	}

	@Test
	fun `a failing timetable load does not block the remaining users`() = runBlocking {
		val source = fakeTimetableSource { _, _, key ->
			if (key == 1L) flow { throw IOException("local cache unavailable") }
			else flowOf(emptyList<Period>())
		}
		val worker = worker(
			users = listOf(testUser(1), testUser(2)),
			settings = mapOf(
				1L to testUserSettings(notifications = true, automute = true),
				2L to testUserSettings(notifications = true, automute = true),
			),
			source = source,
		)

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)

		assertEquals(setOf(2L), userIds(NotificationSetupWorker::class))
		assertEquals(setOf(2L), userIds(AutoMuteSetupWorker::class))
		assertEquals(1, count(WidgetUpdateWorker::class))
		assertEquals(1, count(DailyWorker::class))
	}

	@Test
	fun `widget refresh and next daily run are scheduled even without users`() = runBlocking {
		val worker = worker(emptyList(), emptyMap(), periodsSource(emptyList()))

		worker.doWork()

		assertEquals(0, count(NotificationSetupWorker::class))
		assertEquals(0, count(AutoMuteSetupWorker::class))
		assertEquals(1, count(WidgetUpdateWorker::class))
		assertEquals(1, count(DailyWorker::class))
		assertTrue(enqueued.any { DailyWorker.TAG_DAILY_WORK in it.tags })
	}

	private fun worker(
		users: List<User>,
		settings: Map<Long, UserSettings>,
		source: CachedSource<TimetableRepository.TimetableParams, List<Period>>,
	): DailyWorker {
		val userDao = mockk<UserDao>(relaxed = true)
		every { userDao.getAllFlow() } returns flowOf(users)
		val settingsRepository = mockk<UserSettingsRepository>(relaxed = true)
		every { settingsRepository.getSettings(any()) } answers {
			flowOf(settings[firstArg<Long>()] ?: UserSettings.getDefaultInstance())
		}
		return DailyWorker(
			context,
			workerParams(),
			settingsRepository,
			FakeTimetableRepository(source),
			userDao,
		)
	}

	private fun recordingSource(loads: MutableList<Pair<Long, FromCache>>) =
		fakeTimetableSource { _, fromCache, key ->
			loads += key as Long to fromCache
			flowOf(emptyList<Period>())
		}

	private fun count(worker: kotlin.reflect.KClass<*>) =
		enqueued.count { it.workSpec.workerClassName == worker.java.name }

	private fun userIds(worker: kotlin.reflect.KClass<*>) =
		enqueued.filter { it.workSpec.workerClassName == worker.java.name }
			.map { it.workSpec.input.getLong(TEST_USER_ID_KEY, -1L) }
			.toSet()
}

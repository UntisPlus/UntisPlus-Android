package com.deelabs.untisplus.workers

import android.app.PendingIntent
import androidx.work.ListenableWorker
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.data.settings.model.UserSettings
import com.deelabs.untisplus.mappers.TimetableMapper
import com.deelabs.untisplus.receivers.NotificationReceiver.Companion.EXTRA_BOOLEAN_CLEAR
import com.deelabs.untisplus.receivers.NotificationReceiver.Companion.EXTRA_BOOLEAN_FIRST
import com.deelabs.untisplus.receivers.NotificationReceiver.Companion.EXTRA_STRING_NEXT_SUBJECT
import crocodile8.universal_cache.CachedSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Verifies alarm scheduling using only local fakes for Android services. */
class NotificationSetupWorkerTest {
	private lateinit var alarms: AlarmFixture

	@BeforeEach
	fun setUp() {
		alarms = AlarmFixture()
	}

	@AfterEach
	fun tearDown() {
		alarms.close()
	}

	@Test
	fun `schedules first lesson and break notifications with cleanup alarms`() = runBlocking {
		val firstStart = futureTime(2)
		val firstEnd = firstStart.plusMinutes(45)
		val secondStart = firstEnd.plusMinutes(15)
		val secondEnd = secondStart.plusMinutes(45)
		val periods = listOf(
			testPeriod(1, firstStart, firstEnd),
			testPeriod(2, secondStart, secondEnd),
		)
		val settings = testUserSettings(
			notifications = true,
			notificationsBeforeFirst = true,
			notificationsBeforeFirstMinutes = 30,
		)
		val worker = worker(settings, periods)

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)
		assertEquals(
			listOf(
				firstStart.minusMinutes(30),
				firstStart,
				firstEnd,
				secondStart,
			).map(::epochMillis),
			alarms.triggers,
		)
		assertTrue((EXTRA_BOOLEAN_FIRST to true) in alarms.extras)
		assertTrue((EXTRA_BOOLEAN_CLEAR to true) in alarms.extras)
		assertTrue((EXTRA_STRING_NEXT_SUBJECT to "?") in alarms.extras)
	}

	@Test
	fun `disabled first lesson notification cancels its alarm but keeps the break`() = runBlocking {
		val firstStart = futureTime(2)
		val firstEnd = firstStart.plusMinutes(45)
		val secondStart = firstEnd.plusMinutes(15)
		val periods = listOf(
			testPeriod(1, firstStart, firstEnd),
			testPeriod(2, secondStart, secondStart.plusMinutes(45)),
		)
		val worker = worker(testUserSettings(notifications = true), periods)

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)
		assertEquals(listOf(firstEnd, secondStart).map(::epochMillis), alarms.triggers)
		verify(exactly = 1) { alarms.alarmManager.cancel(any<PendingIntent>()) }
	}

	@Test
	fun `multi hour lesson does not schedule a break notification`() = runBlocking {
		val firstStart = futureTime(2)
		val continuationStart = firstStart.plusHours(2).plusMinutes(15)
		val first = testPeriod(1, firstStart, firstStart.plusHours(2))
		val continuation = testPeriod(1, continuationStart, continuationStart.plusHours(1))
		val worker = worker(testUserSettings(notifications = true), listOf(first, continuation))

		val result = worker.doWork()

		assertTrue(result is ListenableWorker.Result.Success, result.toString())
		assertTrue(alarms.triggers.isEmpty())
		verify(exactly = 2) { alarms.alarmManager.cancel(any<PendingIntent>()) }
	}

	@Test
	fun `cancelled lessons are not considered for break notifications`() = runBlocking {
		val firstStart = futureTime(2)
		val firstEnd = firstStart.plusMinutes(45)
		val secondStart = firstEnd.plusMinutes(15)
		val periods = listOf(
			testPeriod(1, firstStart, firstEnd),
			testPeriod(2, secondStart, secondStart.plusMinutes(45), cancelled = true),
		)
		val worker = worker(testUserSettings(notifications = true), periods)

		worker.doWork()

		assertTrue(alarms.triggers.isEmpty())
		verify(exactly = 0) { alarms.alarmManager.setWindow(any(), any(), any(), any()) }
	}

	@Test
	fun `empty timetable schedules no alarms`() = runBlocking {
		val worker = worker(testUserSettings(notifications = true), emptyList())

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)
		assertTrue(alarms.triggers.isEmpty())
	}

	@Test
	fun `missing user schedules nothing`() = runBlocking {
		val worker = worker(testUserSettings(notifications = true), emptyList(), user = null)

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)
		assertTrue(alarms.triggers.isEmpty())
		verify(exactly = 0) { alarms.alarmManager.setWindow(any(), any(), any(), any()) }
	}

	private fun worker(
		settings: UserSettings,
		periods: List<Period>,
		user: User? = testUser(1),
	): NotificationSetupWorker {
		val userId = 1L
		val settingsRepository = mockk<UserSettingsRepository>(relaxed = true)
		every { settingsRepository.getSettings(userId) } returns flowOf(settings)
		val userDao = mockk<UserDao>(relaxed = true)
		coEvery { userDao.getByIdAsync(userId) } returns user
		val mapper = mockk<TimetableMapper>(relaxed = true)
		every { mapper.preparePeriods(any(), false) } returns periods
		val source: CachedSource<TimetableRepository.TimetableParams, List<Period>> = periodsSource(periods)
		val clock = Clock.fixed(Instant.now().minusSeconds(3600), ZoneId.systemDefault())
		return NotificationSetupWorker(
			settingsRepository,
			testMasterDataRepository(),
			mapper,
			clock,
			userDao,
			workerContext(alarms.alarmManager),
			workerParams(userId),
			FakeTimetableRepository(source),
		)
	}

	private fun futureTime(plusHours: Long) =
		LocalDateTime.now().plusHours(plusHours).withSecond(0).withNano(0)

	private fun epochMillis(time: LocalDateTime) =
		time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

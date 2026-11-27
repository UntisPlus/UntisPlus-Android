package com.deelabs.untisplus.workers

import androidx.work.ListenableWorker
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.data.settings.model.UserSettings
import com.deelabs.untisplus.mappers.TimetableMapper
import com.deelabs.untisplus.receivers.AutoMuteReceiver.Companion.EXTRA_BOOLEAN_MUTE
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
import java.time.LocalDateTime
import java.time.ZoneId

/** Verifies auto-mute alarm scheduling using only local fakes. */
class AutoMuteSetupWorkerTest {
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
	fun `schedules mute and unmute for each lesson with a long enough break`() = runBlocking {
		val firstStart = futureTime(2)
		val firstEnd = firstStart.plusMinutes(45)
		val secondStart = firstEnd.plusMinutes(15)
		val secondEnd = secondStart.plusMinutes(45)
		val periods = listOf(
			testPeriod(1, firstStart, firstEnd),
			testPeriod(2, secondStart, secondEnd),
		)
		val worker = worker(testUserSettings(automute = true), periods)

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)
		assertEquals(
			listOf(firstStart, firstEnd, secondStart, secondEnd).map(::epochMillis),
			alarms.triggers,
		)
		assertEquals(2, alarms.extras.count { it == EXTRA_BOOLEAN_MUTE to true })
		assertEquals(2, alarms.extras.count { it == EXTRA_BOOLEAN_MUTE to false })
	}

	@Test
	fun `short break suppresses only the intermediate unmute`() = runBlocking {
		val firstStart = futureTime(2)
		val firstEnd = firstStart.plusMinutes(45)
		val secondStart = firstEnd.plusMinutes(3)
		val secondEnd = secondStart.plusMinutes(45)
		val periods = listOf(
			testPeriod(1, firstStart, firstEnd),
			testPeriod(2, secondStart, secondEnd),
		)
		val worker = worker(
			testUserSettings(automute = true, automuteMinimumBreakLength = 5f),
			periods,
		)

		worker.doWork()

		assertEquals(
			listOf(firstStart, secondStart, secondEnd).map(::epochMillis),
			alarms.triggers,
		)
		assertEquals(2, alarms.extras.count { it == EXTRA_BOOLEAN_MUTE to true })
		assertEquals(1, alarms.extras.count { it == EXTRA_BOOLEAN_MUTE to false })
	}

	@Test
	fun `cancelled lessons are skipped when cancelled automute is disabled`() = runBlocking {
		val firstStart = futureTime(2)
		val firstEnd = firstStart.plusMinutes(45)
		val periods = listOf(
			testPeriod(1, firstStart, firstEnd, cancelled = true),
			testPeriod(2, firstEnd.plusMinutes(15), firstEnd.plusHours(1), cancelled = true),
		)
		val worker = worker(
			testUserSettings(automute = true, automuteCancelled = false),
			periods,
		)

		worker.doWork()

		assertTrue(alarms.triggers.isEmpty())
		verify(exactly = 0) { alarms.alarmManager.setWindow(any(), any(), any(), any()) }
	}

	@Test
	fun `empty timetable schedules no alarms`() = runBlocking {
		val worker = worker(testUserSettings(automute = true), emptyList())

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)
		assertTrue(alarms.triggers.isEmpty())
	}

	@Test
	fun `missing user schedules nothing`() = runBlocking {
		val worker = worker(testUserSettings(automute = true), emptyList(), user = null)

		assertTrue(worker.doWork() is ListenableWorker.Result.Success)
		verify(exactly = 0) { alarms.alarmManager.setWindow(any(), any(), any(), any()) }
	}

	private fun worker(
		settings: UserSettings,
		periods: List<Period>,
		user: User? = testUser(1),
	): AutoMuteSetupWorker {
		val userId = 1L
		val settingsRepository = mockk<UserSettingsRepository>(relaxed = true)
		every { settingsRepository.getSettings(userId) } returns flowOf(settings)
		val userDao = mockk<UserDao>(relaxed = true)
		coEvery { userDao.getByIdAsync(userId) } returns user
		val mapper = mockk<TimetableMapper>(relaxed = true)
		every { mapper.preparePeriods(any(), any()) } returns periods
		val source: CachedSource<TimetableRepository.TimetableParams, List<Period>> = periodsSource(periods)
		return AutoMuteSetupWorker(
			settingsRepository,
			mapper,
			mockk<MasterDataRepository>(relaxed = true),
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

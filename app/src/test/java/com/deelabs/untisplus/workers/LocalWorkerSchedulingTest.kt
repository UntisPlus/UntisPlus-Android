package com.deelabs.untisplus.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.deelabs.untisplus.data.repository.ImportedCalendarRepository
import com.deelabs.untisplus.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Tests the local scheduling entry points without WorkManager's live services. */
class LocalWorkerSchedulingTest {
	private val workManager: WorkManager = mockk(relaxed = true)
	private val context: Context = mockk(relaxed = true)

	@BeforeEach
	fun setUp() {
		mockkObject(WorkManager.Companion)
		every { WorkManager.getInstance(any<Context>()) } returns workManager
	}

	@AfterEach
	fun tearDown() {
		unmockkObject(WorkManager.Companion)
	}

	@Test
	fun `notification setup enqueues one tagged request with the user id`() {
		NotificationSetupWorker.enqueue(workManager, testUser(42))

		val request = captureSingleEnqueue()
		assertEquals(NotificationSetupWorker::class.java.name, request.workSpec.workerClassName)
		assertTrue(NotificationSetupWorkTag in request.tags)
		assertEquals(42L, request.workSpec.input.getLong(TEST_USER_ID_KEY, -1L))
		verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
	}

	@Test
	fun `notification setup does not dedupe repeated enqueues`() {
		val user = testUser(42)
		NotificationSetupWorker.enqueue(workManager, user)
		NotificationSetupWorker.enqueue(workManager, user)

		verify(exactly = 2) { workManager.enqueue(any<WorkRequest>()) }
	}

	@Test
	fun `auto mute setup enqueues one tagged request with the user id`() {
		AutoMuteSetupWorker.enqueue(workManager, testUser(7))

		val request = captureSingleEnqueue()
		assertEquals(AutoMuteSetupWorker::class.java.name, request.workSpec.workerClassName)
		assertTrue(AutoMuteSetupWorkTag in request.tags)
		assertEquals(7L, request.workSpec.input.getLong(TEST_USER_ID_KEY, -1L))
		verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
	}

	@Test
	fun `auto mute setup does not dedupe repeated enqueues`() {
		val user = testUser(7)
		AutoMuteSetupWorker.enqueue(workManager, user)
		AutoMuteSetupWorker.enqueue(workManager, user)

		verify(exactly = 2) { workManager.enqueue(any<WorkRequest>()) }
	}

	@Test
	fun `widget update enqueues a tagged one-time request without user data`() {
		WidgetUpdateWorker.enqueue(workManager)

		val request = captureSingleEnqueue()
		assertEquals(WidgetUpdateWorker::class.java.name, request.workSpec.workerClassName)
		assertTrue(WidgetUpdateWorkTag in request.tags)
		assertEquals(-1L, request.workSpec.input.getLong(TEST_USER_ID_KEY, -1L))
	}

	@Test
	fun `widget update enqueues again on every refresh`() {
		WidgetUpdateWorker.enqueue(workManager)
		WidgetUpdateWorker.enqueue(workManager)

		verify(exactly = 2) { workManager.enqueue(any<WorkRequest>()) }
		verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
	}

	@Test
	fun `daily worker is enqueued with the daily tag and a delay until the next run`() {
		DailyWorker.enqueueNext(context)

		val request = captureSingleEnqueue()
		assertEquals(DailyWorker::class.java.name, request.workSpec.workerClassName)
		assertTrue(DailyWorker.TAG_DAILY_WORK in request.tags)
		assertTrue(request.workSpec.initialDelay in 0..TimeUnit.DAYS.toMillis(1))
	}

	@Test
	fun `imported calendar sync keeps one six hour periodic work`() {
		ImportedCalendarSyncWorker.schedule(context)

		val request = slot<PeriodicWorkRequest>()
		verify {
			workManager.enqueueUniquePeriodicWork(
				ImportedCalendarSyncWorkName,
				ExistingPeriodicWorkPolicy.KEEP,
				capture(request)
			)
		}
		assertEquals(ImportedCalendarSyncWorker::class.java.name, request.captured.workSpec.workerClassName)
		assertEquals(6 * 60 * 60 * 1000L, request.captured.workSpec.intervalDuration)
		assertEquals(NetworkType.CONNECTED, request.captured.workSpec.constraints.requiredNetworkType)
	}

	@Test
	fun `repeated imported calendar scheduling keeps the existing work`() {
		ImportedCalendarSyncWorker.schedule(context)
		ImportedCalendarSyncWorker.schedule(context)

		verify(exactly = 2) {
			workManager.enqueueUniquePeriodicWork(
				ImportedCalendarSyncWorkName,
				ExistingPeriodicWorkPolicy.KEEP,
				any()
			)
		}
	}

	@Test
	fun `imported calendar cancellation targets the same unique work name`() {
		ImportedCalendarSyncWorker.cancel(context)

		verify { workManager.cancelUniqueWork(ImportedCalendarSyncWorkName) }
	}

	@Test
	fun `calendar sync without a current user does nothing`() = runBlocking {
		val userRepository = mockk<UserRepository>(relaxed = true)
		every { userRepository.currentUser } returns null
		val calendars = mockk<ImportedCalendarRepository>(relaxed = true)
		val worker = ImportedCalendarSyncWorker(context, workerParams(), userRepository, calendars)

		assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
		coVerify(exactly = 0) { calendars.refreshAll() }
	}

	@Test
	fun `calendar sync retries when refresh fails`() = runBlocking {
		val userRepository = mockk<UserRepository>(relaxed = true)
		every { userRepository.currentUser } returns testUser(1)
		val calendars = mockk<ImportedCalendarRepository>(relaxed = true)
		coEvery { calendars.refreshAll() } throws IOException("offline")
		val worker = ImportedCalendarSyncWorker(context, workerParams(), userRepository, calendars)

		assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Retry)
		coVerify(exactly = 1) { calendars.refreshAll() }
	}

	@Test
	fun `calendar sync refreshes local calendars when a user is present`() = runBlocking {
		val userRepository = mockk<UserRepository>(relaxed = true)
		every { userRepository.currentUser } returns testUser(1)
		val calendars = mockk<ImportedCalendarRepository>(relaxed = true)
		coEvery { calendars.refreshAll() } returns Unit
		val worker = ImportedCalendarSyncWorker(context, workerParams(), userRepository, calendars)

		assertTrue(worker.doWork() is androidx.work.ListenableWorker.Result.Success)
		coVerify(exactly = 1) { calendars.refreshAll() }
	}

	private fun captureSingleEnqueue(): WorkRequest {
		val request = slot<WorkRequest>()
		verify { workManager.enqueue(capture(request)) }
		return request.captured
	}

	private companion object {
		// Mirrors the private tags/names in the worker companions.
		const val NotificationSetupWorkTag = "NotificationSetupWork"
		const val AutoMuteSetupWorkTag = "AutoMuteSetupWork"
		const val WidgetUpdateWorkTag = "WidgetUpdateWork"
		const val ImportedCalendarSyncWorkName = "ImportedCalendarSyncWork"
	}
}

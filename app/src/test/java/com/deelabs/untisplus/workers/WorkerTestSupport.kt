package com.deelabs.untisplus.workers

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.WorkerParameters
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.enumeration.PeriodState
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.api.model.untis.timetable.PeriodText
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.settings.model.TimetableElement
import com.deelabs.untisplus.data.settings.model.UserSettings
import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.FromCache
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
import java.util.UUID

/** Mirrors the private Worker key so tests can assert the enqueued input data. */
internal const val TEST_USER_ID_KEY = "UserId"

internal fun workerContext(alarmManager: AlarmManager): Context = mockk<Context>(relaxed = true).apply {
	every { applicationContext } returns this
	every { getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
	every { getSystemService(Context.ALARM_SERVICE) } returns alarmManager
}

internal fun workerContext(): Context = mockk<Context>(relaxed = true).apply {
	every { applicationContext } returns this
	every { getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
}

internal fun workerParams(userId: Long? = null): WorkerParameters = mockk<WorkerParameters>(relaxed = true).apply {
	every { inputData } returns when (userId) {
		null -> Data.EMPTY
		else -> Data.Builder().putLong(TEST_USER_ID_KEY, userId).build()
	}
	every { id } returns UUID.randomUUID()
}

internal fun testUser(id: Long, anonymous: Boolean = false): User = User(
	id = id,
	profileName = "user-$id",
	apiHost = "example.invalid",
	anonymous = anonymous,
	timeGrid = TimeGrid.generateDefault(),
	masterDataTimestamp = 0,
	userData = UserData(
		elemId = 0,
		elemType = null,
		displayName = "Test",
		schoolName = "Test",
		departmentId = 0,
	),
)

internal fun testUserSettings(
	personalTimetable: Boolean = true,
	notifications: Boolean = false,
	notificationsBeforeFirst: Boolean = false,
	notificationsBeforeFirstMinutes: Int = 30,
	notificationsInMultiple: Boolean = false,
	automute: Boolean = false,
	automuteCancelled: Boolean = true,
	automuteMinimumBreakLength: Float = 5f,
): UserSettings = UserSettings.getDefaultInstance().toBuilder()
	.setNotificationsEnable(notifications)
	.setNotificationsInMultiple(notificationsInMultiple)
	.setNotificationsBeforeFirst(notificationsBeforeFirst)
	.setNotificationsBeforeFirstTime(notificationsBeforeFirstMinutes)
	.setAutomuteEnable(automute)
	.setAutomuteCancelledLessons(automuteCancelled)
	.setAutomuteMinimumBreakLength(automuteMinimumBreakLength)
	.apply {
		if (personalTimetable) {
			setTimetablePersonalTimetable(
				TimetableElement.newBuilder()
					.setElementId(7)
					.setElementType(ElementType.CLASS.id)
					.build()
			)
		}
	}
	.build()

internal fun testPeriod(
	id: Long,
	start: LocalDateTime,
	end: LocalDateTime,
	cancelled: Boolean = false,
): Period = Period(
	id = id,
	lessonId = id,
	startDateTime = start.toKotlinLocalDateTime(),
	endDateTime = end.toKotlinLocalDateTime(),
	foreColor = "000000",
	backColor = "ffffff",
	innerForeColor = "000000",
	innerBackColor = "ffffff",
	text = PeriodText(lesson = "", substitution = "", info = ""),
	elements = listOf(PeriodElement(ElementType.SUBJECT, 100 + id)),
	`is` = if (cancelled) listOf(PeriodState.CANCELLED) else emptyList(),
	homeWorks = null,
)

internal fun testMasterDataRepository(): MasterDataRepository = mockk<MasterDataRepository>(relaxed = true).apply {
	every { getShortName(any(), any(), any()) } returns "?"
	every { getLongName(any(), any(), any()) } returns "?"
}

internal class FakeTimetableRepository(
	private val source: CachedSource<TimetableRepository.TimetableParams, List<Period>>,
) : TimetableRepository {
	override fun timetableSource(user: User?) = source
	override fun periodDataSource() = throw UnsupportedOperationException("not used by worker tests")
	override suspend fun postLessonTopic(periodId: Long, lessonTopic: String) = Result.success(true)
	override suspend fun postAbsence(
		periodId: Long,
		studentId: Long,
		startTime: kotlinx.datetime.LocalTime,
		endTime: kotlinx.datetime.LocalTime,
	): Result<List<StudentAbsence>> = Result.success(emptyList())
	override suspend fun deleteAbsence(absenceId: Long) = Result.success(true)
	override suspend fun postAbsencesChecked(periodIds: Set<Long>) = Result.success(Unit)
}

internal fun fakeTimetableSource(
	answer: (TimetableRepository.TimetableParams, FromCache, Any) -> Flow<List<Period>>,
): CachedSource<TimetableRepository.TimetableParams, List<Period>> =
	mockk<CachedSource<TimetableRepository.TimetableParams, List<Period>>>().apply {
		every { get(any(), any(), any(), any(), any()) } answers {
			answer(firstArg(), secondArg(), lastArg())
		}
	}

internal fun periodsSource(periods: List<Period>) = fakeTimetableSource { _, _, _ -> flowOf(periods) }

/**
 * Replaces the Android framework calls made by the two setup workers with local
 * fakes. Construction of [Intent] and the [PendingIntent] factory are intercepted
 * so no real alarm or notification service is ever contacted.
 */
internal class AlarmFixture {
	val alarmManager: AlarmManager = mockk(relaxed = true)
	val triggers = mutableListOf<Long>()
	val extras = mutableListOf<Pair<String, Any?>>()

	init {
		mockkStatic(Log::class)
		every { Log.d(any<String>(), any<String>()) } returns 0
		every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

		mockkConstructor(Intent::class)
		fun putExtra(intent: Intent, name: String, value: Any?): Intent {
			extras += name to value
			return intent
		}
		every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers {
			putExtra(self as Intent, firstArg(), secondArg<String>())
		}
		every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } answers {
			putExtra(self as Intent, firstArg(), secondArg<Int>())
		}
		every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers {
			putExtra(self as Intent, firstArg(), secondArg<Long>())
		}
		every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers {
			putExtra(self as Intent, firstArg(), secondArg<Boolean>())
		}

		mockkStatic(PendingIntent::class)
		every { PendingIntent.getBroadcast(any<Context>(), any(), any<Intent>(), any()) } returns
			mockk(relaxed = true)

		every { alarmManager.setWindow(any(), capture(triggers), any(), any()) } returns Unit
	}

	fun close() {
		unmockkStatic(PendingIntent::class)
		unmockkConstructor(Intent::class)
		unmockkStatic(Log::class)
	}
}

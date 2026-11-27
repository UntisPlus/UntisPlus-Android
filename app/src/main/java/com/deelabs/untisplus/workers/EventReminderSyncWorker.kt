package com.deelabs.untisplus.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.data.cache.CacheRead
import com.deelabs.untisplus.data.cache.readFreshOrCached
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.domain.GetCurrentSchoolYearUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * Rebuilds local homework/exam reminders from cached Info Center state.
 *
 * Reads are freshness-aware: stale data triggers a refresh, and a cold,
 * corrupt or still-expired cache is reported as unavailable instead of empty.
 * When any read is unavailable the existing reminders are preserved untouched.
 * Break/first-lesson alarms are never touched.
 */
class EventReminderSyncWorker constructor(
	private val userRepository: UserRepository,
	private val userSettingsRepository: UserSettingsRepository,
	private val infoCenterRepository: InfoCenterRepository,
	private val masterDataRepository: MasterDataRepository,
	private val getCurrentSchoolYear: GetCurrentSchoolYearUseCase,
	private val clock: Clock,
	context: Context,
	params: WorkerParameters,
) : CoroutineWorker(context, params) {
	private data class EventElement(val id: Long, val type: ElementType, val name: String?)

	companion object {
		private const val LOG_TAG = "EventReminderSync"
		const val WORK_NAME = "EventReminderSyncWork"
		const val WORK_NAME_NOW = "EventReminderSyncNow"
		const val TAG_SYNC = "eventreminder_sync"
		private const val SYNC_INTERVAL_MINUTES = 15L

		/** Keeps a periodic rebuild alive across restarts; the worker is a no-op while opted out. */
		fun schedule(context: Context) {
			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				WORK_NAME,
				ExistingPeriodicWorkPolicy.KEEP,
				PeriodicWorkRequestBuilder<EventReminderSyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
					.addTag(TAG_SYNC)
					.build()
			)
		}

		/** Immediate rebuild after the user enables reminders. */
		fun syncNow(context: Context) {
			WorkManager.getInstance(context).enqueueUniqueWork(
				WORK_NAME_NOW,
				ExistingWorkPolicy.REPLACE,
				OneTimeWorkRequestBuilder<EventReminderSyncWorker>()
					.addTag(TAG_SYNC)
					.build()
			)
		}

		/**
		 * Opt-out cancels only the given profile's reminders. The periodic sync
		 * request is shared by all profiles and intentionally stays enqueued.
		 */
		fun cancel(context: Context, userId: Long?) {
			val id = userId ?: return
			val workManager = WorkManager.getInstance(context)
			try {
				val existing = existingDueDates(workManager, id)
				EventReminderNotifications.cancel(
					context,
					existing.keys.map(EventReminderPlan::notificationIdFor)
				)
			} catch (e: Exception) {
				Log.w(LOG_TAG, "Could not cancel reminder notifications", e)
			}
			workManager.cancelAllWorkByTag(EventReminderPlan.reminderTag(id))
		}

		private fun existingDueDates(workManager: WorkManager, userId: Long): Map<String, String> =
			existingDueDatesFromTags(
				userId,
				workManager.getWorkInfosByTag(EventReminderPlan.reminderTag(userId)).get()
					.flatMap { it.tags }
			)
	}

	override suspend fun doWork(): Result {
		val user = userRepository.currentUser ?: return Result.success()
		val settings = userSettingsRepository.getSettings(user.id).first()
		if (!settings.eventRemindersEnabled || !settings.notificationsEnable) {
			cancel(applicationContext, user.id)
			return Result.success()
		}

		EventReminderNotifications.ensureChannels(applicationContext)

		return try {
			val workManager = WorkManager.getInstance(applicationContext)
			val now = LocalDateTime.now(clock)
			val plan = planEventReminderSync(
				reads = collectCandidateReads(user),
				existingDueDates = existingDueDates(workManager, user.id),
				now = now,
			)
			when (plan) {
				EventReminderSyncPlan.Keep ->
					Log.i(LOG_TAG, "Cached Info Center data unavailable; existing reminders kept")
				is EventReminderSyncPlan.Apply -> applyPlan(user.id, plan, workManager, now)
			}
			Result.success()
		} catch (e: Exception) {
			// No new network endpoint is used here; a corrupt cache should not
			// cause a retry storm. The next periodic run re-reads local state.
			Log.w(LOG_TAG, "Event reminder sync failed", e)
			Result.success()
		}
	}

	private suspend fun collectCandidateReads(user: User): List<CacheRead<List<EventReminderCandidate>>> {
		val today: LocalDate = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())
		val endDate = getCurrentSchoolYear(today)?.endDate ?: today
		return eventElements(user).map { element ->
			val params = InfoCenterRepository.EventsParams(element.id, element.type, today, endDate)
			try {
				val homework = readFreshOrCached<List<HomeWork>> { fromCache, maxAge ->
					infoCenterRepository.homeworkSource()
						.get(params, fromCache, maxAge = maxAge, additionalKey = user.id)
						.last()
				}
				val exams = readFreshOrCached<List<Exam>> { fromCache, maxAge ->
					infoCenterRepository.examsSource()
						.get(params, fromCache, maxAge = maxAge, additionalKey = user.id)
						.last()
				}
				if (homework is CacheRead.Value && exams is CacheRead.Value) {
					CacheRead.Value(
						homework.value.map { it.toEventReminderCandidate(user.id, element.id, element.name) } +
							exams.value.map {
								it.toEventReminderCandidate(
									userId = user.id,
									elementId = element.id,
									subject = masterDataRepository.getShortName(it.subjectId, ElementType.SUBJECT, ""),
									studentName = element.name
								)
							}
					)
				} else {
					CacheRead.Unavailable
				}
			} catch (e: Exception) {
				Log.w(LOG_TAG, "Cached Info Center events unusable for element ${element.id}; keeping reminders", e)
				CacheRead.Unavailable
			}
		}
	}

	private fun eventElements(user: User): List<EventElement> {
		val userData = user.userData
		return if (userData.isParentAccount())
			userData.children.filterNotNull().map {
				EventElement(it.id, ElementType.STUDENT, it.fullName())
			}
		else
			listOf(EventElement(userData.elemId, userData.elemType ?: ElementType.STUDENT, null))
	}

	private fun applyPlan(
		userId: Long,
		plan: EventReminderSyncPlan.Apply,
		workManager: WorkManager,
		now: LocalDateTime,
	) {
		// Drops notifications for entries that disappeared or moved, plus this
		// profile's queued work. Other profiles' tags are never matched.
		EventReminderNotifications.cancel(
			applicationContext,
			plan.cancelKeys.map(EventReminderPlan::notificationIdFor)
		)
		workManager.cancelAllWorkByTag(EventReminderPlan.reminderTag(userId))

		plan.reminders.forEach { reminder ->
			EventReminderWorker.enqueue(
				workManager = workManager,
				userId = userId,
				reminder = reminder,
				title = reminderTitle(reminder),
				text = reminderText(reminder),
				delayMillis = reminder.initialDelayMillis(now)
			)
		}
	}

	private fun reminderTitle(reminder: EventReminder): String = applicationContext.getString(
		when (reminder.kind) {
			EventReminderKind.HOMEWORK -> R.string.notifications_event_reminder_homework_title
			EventReminderKind.EXAM -> R.string.notifications_event_reminder_exam_title
		}
	)

	private fun reminderText(reminder: EventReminder): String {
		val fallback = applicationContext.getString(
			when (reminder.kind) {
				EventReminderKind.HOMEWORK -> R.string.all_homework
				EventReminderKind.EXAM -> R.string.all_exam
			}
		)
		val label = reminder.label.takeIf { it.isNotBlank() } ?: fallback
		val namedLabel = reminder.studentName?.takeIf { it.isNotBlank() }
			?.let { applicationContext.getString(R.string.parent_event_student, it, label) }
			?: label
		val dueDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(reminder.dueDate)

		return applicationContext.getString(
			when (reminder.kind) {
				EventReminderKind.HOMEWORK -> R.string.notifications_event_reminder_homework_text
				EventReminderKind.EXAM -> R.string.notifications_event_reminder_exam_text
			},
			namedLabel,
			dueDate
		)
	}
}

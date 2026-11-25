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
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.domain.GetCurrentSchoolYearUseCase
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.flow.first
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
 * This worker never requests data from the network. [FromCache.ONLY] reads the
 * same disk cache that the Info Center screen fills; if the cache is empty, no
 * reminders are created. Opening the Info Center is therefore the only way data
 * reaches this worker, and existing break/first-lesson alarms are untouched.
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

		fun cancel(context: Context) {
			val workManager = WorkManager.getInstance(context)
			workManager.cancelUniqueWork(WORK_NAME)
			workManager.cancelUniqueWork(WORK_NAME_NOW)
			try {
				val existing = existingDueDates(workManager)
				EventReminderNotifications.cancel(
					context,
					existing.keys.map(EventReminderPlan::notificationIdFor)
				)
			} catch (e: Exception) {
				Log.w(LOG_TAG, "Could not cancel reminder notifications", e)
			}
			workManager.cancelAllWorkByTag(EventReminderPlan.TAG_REMINDER)
		}

		private fun existingDueDates(workManager: WorkManager): Map<String, String> =
			workManager.getWorkInfosByTag(EventReminderPlan.TAG_REMINDER).get()
				.mapNotNull { info ->
					val dueTag = info.tags.firstOrNull { it.startsWith(EventReminderPlan.DUE_TAG_PREFIX) }
						?.removePrefix(EventReminderPlan.DUE_TAG_PREFIX)
						?: return@mapNotNull null
					dueTag.substringBeforeLast('_') to dueTag.substringAfterLast('_')
				}
				.toMap()
	}

	override suspend fun doWork(): Result {
		val user = userRepository.currentUser ?: return Result.success()
		val settings = userSettingsRepository.getSettings(user.id).first()
		if (!settings.eventRemindersEnabled || !settings.notificationsEnable) {
			cancel(applicationContext)
			return Result.success()
		}

		EventReminderNotifications.ensureChannels(applicationContext)

		return try {
			val candidates = collectCandidates(user)
			val now = LocalDateTime.now(clock)
			replaceReminders(user.id, candidates, EventReminderPlan.build(candidates, now), now)
			Result.success()
		} catch (e: Exception) {
			// No network is involved; a corrupt/missing cache should not cause a
			// retry storm. The next periodic run will pick up fresh local state.
			Log.w(LOG_TAG, "Event reminder sync failed", e)
			Result.success()
		}
	}

	private suspend fun collectCandidates(user: User): List<EventReminderCandidate> {
		val today = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())
		val endDate = getCurrentSchoolYear(today)?.endDate ?: today
		return eventElements(user).flatMap { element ->
			try {
				val params = InfoCenterRepository.EventsParams(element.id, element.type, today, endDate)
				val homework = infoCenterRepository.homeworkSource()
					.get(params, FromCache.ONLY, additionalKey = user.id)
					.first()
				val exams = infoCenterRepository.examsSource()
					.get(params, FromCache.ONLY, additionalKey = user.id)
					.first()

				homework.map { it.toEventReminderCandidate(user.id, element.id, element.name) } +
					exams.map {
						it.toEventReminderCandidate(
							userId = user.id,
							elementId = element.id,
							subject = masterDataRepository.getShortName(it.subjectId, ElementType.SUBJECT, ""),
							studentName = element.name
						)
					}
			} catch (e: Exception) {
				// A missing cache for one element must not drop reminders for the others.
				Log.w(LOG_TAG, "No cached events for element ${element.id}; skipping", e)
				emptyList()
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

	private fun replaceReminders(
		userId: Long,
		candidates: List<EventReminderCandidate>,
		reminders: List<EventReminder>,
		now: LocalDateTime
	) {
		val workManager = WorkManager.getInstance(applicationContext)
		val existing = existingDueDates(workManager)
		val cancelKeys = EventReminderPlan.keysToCancel(existing, candidates)

		// Remove notifications for entries that disappeared or moved; keep ones
		// for unchanged entries that already fired and are still visible.
		EventReminderNotifications.cancel(
			applicationContext,
			cancelKeys.map(EventReminderPlan::notificationIdFor)
		)
		workManager.cancelAllWorkByTag(EventReminderPlan.TAG_REMINDER)

		reminders.forEach { reminder ->
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

package com.deelabs.untisplus.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Fires a single locally scheduled homework/exam reminder. */
class EventReminderWorker constructor(
	context: Context,
	params: WorkerParameters,
	private val userSettingsRepository: UserSettingsRepository,
) : CoroutineWorker(context, params) {
	companion object {
		const val KEY_USER_ID = "userId"
		const val KEY_NOTIFICATION_ID = "notificationId"
		const val KEY_TITLE = "title"
		const val KEY_TEXT = "text"

		internal fun enqueue(
			workManager: WorkManager,
			userId: Long,
			reminder: EventReminder,
			title: String,
			text: String,
			delayMillis: Long
		) {
			workManager.enqueueUniqueWork(
				reminder.key,
				ExistingWorkPolicy.REPLACE,
				OneTimeWorkRequestBuilder<EventReminderWorker>()
					.addTag(EventReminderPlan.TAG_REMINDER)
					.addTag(EventReminderPlan.KEY_TAG_PREFIX + reminder.key)
					// WorkInfo no longer exposes input data, so the due date is
					// kept in a tag for change/cancellation detection.
					.addTag(EventReminderPlan.DUE_TAG_PREFIX + reminder.key + "_" + reminder.dueDate)
					.setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
					.setInputData(
						workDataOf(
							KEY_USER_ID to userId,
							KEY_NOTIFICATION_ID to reminder.notificationId,
							KEY_TITLE to title,
							KEY_TEXT to text
						)
					)
					.build()
			)
		}
	}

	override suspend fun doWork(): Result {
		val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
		val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
		val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0)
		val userId = inputData.getLong(KEY_USER_ID, -1)

		// Re-check the opt-in at fire time; the user may have turned it off since
		// the reminder was queued and before the next sync could cancel it.
		val settings = userSettingsRepository.getSettings(userId).first()
		if (!settings.eventRemindersEnabled || !settings.notificationsEnable) {
			EventReminderNotifications.cancel(applicationContext, listOf(notificationId))
			return Result.success()
		}

		EventReminderNotifications.ensureChannels(applicationContext)
		EventReminderNotifications.post(applicationContext, notificationId, title, text)
		return Result.success()
	}
}

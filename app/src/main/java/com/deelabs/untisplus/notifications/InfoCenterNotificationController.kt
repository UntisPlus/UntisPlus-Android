package com.deelabs.untisplus.notifications

import com.deelabs.untisplus.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.first

/** Per-profile opt-in state and dedupe storage. */
interface InfoCenterNotificationStore {
	suspend fun read(userId: Long): InfoCenterNotificationState

	suspend fun saveSeen(userId: Long, seen: Map<String, String>, initialized: Boolean)

	suspend fun clear(userId: Long)
}

class ProtoInfoCenterNotificationStore(
	private val userSettingsRepository: UserSettingsRepository,
) : InfoCenterNotificationStore {
	override suspend fun read(userId: Long): InfoCenterNotificationState {
		val settings = userSettingsRepository.getSettings(userId).first()
		return InfoCenterNotificationState(
			enabled = settings.notificationsEnable && settings.infocenterMessageNotifications,
			seen = settings.infocenterMessageNotificationsSeenMap,
			initialized = settings.infocenterMessageNotificationsInitialized,
		)
	}

	override suspend fun saveSeen(userId: Long, seen: Map<String, String>, initialized: Boolean) {
		userSettingsRepository.updateSettingsForUser(userId) {
			clearInfocenterMessageNotificationsSeen()
			seen.forEach { (key, signature) ->
				putInfocenterMessageNotificationsSeen(key, signature)
			}
			infocenterMessageNotificationsInitialized = initialized
		}
	}

	override suspend fun clear(userId: Long) {
		userSettingsRepository.updateSettingsForUser(userId) {
			infocenterMessageNotifications = false
			clearInfocenterMessageNotificationsSeen()
			infocenterMessageNotificationsInitialized = false
		}
	}
}

/** Called with results from a refresh the app already performed. */
interface InfoCenterNotificationController {
	suspend fun onMessagesRefreshed(userId: Long, entries: List<InfoCenterNotificationEntry>)

	fun cancel()
}

class DefaultInfoCenterNotificationController(
	private val notifier: InfoCenterNotifier,
	private val store: InfoCenterNotificationStore,
) : InfoCenterNotificationController {
	override suspend fun onMessagesRefreshed(
		userId: Long,
		entries: List<InfoCenterNotificationEntry>,
	) {
		val state = store.read(userId)
		if (!state.enabled || !notifier.canPost()) return

		val diff = InfoCenterNotificationDiffer.evaluate(entries, state)

		// Only advance dedupe state after a successful post, so a transient
		// permission/security error does not permanently swallow the message.
		if (diff.newEntries.isNotEmpty() && !notifier.post(diff.newEntries)) return

		store.saveSeen(userId, diff.seen, initialized = true)
	}

	override fun cancel() = notifier.cancel()
}

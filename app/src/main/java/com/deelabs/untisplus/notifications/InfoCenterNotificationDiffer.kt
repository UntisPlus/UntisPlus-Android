package com.deelabs.untisplus.notifications

import com.deelabs.untisplus.api.model.untis.MessageOfDay
import com.deelabs.untisplus.model.rest.Message

/** A single Info Center item reduced to what a local notification needs. */
data class InfoCenterNotificationEntry(
	val key: String,
	val signature: String,
	val title: String,
	val text: String,
)

/** Persisted dedupe state for one profile. */
data class InfoCenterNotificationState(
	val enabled: Boolean,
	val seen: Map<String, String>,
	val initialized: Boolean,
)

data class InfoCenterNotificationDiff(
	val newEntries: List<InfoCenterNotificationEntry>,
	val seen: Map<String, String>,
)

/**
 * Pure dedupe logic. The first observed refresh only primes the baseline so
 * enabling the option never spams notifications about old messages. Later
 * refreshes report an item once per stable key/content signature change.
 */
object InfoCenterNotificationDiffer {
	fun evaluate(
		entries: List<InfoCenterNotificationEntry>,
		state: InfoCenterNotificationState,
	): InfoCenterNotificationDiff {
		val current = entries.associateBy { it.key }.values.toList()
		val seen = current.associate { it.key to it.signature }

		if (!state.initialized)
			return InfoCenterNotificationDiff(newEntries = emptyList(), seen = seen)

		return InfoCenterNotificationDiff(
			newEntries = current.filter { state.seen[it.key] != it.signature },
			seen = seen,
		)
	}
}

fun List<Message>.toMessageNotificationEntries(): List<InfoCenterNotificationEntry> = map { message ->
	InfoCenterNotificationEntry(
		key = "message:${message.id}",
		signature = signature(
			message.subject,
			message.contentPreview,
			message.content,
			message.isRevoked?.toString(),
		),
		title = message.subject.orEmpty().plainText(),
		text = (message.contentPreview ?: message.content).orEmpty().plainText(),
	)
}

fun List<MessageOfDay>.toMessageOfDayNotificationEntries(): List<InfoCenterNotificationEntry> = map { message ->
	InfoCenterNotificationEntry(
		key = "motd:${message.id}",
		signature = signature(message.subject, message.body),
		title = message.subject.plainText(),
		text = message.body.plainText(),
	)
}

/** Length-prefixed fields keep the signature unambiguous if values contain separators. */
private fun signature(vararg parts: String?): String =
	parts.joinToString("|") { value ->
		val text = value.orEmpty()
		"${text.length}:$text"
	}

private fun String.plainText(): String = replace(Regex("<[^>]*>"), " ")
	.replace(Regex("\\s+"), " ")
	.trim()

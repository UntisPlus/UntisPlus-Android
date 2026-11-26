package com.deelabs.untisplus.workers

import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

internal enum class EventReminderKind(val storedName: String) {
	HOMEWORK("homework"),
	EXAM("exam")
}

/**
 * A locally cached Info Center entry, normalised before any WorkManager work is
 * created. [enabled] is false for homework that is already marked done.
 */
internal data class EventReminderCandidate(
	val key: String,
	val kind: EventReminderKind,
	val entryId: Long,
	val dueDate: LocalDate,
	val label: String,
	val studentName: String? = null,
	val enabled: Boolean = true
)

internal data class EventReminder(
	val key: String,
	val kind: EventReminderKind,
	val entryId: Long,
	val dueDate: LocalDate,
	val remindAt: LocalDateTime,
	val label: String,
	val studentName: String? = null
) {
	val notificationId: Int get() = EventReminderPlan.notificationIdFor(key)
}

/**
 * Pure reminder policy shared by the sync worker and its unit tests. Reminders
 * are local only: the day before an entry is due at 18:00 device-local time.
 */
internal object EventReminderPlan {
	const val REMINDER_TAG_PREFIX = "eventreminder_user_"
	const val REMINDER_HOUR = 18

	/** Reminder work is per profile; a sync must never cancel another profile's work. */
	fun reminderTag(userId: Long): String = "$REMINDER_TAG_PREFIX$userId"

	private fun dueTagPrefix(userId: Long): String = "${reminderTag(userId)}_due_"

	fun keyTag(userId: Long, key: String): String = "${reminderTag(userId)}_key_$key"

	fun dueTag(userId: Long, key: String, dueDate: LocalDate): String =
		"${dueTagPrefix(userId)}${key}_$dueDate"

	/** Parses a due-date tag back into its reminder key; ignores other users' tags. */
	fun dueDateFromTag(userId: Long, tag: String): Pair<String, String>? {
		val prefix = dueTagPrefix(userId)
		if (!tag.startsWith(prefix)) return null
		val body = tag.removePrefix(prefix)
		val key = body.substringBeforeLast('_')
		val dueDate = body.substringAfterLast('_')
		return if (key.isBlank() || dueDate.isBlank()) null else key to dueDate
	}

	/** Includes the profile and element so ids from different sources never collide. */
	fun key(userId: Long, elementId: Long, kind: EventReminderKind, entryId: Long): String =
		"eventreminder_${userId}_${elementId}_${kind.storedName}_$entryId"

	fun notificationIdFor(key: String): Int = key.hashCode().takeIf { it != 0 } ?: 1

	fun reminderTime(dueDate: LocalDate): LocalDateTime = dueDate.minusDays(1).atTime(REMINDER_HOUR, 0)

	fun build(candidates: List<EventReminderCandidate>, now: LocalDateTime): List<EventReminder> =
		candidates.asSequence()
			.filter { it.enabled }
			.mapNotNull { candidate ->
				val remindAt = reminderTime(candidate.dueDate)
				if (!remindAt.isAfter(now)) null
				else EventReminder(
					key = candidate.key,
					kind = candidate.kind,
					entryId = candidate.entryId,
					dueDate = candidate.dueDate,
					remindAt = remindAt,
					label = candidate.label,
					studentName = candidate.studentName
				)
			}
			.distinctBy { it.key }
			.toList()

	/**
	 * Keys whose already-posted notification should be removed: the entry is
	 * gone, done, or its due date changed. Unchanged past entries keep their
	 * notification until the user dismisses it.
	 */
	fun keysToCancel(
		existingDueDates: Map<String, String>,
		candidates: List<EventReminderCandidate>
	): Set<String> {
		val current = candidates.filter { it.enabled }.associate { it.key to it.dueDate.toString() }
		return existingDueDates.keys.filter { key ->
			current[key] != existingDueDates[key]
		}.toSet()
	}

}

/** Extracts only this profile's reminder keys and due dates from WorkManager tags. */
internal fun existingDueDatesFromTags(userId: Long, tags: List<String>): Map<String, String> =
	tags.mapNotNull { EventReminderPlan.dueDateFromTag(userId, it) }.toMap()

internal fun EventReminder.initialDelayMillis(now: LocalDateTime): Long =
	Duration.between(now, remindAt).toMillis().coerceAtLeast(0)

internal fun HomeWork.toEventReminderCandidate(
	userId: Long,
	elementId: Long,
	studentName: String?
) = EventReminderCandidate(
	key = EventReminderPlan.key(userId, elementId, EventReminderKind.HOMEWORK, id),
	kind = EventReminderKind.HOMEWORK,
	entryId = id,
	dueDate = endDate.toJavaLocalDate(),
	label = text.trim(),
	studentName = studentName,
	enabled = !done && !completed
)

internal fun Exam.toEventReminderCandidate(
	userId: Long,
	elementId: Long,
	subject: String,
	studentName: String?
): EventReminderCandidate {
	val name = this.name.trim().ifBlank { subject.trim() }
	val label = if (subject.isNotBlank() && !name.contains(subject, ignoreCase = true))
		"$subject - $name"
	else name
	return EventReminderCandidate(
		key = EventReminderPlan.key(userId, elementId, EventReminderKind.EXAM, id),
		kind = EventReminderKind.EXAM,
		entryId = id,
		dueDate = startDateTime.toJavaLocalDateTime().toLocalDate(),
		label = label,
		studentName = studentName,
		enabled = true
	)
}

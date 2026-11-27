package com.deelabs.untisplus.workers

import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import kotlinx.datetime.LocalDate as KotlinxDate
import kotlinx.datetime.LocalDateTime as KotlinxDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EventReminderPlanTest {
	private val now = LocalDateTime.of(2026, 11, 26, 12, 0)
	private val due = LocalDate.of(2026, 11, 28)

	@Test
	fun `build schedules day before at 18 and filters disabled past duplicate entries`() {
		val candidates = listOf(
			candidate("kept", due),
			candidate("kept", due.plusDays(1), label = "changed duplicate"), // same key, first wins
			candidate("done", due, enabled = false),
			candidate("past", LocalDate.of(2026, 11, 26)),
			candidate("later", due.plusDays(2))
		)

		val reminders = EventReminderPlan.build(candidates, now)

		assertEquals(listOf("kept", "later"), reminders.map { it.key })
		assertEquals(LocalDateTime.of(2026, 11, 27, 18, 0), reminders.first().remindAt)
		assertEquals("label", reminders.first().label)
		assertEquals(30L * 60 * 60 * 1000, reminders.first().initialDelayMillis(now))
	}

	@Test
	fun `homework and exam candidates use local due dates and scoped keys`() {
		val homework = HomeWork(
			id = 7,
			lessonId = 1,
			startDate = KotlinxDate(2026, 11, 20),
			endDate = KotlinxDate(2026, 11, 30),
			text = " Essay ",
			completed = false,
			attachments = emptyList()
		)
		val doneHomework = homework.copy(id = 8, done = true)
		val exam = Exam(
			id = 9,
			examType = null,
			startDateTime = KotlinxDateTime(2026, 12, 1, 10, 0),
			endDateTime = KotlinxDateTime(2026, 12, 1, 12, 0),
			departmentId = 0,
			subjectId = 42,
			name = "Test",
			text = ""
		)

		val homeworkCandidate = homework.toEventReminderCandidate(5, 6, "Ada")
		val examCandidate = exam.toEventReminderCandidate(5, 6, "Math", "Ada")

		assertEquals(LocalDate.of(2026, 11, 30), homeworkCandidate.dueDate)
		assertEquals("eventreminder_5_6_homework_7", homeworkCandidate.key)
		assertEquals("Essay", homeworkCandidate.label)
		assertEquals("Ada", homeworkCandidate.studentName)
		assertTrue(homeworkCandidate.enabled)
		assertFalse(doneHomework.toEventReminderCandidate(5, 6, null).enabled)

		assertEquals(LocalDate.of(2026, 12, 1), examCandidate.dueDate)
		assertEquals("eventreminder_5_6_exam_9", examCandidate.key)
		assertEquals("Math - Test", examCandidate.label)
		assertEquals(EventReminderKind.EXAM, examCandidate.kind)
	}

	@Test
	fun `keysToCancel drops gone and moved entries but keeps unchanged ones`() {
		val candidates = listOf(
			candidate("kept", due),
			candidate("moved", due.plusDays(1)),
			candidate("done", due, enabled = false)
		)
		val existingDueDates = mapOf(
			"kept" to due.toString(),
			"moved" to due.minusDays(2).toString(),
			"done" to due.toString(),
			"gone" to due.toString()
		)

		assertEquals(
			setOf("moved", "done", "gone"),
			EventReminderPlan.keysToCancel(existingDueDates, candidates)
		)
	}

	@Test
	fun `notification ids are non-zero and stable per key`() {
		val keys = listOf("eventreminder_1_2_homework_3", "eventreminder_1_2_exam_4", "eventreminder_9_2_homework_3")
		val ids = keys.map(EventReminderPlan::notificationIdFor)

		assertTrue(ids.all { it != 0 })
		assertEquals(3, ids.toSet().size)
		assertEquals(ids, keys.map(EventReminderPlan::notificationIdFor))
	}

	private fun candidate(
		key: String,
		dueDate: LocalDate,
		label: String = "label",
		enabled: Boolean = true
	) = EventReminderCandidate(
		key = key,
		kind = EventReminderKind.HOMEWORK,
		entryId = 1,
		dueDate = dueDate,
		label = label,
		studentName = null,
		enabled = enabled
	)
}

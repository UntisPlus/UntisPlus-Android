package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.api.model.untis.classreg.ClassRegEvent
import com.deelabs.untisplus.api.model.untis.timetable.Period
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.flow.last
import kotlinx.datetime.LocalDate

/** A student absence as seen in one of the current user's lessons. */
data class StudentAbsenceEntry(
	val id: Long,
	val studentName: String,
	val date: String,
	val time: String,
	val reason: String,
	val text: String,
	val excused: Boolean,
)

/** A class register entry attached to one of the current user's lessons. */
data class ClassRegisterEntry(
	val id: Long,
	val date: String,
	val time: String,
	val lesson: String,
	val reason: String,
	val text: String,
)

interface ClassRegisterRepository {
	suspend fun loadAbsences(from: LocalDate, to: LocalDate): Result<List<StudentAbsenceEntry>>
	suspend fun loadEvents(from: LocalDate, to: LocalDate): Result<List<ClassRegisterEntry>>
}

/**
 * Teacher-facing overview built from the same `getPeriodData2017` payload the
 * lesson-details dialog uses. No proxy endpoints are involved, so this works
 * for official Untis accounts as well.
 */
class RestClassRegisterRepository constructor(
	private val userRepository: UserRepository,
	private val timetableRepository: TimetableRepository,
	private val masterDataRepository: MasterDataRepository,
) : ClassRegisterRepository {
	override suspend fun loadAbsences(from: LocalDate, to: LocalDate): Result<List<StudentAbsenceEntry>> =
		load(from, to).map { it.absences }

	override suspend fun loadEvents(from: LocalDate, to: LocalDate): Result<List<ClassRegisterEntry>> =
		load(from, to).map { it.events }

	private data class Overview(
		val absences: List<StudentAbsenceEntry>,
		val events: List<ClassRegisterEntry>,
	)

	private suspend fun load(from: LocalDate, to: LocalDate): Result<Overview> = runCatching {
		val user = userRepository.currentUser
			?: throw IllegalStateException("No active user")
		val element = user.userData.getPeriodElement()
			?: user.userData.getFallbackPeriodElements().firstOrNull()
			?: throw IllegalStateException("No personal timetable element")

		val periods = timetableRepository.timetableSource().get(
			params = TimetableRepository.TimetableParams(element.id, element.type, from, to),
			fromCache = FromCache.IF_FAILED,
			maxAge = ONE_HOUR,
			additionalKey = user.id
		).last()

		val periodData = timetableRepository.periodDataSource().get(
			params = periods.toSet(),
			fromCache = FromCache.IF_FAILED,
			maxAge = ONE_HOUR,
			additionalKey = user.id
		).last()

		val students = periodData.referencedStudents.associate { it.id to it.fullName() }
		val absences = mutableListOf<StudentAbsenceEntry>()
		val events = mutableListOf<ClassRegisterEntry>()

		periodData.dataByTTId.forEach { (ttId, data) ->
			val period = periods.firstOrNull { it.id == ttId }
			data.absences.orEmpty().forEach { absence ->
				absences += absence.toEntry(period, students)
			}
			data.classRegEvents.orEmpty().forEach { event ->
				events += event.toEntry(period)
			}
		}

		Overview(absences = absences, events = events)
	}

	private fun StudentAbsence.toEntry(
		period: Period?,
		students: Map<Long, String>,
	) = StudentAbsenceEntry(
		id = id,
		studentName = students[studentId]
			?: period?.let { masterDataRepository.getShortName(studentId, null, studentId.toString()) }
			?: studentId.toString(),
		date = derived?.date?.takeIf { it.isNotBlank() }
			?: startDateTime.date.toString().takeIf { it.isNotBlank() }
			?: period?.startDateTime?.date?.toString().orEmpty(),
		time = listOfNotNull(
			startDateTime.time.toString().takeIf { it.isNotBlank() },
			endDateTime.time.toString().takeIf { it.isNotBlank() },
		).joinToString(" – "),
		reason = derived?.reason?.takeIf { it.isNotBlank() }
			?: absenceReason.takeIf { it.isNotBlank() }
			?: absenceReasonId.toString(),
		text = text,
		excused = excused,
	)

	private fun ClassRegEvent.toEntry(period: Period?) = ClassRegisterEntry(
		id = id,
		date = period?.startDateTime?.date?.toString().orEmpty(),
		time = dateTime?.takeIf { it.isNotBlank() }
			?: period?.startDateTime?.time?.toString().orEmpty(),
		lesson = period?.text?.lesson?.takeIf { it.isNotBlank() }
			?: masterDataRepository.getShortName(elementId, elementType),
		reason = eventReasonId.toString(),
		text = text.orEmpty(),
	)

	private companion object {
		const val ONE_HOUR = 60 * 60 * 1000L
	}
}

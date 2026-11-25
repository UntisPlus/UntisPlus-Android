package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.data.database.entities.jsonRpcApiUrlString

import com.deelabs.untisplus.api.client.AbsenceApi
import com.deelabs.untisplus.api.client.ClassRegApi
import com.deelabs.untisplus.api.client.MessagesApi
import com.deelabs.untisplus.api.client.OfficeHoursApi
import com.deelabs.untisplus.api.model.untis.MessageOfDay
import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.response.OfficeHourRegistrationsResult
import com.deelabs.untisplus.api.model.untis.timetable.OfficeHour
import com.deelabs.untisplus.data.cache.DiskCache
import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.datetime.LocalDate
import kotlinx.serialization.serializer
import okio.Path

interface InfoCenterRepository {
	fun messagesOfDaySource(): CachedSource<LocalDate, List<MessageOfDay>>

	fun examsSource(): CachedSource<EventsParams, List<Exam>>

	fun homeworkSource(): CachedSource<EventsParams, List<HomeWork>>

	fun absencesSource(): CachedSource<AbsencesParams, List<StudentAbsence>>

	fun officeHoursSource(): CachedSource<OfficeHoursParams, List<OfficeHour>>

	fun officeHourRegistrationsSource(): CachedSource<OfficeHourRegistrationParams, OfficeHourRegistrationsResult>

	data class EventsParams(
		val elementId: Long,
		val elementType: ElementType,
		val startDate: LocalDate,
		val endDate: LocalDate
	)

	data class AbsencesParams(
		val startDate: LocalDate,
		val endDate: LocalDate,
		val includeExcused: Boolean = true,
		val includeUnExcused: Boolean = true
	)

	data class OfficeHoursParams(
		val klasseId: Long,
		val startDate: LocalDate
	)

	data class OfficeHourRegistrationParams(
		val periodId: Long,
		val teacherId: Long
	)
}

class UntisInfoCenterRepository constructor(
	private val currentUserProvider: CurrentUserProvider,
	private val messagesApi: MessagesApi,
	private val classRegApi: ClassRegApi,
	private val absenceApi: AbsenceApi,
	private val officeHoursApi: OfficeHoursApi,
	private val cacheDir: Path,
	private val timeProvider: TimeProvider,
) : InfoCenterRepository {

	override fun messagesOfDaySource(): CachedSource<LocalDate, List<MessageOfDay>> {
		val user = currentUserProvider.currentUser!!
		return CachedSource(
			source = { params ->
				messagesApi.getMessagesOfDay(
					date = params,
					apiUrl = user.jsonRpcApiUrlString,
					user = user.user,
					key = user.key
				).messages
			},
			cache = DiskCache(cacheDir.resolve("infocenter").resolve("messages"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun examsSource(): CachedSource<InfoCenterRepository.EventsParams, List<Exam>> {
		val user = currentUserProvider.currentUser!!
		return CachedSource(
			source = { params ->
				classRegApi.getExams(
					id = params.elementId,
					type = params.elementType,
					startDate = params.startDate,
					endDate = params.endDate,
					apiUrl = user.jsonRpcApiUrlString,
					user = user.user,
					key = user.key
				).exams
			},
			cache = DiskCache(cacheDir.resolve("infocenter").resolve("exams"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun homeworkSource(): CachedSource<InfoCenterRepository.EventsParams, List<HomeWork>> {
		val user = currentUserProvider.currentUser!!
		return CachedSource(
			source = { params ->
				classRegApi.getHomework(
					id = params.elementId,
					type = params.elementType,
					startDate = params.startDate,
					endDate = params.endDate,
					apiUrl = user.jsonRpcApiUrlString,
					user = user.user,
					key = user.key
				).homeWorks
			},
			cache = DiskCache(cacheDir.resolve("infocenter").resolve("homework"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun absencesSource(): CachedSource<InfoCenterRepository.AbsencesParams, List<StudentAbsence>> {
		val user = currentUserProvider.currentUser!!
		return CachedSource(
			source = { params ->
				absenceApi.getStudentAbsences(
					startDate = params.startDate,
					endDate = params.endDate,
					includeExcused = params.includeExcused,
					includeUnExcused = params.includeUnExcused,
					apiUrl = user.jsonRpcApiUrlString,
					user = user.user,
					key = user.key
				).absences
			},
			cache = DiskCache(cacheDir.resolve("infocenter").resolve("absences"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun officeHoursSource(): CachedSource<InfoCenterRepository.OfficeHoursParams, List<OfficeHour>> {
		val user = currentUserProvider.currentUser!!
		return CachedSource(
			source = { params ->
				officeHoursApi.getOfficeHours(
					klasseId = params.klasseId,
					startDate = params.startDate,
					apiUrl = user.jsonRpcApiUrlString,
					user = user.user,
					key = user.key
				).officeHours
			},
			cache = DiskCache(cacheDir.resolve("infocenter").resolve("officehours"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun officeHourRegistrationsSource(): CachedSource<InfoCenterRepository.OfficeHourRegistrationParams, OfficeHourRegistrationsResult> {
		val user = currentUserProvider.currentUser!!
		return CachedSource(
			source = { params ->
				officeHoursApi.getOfficeHourRegistrations(
					periodId = params.periodId,
					teacherId = params.teacherId,
					apiUrl = user.jsonRpcApiUrlString,
					user = user.user,
					key = user.key
				)
			},
			cache = DiskCache(cacheDir.resolve("infocenter").resolve("officehourregistrations"), serializer()),
			timeProvider = timeProvider
		)
	}
}

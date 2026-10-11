package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.client.TimetableApi
import com.deelabs.untisplus.api.model.response.PeriodDataResult
import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.data.cache.DiskCache
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.database.entities.jsonRpcApiUrl
import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.serialization.serializer
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import okio.Path
import java.time.LocalDate
import java.time.LocalTime

interface TimetableRepository {
	fun timetableSource(): CachedSource<TimetableParams, List<Period>>
	fun periodDataSource(): CachedSource<Set<Period>, PeriodDataResult>

	suspend fun postLessonTopic(periodId: Long, lessonTopic: String): Result<Boolean>

	suspend fun postAbsence(
		periodId: Long, studentId: Long, startTime: LocalTime, endTime: LocalTime
	): Result<List<StudentAbsence>>

	suspend fun deleteAbsence(absenceId: Long): Result<Boolean>

	suspend fun postAbsencesChecked(periodIds: Set<Long>): Result<Unit>

	data class TimetableParams(
		val elementId: Long,
		val elementType: ElementType,
		val startDate: LocalDate,
		val endDate: LocalDate = startDate.plusDays(5 /*TODO*/)
	)
}

class UntisTimetableRepository constructor(
	private val userRepository: UserRepository,
	private val api: TimetableApi,
	private val cacheDir: Path,
	private val timeProvider: TimeProvider,
	private val userDao: UserDao,
) : TimetableRepository {
	override fun timetableSource(): CachedSource<TimetableRepository.TimetableParams, List<Period>> {
		val user = userRepository.currentUser!!
		return CachedSource(
			source = { params ->
				val timetableResponse = api.getTimetable(
					id = params.elementId,
					type = params.elementType,
					startDate = params.startDate.toKotlinLocalDate(),
					endDate = params.endDate.toKotlinLocalDate(),
					masterDataTimestamp = user.masterDataTimestamp,
					apiUrl = user.jsonRpcApiUrl.toString(),
					user = user.user,
					key = user.key
				)
				userDao.upsertMasterData(user.id, timetableResponse.masterData)
				return@CachedSource timetableResponse.timetable.periods
			},
			cache = DiskCache(cacheDir.resolve("timetable"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun periodDataSource(): CachedSource<Set<Period>, PeriodDataResult> {
		val user = userRepository.currentUser!!
		return CachedSource(
			source = { params ->
				api.getPeriodData(
					periodIds = params.map { it.id }.toSet(),
					apiUrl = user.jsonRpcApiUrl.toString(),
					user = user.user,
					key = user.key
				)
			},
			cache = DiskCache(cacheDir.resolve("periodData"), serializer()),
			timeProvider = timeProvider
		)
	}

	override suspend fun postLessonTopic(periodId: Long, lessonTopic: String): Result<Boolean> {
		val user = userRepository.currentUser!!
		return runCatching {
			api.postLessonTopic(
				periodId = periodId,
				lessonTopic = lessonTopic,
				apiUrl = user.jsonRpcApiUrl.toString(),
				user = user.user,
				key = user.key
			)
		}
	}

	override suspend fun postAbsence(periodId: Long, studentId: Long, startTime: LocalTime, endTime: LocalTime): Result<List<StudentAbsence>> {
		val user = userRepository.currentUser!!
		return runCatching {
			api.postAbsence(
				periodId = periodId,
				studentId = studentId,
				startTime = startTime.toKotlinLocalTime(),
				endTime = endTime.toKotlinLocalTime(),
				apiUrl = user.jsonRpcApiUrl.toString(),
				user = user.user,
				key = user.key
			)
		}
	}

	override suspend fun deleteAbsence(absenceId: Long): Result<Boolean> {
		val user = userRepository.currentUser!!
		return runCatching {
			api.deleteAbsence(
				absenceId = absenceId, apiUrl = user.jsonRpcApiUrl.toString(), user = user.user, key = user.key
			)
		}
	}

	override suspend fun postAbsencesChecked(periodIds: Set<Long>): Result<Unit> {
		val user = userRepository.currentUser!!
		return runCatching {
			api.postAbsencesChecked(
				periodIds = periodIds, apiUrl = user.jsonRpcApiUrl.toString(), user = user.user, key = user.key
			)
		}
	}
}

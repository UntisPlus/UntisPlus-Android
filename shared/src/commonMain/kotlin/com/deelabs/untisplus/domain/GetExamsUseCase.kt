package com.deelabs.untisplus.domain

import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import com.deelabs.untisplus.data.repository.CurrentUserProvider

class GetExamsUseCase constructor(
	private val currentUserProvider: CurrentUserProvider,
	private val infoCenterRepository: InfoCenterRepository,
	getCurrentSchoolYear: GetCurrentSchoolYearUseCase,
) {
	private val currentSchoolYear =
		getCurrentSchoolYear() ?: SchoolYearEntity(startDate = Clock.System.todayIn(TimeZone.currentSystemDefault()), endDate = Clock.System.todayIn(TimeZone.currentSystemDefault()))

	companion object {
		private const val ONE_HOUR: Long = 60 * 60 * 1000
	}

	operator fun invoke(): Flow<Result<List<Exam>>> = currentUserProvider.currentUser!!.let { user ->
		infoCenterRepository.examsSource()
			.get(
				InfoCenterRepository.EventsParams(
					user.userData.elemId,
					user.userData.elemType ?: ElementType.STUDENT,
					Clock.System.todayIn(TimeZone.currentSystemDefault()),
					currentSchoolYear.endDate
				),
				FromCache.CACHED_THEN_LOAD, maxAge = ONE_HOUR, additionalKey = user.id
			)
			.map(Result.Companion::success)
			.catch { emit(Result.failure(it)) }
	}
}

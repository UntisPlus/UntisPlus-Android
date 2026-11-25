package com.deelabs.untisplus.domain

import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.data.repository.CurrentUserProvider
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class GetExamsUseCase constructor(
	private val currentUserProvider: CurrentUserProvider,
	private val infoCenterRepository: InfoCenterRepository,
	private val getCurrentSchoolYear: GetCurrentSchoolYearUseCase,
) {
	companion object {
		private const val ONE_HOUR: Long = 60 * 60 * 1000
	}

	operator fun invoke(elementId: Long, elementType: ElementType): Flow<Result<List<Exam>>> = flow {
		val user = currentUserProvider.currentUser!!
		val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
		val endDate = getCurrentSchoolYear(today)?.endDate ?: today
		emitAll(
			infoCenterRepository.examsSource()
				.get(
					InfoCenterRepository.EventsParams(
						elementId,
						elementType,
						today,
						endDate
					),
					FromCache.CACHED_THEN_LOAD, maxAge = ONE_HOUR, additionalKey = user.id
				)
				.map(Result.Companion::success)
				.catch { emit(Result.failure(it)) }
		)
	}
}

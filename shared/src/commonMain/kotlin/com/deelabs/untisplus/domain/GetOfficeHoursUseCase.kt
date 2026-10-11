package com.deelabs.untisplus.domain

import com.deelabs.untisplus.api.model.untis.timetable.OfficeHour
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import com.deelabs.untisplus.data.repository.CurrentUserProvider

class GetOfficeHoursUseCase constructor(
	private val currentUserProvider: CurrentUserProvider,
	private val infoCenterRepository: InfoCenterRepository,
) {
	companion object {
		private const val ONE_HOUR: Long = 60 * 60 * 1000
	}

	operator fun invoke(): Flow<Result<List<OfficeHour>>> = infoCenterRepository.officeHoursSource()
		.get(
			InfoCenterRepository.OfficeHoursParams(-1, Clock.System.todayIn(TimeZone.currentSystemDefault())),
			FromCache.CACHED_THEN_LOAD,
			maxAge = ONE_HOUR,
			additionalKey = currentUserProvider.currentUser!!.id
		)
		.map(Result.Companion::success)
		.catch { emit(Result.failure(it)) }
}

package com.deelabs.untisplus.domain

import com.deelabs.untisplus.api.model.untis.timetable.OfficeHour
import com.deelabs.untisplus.api.model.response.OfficeHourRegistrationsResult
import com.deelabs.untisplus.data.repository.CurrentUserProvider
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Read-only retrieval of an office hour's timeslots. Never books anything. */
class GetOfficeHourRegistrationsUseCase constructor(
	private val currentUserProvider: CurrentUserProvider,
	private val infoCenterRepository: InfoCenterRepository,
) {
	companion object {
		private const val FIVE_MINUTES: Long = 5 * 60 * 1000
	}

	operator fun invoke(officeHour: OfficeHour): Flow<Result<OfficeHourRegistrationsResult>> =
		infoCenterRepository.officeHourRegistrationsSource()
			.get(
				InfoCenterRepository.OfficeHourRegistrationParams(
					periodId = officeHour.id,
					teacherId = officeHour.teacherId
				),
				FromCache.CACHED_THEN_LOAD,
				maxAge = FIVE_MINUTES,
				additionalKey = currentUserProvider.currentUser!!.id
			)
			.map(Result.Companion::success)
			.catch { emit(Result.failure(it)) }
}

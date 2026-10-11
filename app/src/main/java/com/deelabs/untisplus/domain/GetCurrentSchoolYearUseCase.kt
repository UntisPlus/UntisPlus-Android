package com.deelabs.untisplus.domain

import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.repository.MasterDataRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class GetCurrentSchoolYearUseCase constructor(
	private val masterDataRepository: MasterDataRepository
) {
	operator fun invoke(currentDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): SchoolYearEntity? {
		return masterDataRepository.userData?.schoolYears?.find {
			currentDate > it.startDate && currentDate < it.endDate
		}
	}
}

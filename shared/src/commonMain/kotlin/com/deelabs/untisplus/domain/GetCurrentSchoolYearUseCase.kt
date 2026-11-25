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
	suspend operator fun invoke(currentDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): SchoolYearEntity? {
		val schoolYears = masterDataRepository.schoolYears()
			.ifEmpty { masterDataRepository.userData?.schoolYears.orEmpty() }
		return schoolYears.find {
			currentDate >= it.startDate && currentDate <= it.endDate
		}
	}
}

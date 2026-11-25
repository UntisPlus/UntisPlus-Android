package com.deelabs.untisplus.ui.pages.teacherabsences

import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import kotlinx.datetime.LocalDate
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.time.LocalDate as JavaLocalDate

class TeacherAbsencesYearTest {
	private val deviceYears = listOf(
		SchoolYearEntity(
			name = "2025/2026",
			startDate = LocalDate.parse("2025-08-18"),
			endDate = LocalDate.parse("2026-06-28"),
		).toTeacherAbsencesYear(),
		SchoolYearEntity(
			name = "2026/2027",
			startDate = LocalDate.parse("2026-08-10"),
			endDate = LocalDate.parse("2027-06-27"),
		).toTeacherAbsencesYear(),
	)

	@Test
	fun currentSchoolYearIsSelectedByDefault() {
		assertThat(
			deviceYears.defaultYear(JavaLocalDate.parse("2026-11-24"))!!.name,
			`is`("2026/2027")
		)
	}

	@Test
	fun latestPastSchoolYearIsSelectedDuringHolidays() {
		assertThat(
			deviceYears.defaultYear(JavaLocalDate.parse("2026-07-15"))!!.name,
			`is`("2025/2026")
		)
	}

	@Test
	fun masterDataYearsAreMarkedAsNotConfiguredOnApi() {
		assertThat(deviceYears.all { !it.configuredOnApi }, `is`(true))
	}
}

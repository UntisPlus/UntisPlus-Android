package com.deelabs.untisplus.api.model.untis.masterdata

import com.deelabs.untisplus.api.model.untis.masterdata.timegrid.Day
import com.deelabs.untisplus.api.model.untis.masterdata.timegrid.Unit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class TimeGrid(
	val days: List<Day>
) {
	companion object {
		fun generateDefault(): TimeGrid {
			val unitsForDay = (6..22).map { hourIndex -> // Range of hours to include
				Unit(
					hourIndex.toString(),
					LocalTime(hourIndex, 0),
					LocalTime(if (hourIndex < 23) hourIndex + 1 else 0, 0)
				)
			}

			return TimeGrid(
				// Range of week days to include (1 = Monday, ...)
				(1..5).map {
					Day(DayOfWeek.entries[it - 1], unitsForDay)
				})
		}
	}

	fun hasEqualDays(): Boolean {
		for (i in 1..days.size) {
			if (days[i] != days[i - 1])
				return false
		}

		return true
	}
}

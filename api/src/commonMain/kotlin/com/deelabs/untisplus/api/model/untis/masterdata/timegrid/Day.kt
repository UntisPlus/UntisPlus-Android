package com.deelabs.untisplus.api.model.untis.masterdata.timegrid

import com.deelabs.untisplus.api.serializer.DayOfWeekSerializer
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable

@Serializable
data class Day(
	@Serializable(with = DayOfWeekSerializer::class)
	val day: DayOfWeek,
	val units: List<Unit>
)

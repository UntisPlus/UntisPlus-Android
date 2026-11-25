package com.deelabs.untisplus.api.model.response

import com.deelabs.untisplus.api.model.untis.timetable.OfficeHourRegistrationTimeSlot
import kotlinx.serialization.Serializable

@Serializable
data class OfficeHourRegistrationsResponse(
	val result: OfficeHourRegistrationsResult? = null
) : BaseResponse()

@Serializable
data class OfficeHourRegistrationsResult(
	val teacherText: String? = null,
	val userText: String? = null,
	val timeSlots: List<OfficeHourRegistrationTimeSlot> = emptyList()
)
